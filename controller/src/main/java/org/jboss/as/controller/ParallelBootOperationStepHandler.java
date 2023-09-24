/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityRealm;

/**
 * Special handler that executes subsystem boot operations in parallel.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ParallelBootOperationStepHandler implements OperationStepHandler {

    private final Executor executor;
    private final ImmutableManagementResourceRegistration rootRegistration;
    private final ControlledProcessState processState;
    private final OperationStepHandler extraValidationStepHandler;

    private final ModelControllerImpl controller;
    private final int operationId;

    private final Map<String, List<ParsedBootOp>> opsBySubsystem = new LinkedHashMap<String, List<ParsedBootOp>>();
    private ParsedBootOp ourOp;

    ParallelBootOperationStepHandler(final ExecutorService executorService, final ImmutableManagementResourceRegistration rootRegistration,
                                     final ControlledProcessState processState, final ModelControllerImpl controller,
                                     final int operationId, final OperationStepHandler extraValidationStepHandler) {
        this.executor = executorService;
        this.rootRegistration = rootRegistration;
        this.processState = processState;

        this.controller = controller;
        this.operationId = operationId;
        this.extraValidationStepHandler = extraValidationStepHandler;
    }

    boolean addSubsystemOperation(final ParsedBootOp parsedOp) {
        final String subsystemName = getSubsystemName(parsedOp.address);
        if (subsystemName != null) {
            List<ParsedBootOp> list = opsBySubsystem.get(subsystemName);
            if (list == null) {
                list = new ArrayList<ParsedBootOp>();
                opsBySubsystem.put(subsystemName, list);
            }
            list.add(parsedOp);
            getParsedBootOp().addChildOperation(parsedOp);
        }
        return subsystemName != null;
    }

    ParsedBootOp getParsedBootOp() {
        if (ourOp == null) {
            ModelNode op = Util.getEmptyOperation("parallel-subsystem-boot", new ModelNode().setEmptyList());
            ourOp = new ParsedBootOp(op, this);
        }
        return ourOp;
    }

    private String getSubsystemName(final PathAddress address) {
        String key = null;
        if (address.size() > 0 && ModelDescriptionConstants.SUBSYSTEM.equals(address.getElement(0).getKey())) {
            key = address.getElement(0).getValue();
        }
        return key;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        if (!context.isNormalServer()) {
            throw ControllerLogger.ROOT_LOGGER.fullServerBootRequired(getClass());
        }

        if (!(context instanceof OperationContextImpl)) {
            throw ControllerLogger.ROOT_LOGGER.operationContextIsNotAbstractOperationContext();
        }

        long start = System.currentTimeMillis();

        final OperationContextImpl primaryContext = (OperationContextImpl) context;

        // Make sure the lock has been taken
        context.getResourceRegistrationForUpdate();
        final Resource rootResource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
        context.acquireControllerLock();

        final Map<String, List<ParsedBootOp>> runtimeOpsBySubsystem = new LinkedHashMap<String, List<ParsedBootOp>>();
        final Map<String, ParallelBootTransactionControl> transactionControls = new LinkedHashMap<String, ParallelBootTransactionControl>();

        final CountDownLatch preparedLatch = new CountDownLatch(opsBySubsystem.size());
        final CountDownLatch committedLatch = new CountDownLatch(1);
        final CountDownLatch completeLatch = new CountDownLatch(opsBySubsystem.size());

        // TODO Elytron - We probably need a way to stop repeating this.
        final SecurityDomain bootSecurityDomain = SecurityDomain.builder()
                .setDefaultRealmName("Empty")
                .addRealm("Empty", SecurityRealm.EMPTY_REALM).build()
                .build();

        for (Map.Entry<String, List<ParsedBootOp>> entry : opsBySubsystem.entrySet()) {
            String subsystemName = entry.getKey();
            List<ParsedBootOp> subsystemRuntimeOps = new ArrayList<ParsedBootOp>();
            runtimeOpsBySubsystem.put(subsystemName, subsystemRuntimeOps);

            final ParallelBootTransactionControl txControl = new ParallelBootTransactionControl(preparedLatch, committedLatch, completeLatch);
            transactionControls.put(entry.getKey(), txControl);

            // Execute the subsystem's ops in another thread
            List<ParsedBootOp> bootOps = entry.getValue();
            ParallelBootOperationContext pboc = bootOps.isEmpty()
                    ? null
                    : createOperationContext(primaryContext, bootSecurityDomain, txControl, subsystemRuntimeOps);
            ParallelBootTask subsystemTask = new ParallelBootTask(subsystemName, bootOps, OperationContext.Stage.MODEL, txControl, pboc);
            executor.execute(subsystemTask);
        }

        // Wait for all subsystem ops to complete
        try {
            preparedLatch.await();

            // See if all subsystems succeeded; if not report a failure to context
            checkForSubsystemFailures(context, transactionControls, OperationContext.Stage.MODEL);

            // Add any logging subsystem steps so we get logging early in the boot
            List<ParsedBootOp> loggingOps = runtimeOpsBySubsystem.remove("logging");
            if (loggingOps != null) {
                for (ParsedBootOp loggingOp : loggingOps) {
                    context.addStep(loggingOp.response, loggingOp.operation, loggingOp.handler, OperationContext.Stage.RUNTIME);
                }
            }

            // AS7-2561
            // The parallel execution will have added the subsystems to their parent resource in random order.
            // We need to restore the order that came in the XML.
            final Map<String, Resource> subsystemResources = new LinkedHashMap<String, Resource>();
            for (String subsystemName : opsBySubsystem.keySet()) {
                final Resource resource = rootResource.removeChild(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, subsystemName));
                if (resource != null) {
                    subsystemResources.put(subsystemName, resource);
                }
            }
            for (Map.Entry<String, Resource> entry : subsystemResources.entrySet()) {
                rootResource.registerChild(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, entry.getKey()), entry.getValue());
            }

            // Add step to execute all the runtime ops recorded by the other subsystem tasks
            context.addStep(getRuntimeStep(runtimeOpsBySubsystem, bootSecurityDomain), OperationContext.Stage.RUNTIME);

        } catch (InterruptedException e) {
            context.getFailureDescription().set(new ModelNode().set(ControllerLogger.ROOT_LOGGER.subsystemBootInterrupted()));
            Thread.currentThread().interrupt();
        }

        if (MGMT_OP_LOGGER.isDebugEnabled()) {
            long elapsed = System.currentTimeMillis() - start;
            MGMT_OP_LOGGER.debugf("Ran subsystem model operations in [%d] ms", elapsed);
        }

        // Continue boot
        context.completeStep(new OperationContext.ResultHandler() {
            @Override
            public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {

                // Tell all the subsystem tasks the result of the operations
                notifySubsystemTransactions(transactionControls, resultAction == OperationContext.ResultAction.ROLLBACK, committedLatch, OperationContext.Stage.RUNTIME);

                // Make sure all the subsystems have completed the out path before we return
                try {
                    completeLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

    }

    private ParallelBootOperationContext createOperationContext(final OperationContextImpl primaryContext,
                                                                final SecurityDomain bootSecurityDomain,
                                                                final ParallelBootTransactionControl txControl,
                                                                final List<ParsedBootOp> runtimeOps) {
        return new ParallelBootOperationContext(txControl, processState,
                primaryContext, runtimeOps, controller, operationId, controller.getAuditLogger(),
                extraValidationStepHandler, bootSecurityDomain::getAnonymousSecurityIdentity);
    }

    private void checkForSubsystemFailures(OperationContext context, Map<String, ParallelBootTransactionControl> transactionControls, OperationContext.Stage stage) {
        boolean failureRecorded = false;
        for (Map.Entry<String, ParallelBootTransactionControl> entry : transactionControls.entrySet()) {
            ParallelBootTransactionControl txControl = entry.getValue();
            if (txControl.transaction == null) {
                // This means a set of subsystem steps didn't complete and rolled back
                String failureDesc;
                if (txControl.response.getResponseNode().hasDefined(ModelDescriptionConstants.FAILURE_DESCRIPTION)) {
                    failureDesc = txControl.response.getResponseNode().get(ModelDescriptionConstants.FAILURE_DESCRIPTION).toString();
                } else {
                    failureDesc = ControllerLogger.ROOT_LOGGER.subsystemBootOperationFailed(entry.getKey());
                }
                MGMT_OP_LOGGER.error(failureDesc);
                if (!failureRecorded) {
                    context.getFailureDescription().set(failureDesc);
                    failureRecorded = true;
                    // If this had been a normal non-parallel op, everything would have
                    // rolled back, so we need to ensure that happens
                    context.setRollbackOnly();
                }
            } else {
                MGMT_OP_LOGGER.debugf("Stage %s boot ops for subsystem %s succeeded", stage, entry.getKey());
            }
        }
    }

    private void notifySubsystemTransactions(final Map<String, ParallelBootTransactionControl> transactionControls,
                                             final boolean rollback,
                                             final CountDownLatch committedLatch,
                                             final OperationContext.Stage stage) {
        for (Map.Entry<String, ParallelBootTransactionControl> entry : transactionControls.entrySet()) {
            ParallelBootTransactionControl txControl = entry.getValue();
            if (txControl.transaction != null) {
                if (!rollback) {
                    txControl.transaction.commit();
                    MGMT_OP_LOGGER.debugf("Committed transaction for %s subsystem %s stage boot operations", entry.getKey(), stage);
                } else {
                    txControl.transaction.rollback();
                    MGMT_OP_LOGGER.debugf("Rolled back transaction for %s subsystem %s stage boot operations", entry.getKey(), stage);
                }
            }
        }
        committedLatch.countDown();
    }

    private OperationStepHandler getRuntimeStep(final Map<String, List<ParsedBootOp>> runtimeOpsBySubsystem, final SecurityDomain bootSecurityDomain) {

        return new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

                long start = System.currentTimeMillis();

                if (!(context instanceof OperationContextImpl)) {
                    throw ControllerLogger.ROOT_LOGGER.operationContextIsNotAbstractOperationContext();
                }
                OperationContextImpl primaryContext = (OperationContextImpl) context;

                // make sure the registry lock is held
                context.getServiceRegistry(true);

                final Map<String, ParallelBootTransactionControl> transactionControls = new LinkedHashMap<String, ParallelBootTransactionControl>();

                final CountDownLatch preparedLatch = new CountDownLatch(runtimeOpsBySubsystem.size());
                final CountDownLatch committedLatch = new CountDownLatch(1);
                final CountDownLatch completeLatch = new CountDownLatch(runtimeOpsBySubsystem.size());

                for (Map.Entry<String, List<ParsedBootOp>> entry : runtimeOpsBySubsystem.entrySet()) {
                    String subsystemName = entry.getKey();
                    final ParallelBootTransactionControl txControl = new ParallelBootTransactionControl(preparedLatch, committedLatch, completeLatch);
                    transactionControls.put(subsystemName, txControl);

                    // Execute the subsystem's ops in another thread
                    List<ParsedBootOp> bootOps = entry.getValue();
                    ParallelBootOperationContext pboc = bootOps.isEmpty()
                        ? null
                        : createOperationContext(primaryContext, bootSecurityDomain, txControl, null);
                    ParallelBootTask subsystemTask = new ParallelBootTask(subsystemName, bootOps, OperationContext.Stage.RUNTIME, txControl, pboc);
                    executor.execute(subsystemTask);
                }

                // Wait for all subsystem ops to complete
                try {
                    preparedLatch.await();

                    // See if all subsystems succeeded; if not report a failure to context
                    checkForSubsystemFailures(context, transactionControls, OperationContext.Stage.RUNTIME);

                } catch (InterruptedException e) {
                    context.getFailureDescription().set(new ModelNode().set(ControllerLogger.ROOT_LOGGER.subsystemBootInterrupted()));
                    Thread.currentThread().interrupt();
                }

                if (MGMT_OP_LOGGER.isDebugEnabled()) {
                    long elapsed = System.currentTimeMillis() - start;
                    MGMT_OP_LOGGER.debugf("Ran subsystem runtime operations in [%d] ms", elapsed);
                }


                // Continue boot
                context.completeStep(new OperationContext.ResultHandler() {
                    @Override
                    public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {

                        // Tell all the subsystem tasks the result of the operations
                        notifySubsystemTransactions(transactionControls, resultAction == OperationContext.ResultAction.ROLLBACK, committedLatch, OperationContext.Stage.MODEL);

                        // Make sure all the subsystems have completed the out path before we return
                        try {
                            completeLatch.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
            }
        };
    }

    private class ParallelBootTask implements Runnable {

        private final String subsystemName;
        private final List<ParsedBootOp> bootOperations;
        private final OperationContext.Stage executionStage;
        private final ParallelBootTransactionControl transactionControl;
        private final ParallelBootOperationContext pboc;

        ParallelBootTask(final String subsystemName,
                         final List<ParsedBootOp> bootOperations,
                         final OperationContext.Stage executionStage,
                         final ParallelBootTransactionControl transactionControl,
                         final ParallelBootOperationContext pboc) {
            assert bootOperations != null || pboc != null;
            this.subsystemName = subsystemName;
            this.bootOperations = bootOperations;
            this.executionStage = executionStage;
            this.transactionControl = transactionControl;
            this.pboc = pboc;
        }

        @Override
        public void run() {
            try {

                if (pboc == null) {
                    transactionControl.operationPrepared(new ModelController.OperationTransaction() {
                        @Override
                        public void commit() {}

                        @Override
                        public void rollback() {}
                    }, new ModelNode());
                    return;
                }
                try (pboc) {
                    pboc.setControllingThread();
                    for (ParsedBootOp op : bootOperations) {
                        final OperationStepHandler osh = op.handler == null ? rootRegistration.getOperationHandler(op.address, op.operationName) : op.handler;
                        pboc.addStep(op.response, op.operation, osh, executionStage);
                    }
                    pboc.executeOperation();
                }
            } catch (RuntimeException | Error t) {
                MGMT_OP_LOGGER.failedSubsystemBootOperations(t, subsystemName);
                if (!transactionControl.signalled) {
                    ModelNode failure = new ModelNode();
                    failure.get(ModelDescriptionConstants.SUCCESS).set(false);
                    failure.get(ModelDescriptionConstants.FAILURE_DESCRIPTION).set(t.toString());
                    transactionControl.operationFailed(failure);
                }
            } finally {
                if (!transactionControl.signalled) {

                    if (bootOperations != null) {
                        for (ParsedBootOp op : bootOperations) {
                            if (op.response.hasDefined(ModelDescriptionConstants.SUCCESS) && !op.response.get(ModelDescriptionConstants.SUCCESS).asBoolean()) {
                                transactionControl.operationFailed(op.response);
                                break;
                            }
                        }
                    }
                    if (!transactionControl.signalled) {
                        ModelNode failure = new ModelNode();
                        failure.get(ModelDescriptionConstants.SUCCESS).set(false);
                        failure.get(ModelDescriptionConstants.FAILURE_DESCRIPTION).set(ControllerLogger.ROOT_LOGGER.subsystemBootOperationFailedExecuting(subsystemName));
                        transactionControl.operationFailed(failure);
                    }
                } else {
                    transactionControl.operationCompleted(transactionControl.response);
                }
            }
        }
    }

    private static class ParallelBootTransactionControl implements ProxyController.ProxyOperationControl {

        private final CountDownLatch preparedLatch;
        private final CountDownLatch committedLatch;
        private final CountDownLatch completeLatch;
        private OperationResponse response;
        private ModelController.OperationTransaction transaction;
        private boolean signalled;

        ParallelBootTransactionControl(CountDownLatch preparedLatch, CountDownLatch committedLatch, CountDownLatch completeLatch) {
            this.preparedLatch = preparedLatch;
            this.committedLatch = committedLatch;
            this.completeLatch = completeLatch;
        }

        @Override
        public void operationFailed(ModelNode response) {
            if (!signalled) {
                this.response = OperationResponse.Factory.createSimple(response);
                preparedLatch.countDown();
                completeLatch.countDown();
                signalled = true;
            }
        }

        @Override
        public void operationPrepared(ModelController.OperationTransaction transaction, ModelNode result) {
            if (!signalled) {
                this.transaction = transaction;
                preparedLatch.countDown();
                signalled = true;

                try {
                    committedLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw ControllerLogger.ROOT_LOGGER.transactionInterrupted();
                }
            }
        }

        @Override
        public void operationCompleted(OperationResponse response) {
            this.response = response;
            completeLatch.countDown();
        }
    }
}
