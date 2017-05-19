/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
    private final int maxParallelBootTasks;

    private final Map<String, List<ParsedBootOp>> opsBySubsystem = new LinkedHashMap<String, List<ParsedBootOp>>();
    private ParsedBootOp ourOp;

    ParallelBootOperationStepHandler(final ExecutorService executorService, final int maxParallelBootTasks, final ImmutableManagementResourceRegistration rootRegistration,
                                     final ControlledProcessState processState, final ModelControllerImpl controller,
                                     final int operationId, final OperationStepHandler extraValidationStepHandler) {
        assert maxParallelBootTasks > 1; // else this handler should not be used
        this.executor = executorService;
        this.maxParallelBootTasks = maxParallelBootTasks;
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

        final Resource rootResource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);

        // Organize the boot ops into larger "chunks" with the chunks executed concurrently.
        // All ops for a subsystem are in the same chunk.
        // We attempt to have approximately the same number of ops per chunk. This
        // is just a heuristic to try and get the chunks to take the same time to run,
        // but of course different ops can take different amounts of time and how much is
        // unknown to the kernel. Ideally any large variance in execution time between
        // subsystems would be in the MSC threads, not in the management ops themselves,
        // but this isn't always the case.

        // We separately track the chunk for logging as we want to add its
        // runtime steps first in order to get logging as-per-config as soon as possible
        OpChunk loggingModelChunk = null;
        // OpChunk is comparable based on # of ops it holds. So store in a TreeSet that will put the smallest chunk first
        final Set<OpChunk> opChunks = new TreeSet<>();

        // Organize subsystems by # of ops, most to fewest, so we end up creating chunks with big initial
        // sizes and then filling them in with the smaller subsystems
        Set<Map.Entry<String, List<ParsedBootOp>>> sortedEntries = sortSubsystemsByCount();

        int count = 0;
        for (Map.Entry<String, List<ParsedBootOp>> entry : sortedEntries) {
            String subsystemName = entry.getKey();
            if ("logging".equals(subsystemName)) {
                loggingModelChunk = new OpChunk();
                loggingModelChunk.addModelPhaseOps(subsystemName, entry.getValue());
            } else {
                OpChunk opChunk;
                if (count < maxParallelBootTasks) {
//                    ControllerLogger.MGMT_OP_LOGGER.debugf("Adding a chunk of %d ops for %s", entry.getValue().size(), subsystemName);
                    opChunk = new OpChunk();
                    count++;
                } else {
                    // Add this subsystem to the currently smallest, aka the first, chunk
                    Iterator<OpChunk> iter = opChunks.iterator();
                    opChunk = iter.next();
                    iter.remove(); // so when we re-add below it gets sorted to its new position
//                    ControllerLogger.MGMT_OP_LOGGER.debugf("Adding %d ops for %s to %s", entry.getValue().size(), subsystemName, opChunk.getSubsystemNames());
                }
                opChunk.addModelPhaseOps(subsystemName, entry.getValue());

                opChunks.add(opChunk);
            }
        }

        if (loggingModelChunk != null) {
            opChunks.add(loggingModelChunk);
        }

        final Map<OpChunk, ParallelBootTransactionControl> transactionControls = new LinkedHashMap<>();
        final CountDownLatch preparedLatch = new CountDownLatch(opChunks.size());
        final CountDownLatch committedLatch = new CountDownLatch(1);
        final CountDownLatch completeLatch = new CountDownLatch(opChunks.size());

        // TODO Elytron - We probably need a way to stop repeating this.
        final SecurityDomain bootSecurityDomain = SecurityDomain.builder()
                .setDefaultRealmName("Empty")
                .addRealm("Empty", SecurityRealm.EMPTY_REALM).build()
                .build();

        for (OpChunk opChunk : opChunks) {

            final ParallelBootTransactionControl txControl = new ParallelBootTransactionControl(preparedLatch, committedLatch, completeLatch);
            transactionControls.put(opChunk, txControl);

            Set<String> chunkSubsystems = opChunk.getSubsystemNames();
            List<ParsedBootOp> modelPhaseOps = opChunk.getModelPhaseOps();
            ControllerLogger.MGMT_OP_LOGGER.debugf("Chunk for %s has %d ops", chunkSubsystems, opChunk.opCount);

            // Execute the subsystem's ops in another thread
            ParallelBootOperationContext pboc = modelPhaseOps.size() == 0
                    ? null
                    : createOperationContext(primaryContext, bootSecurityDomain, txControl, opChunk.runtimeOps);
            ParallelBootTask subsystemTask = new ParallelBootTask(chunkSubsystems, modelPhaseOps,
                    OperationContext.Stage.MODEL, txControl, pboc);
            executor.execute(subsystemTask);
        }

        // Wait for all subsystem ops to complete
        try {
            preparedLatch.await();

            // See if all subsystems succeeded; if not report a failure to context
            checkForSubsystemFailures(context, transactionControls, OperationContext.Stage.MODEL);

            // Add any logging subsystem steps so we get logging early in the boot
            if (loggingModelChunk != null) {
                opChunks.remove(loggingModelChunk);
                for (ParsedBootOp loggingOp : loggingModelChunk.runtimeOps) {
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
            context.addStep(getRuntimeStep(opChunks, bootSecurityDomain), OperationContext.Stage.RUNTIME);

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

    private Set<Map.Entry<String, List<ParsedBootOp>>> sortSubsystemsByCount() {
        TreeSet<Map.Entry<String, List<ParsedBootOp>>> result = new TreeSet<>(new EntryComparator());
        result.addAll(opsBySubsystem.entrySet());
        return result;
    }

    private void checkForSubsystemFailures(OperationContext context, Map<OpChunk, ParallelBootTransactionControl> transactionControls, OperationContext.Stage stage) {
        boolean failureRecorded = false;
        for (Map.Entry<OpChunk, ParallelBootTransactionControl> entry : transactionControls.entrySet()) {
            ParallelBootTransactionControl txControl = entry.getValue();
            if (txControl.transaction == null) {
                // This means a set of subsystem steps didn't complete and rolled back
                String failureDesc;
                if (txControl.response.getResponseNode().hasDefined(ModelDescriptionConstants.FAILURE_DESCRIPTION)) {
                    failureDesc = txControl.response.getResponseNode().get(ModelDescriptionConstants.FAILURE_DESCRIPTION).toString();
                } else {
                    failureDesc = ControllerLogger.ROOT_LOGGER.subsystemBootOperationFailed(entry.getKey().getSubsystemNames());
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

    private void notifySubsystemTransactions(final Map<OpChunk, ParallelBootTransactionControl> transactionControls,
                                             final boolean rollback,
                                             final CountDownLatch committedLatch,
                                             final OperationContext.Stage stage) {
        for (Map.Entry<OpChunk, ParallelBootTransactionControl> entry : transactionControls.entrySet()) {
            ParallelBootTransactionControl txControl = entry.getValue();
            if (txControl.transaction != null) {
                if (!rollback) {
                    txControl.transaction.commit();
                    if (MGMT_OP_LOGGER.isDebugEnabled()) {
                        MGMT_OP_LOGGER.debugf("Committed transaction for %s subsystem %s stage boot operations", entry.getKey().getSubsystemNames(), stage);
                    }
                } else {
                    txControl.transaction.rollback();
                    if (MGMT_OP_LOGGER.isDebugEnabled()) {
                        MGMT_OP_LOGGER.debugf("Rolled back transaction for %s subsystem %s stage boot operations", entry.getKey().getSubsystemNames(), stage);
                    }
                }
            }
        }
        committedLatch.countDown();
    }

    private OperationStepHandler getRuntimeStep(final Set<OpChunk> opChunks, final SecurityDomain bootSecurityDomain) {

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

                final Map<OpChunk, ParallelBootTransactionControl> transactionControls = new LinkedHashMap<>();

                final CountDownLatch preparedLatch = new CountDownLatch(opChunks.size());
                final CountDownLatch committedLatch = new CountDownLatch(1);
                final CountDownLatch completeLatch = new CountDownLatch(opChunks.size());

                for (OpChunk opChunk : opChunks) {
                    final ParallelBootTransactionControl txControl = new ParallelBootTransactionControl(preparedLatch, committedLatch, completeLatch);
                    transactionControls.put(opChunk, txControl);

                    // Execute the subsystem's ops in another thread
                    ParallelBootOperationContext pboc = opChunk.runtimeOps.size() == 0
                        ? null
                        : createOperationContext(primaryContext, bootSecurityDomain, txControl, null);
                    ParallelBootTask subsystemTask = new ParallelBootTask(opChunk.getSubsystemNames(), opChunk.runtimeOps, OperationContext.Stage.RUNTIME, txControl, pboc);
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

        private final Set<String> subsystemNames;
        private final List<ParsedBootOp> bootOperations;
        private final OperationContext.Stage executionStage;
        private final ParallelBootTransactionControl transactionControl;
        private final ParallelBootOperationContext pboc;

        ParallelBootTask(final Set<String> subsystemNames,
                         final List<ParsedBootOp> bootOperations,
                         final OperationContext.Stage executionStage,
                         final ParallelBootTransactionControl transactionControl,
                         final ParallelBootOperationContext pboc) {
            assert bootOperations != null || pboc != null;
            this.subsystemNames = subsystemNames;
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
                pboc.setControllingThread();
                for (ParsedBootOp op : bootOperations) {
                    final OperationStepHandler osh = op.handler == null ? rootRegistration.getOperationHandler(op.address, op.operationName) : op.handler;
                    pboc.addStep(op.response, op.operation, osh, executionStage);
                }
                pboc.executeOperation();
            } catch (RuntimeException | Error t) {
                MGMT_OP_LOGGER.failedSubsystemBootOperations(t, subsystemNames);
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
                        failure.get(ModelDescriptionConstants.FAILURE_DESCRIPTION).set(ControllerLogger.ROOT_LOGGER.subsystemBootOperationFailedExecuting(subsystemNames));
                        transactionControl.operationFailed(failure);
                    }
                } else {
                    transactionControl.operationCompleted(transactionControl.response);
                }

                if (pboc != null) {
                    pboc.close();
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

    private static class OpChunk implements Comparable<OpChunk> {
        private final Map<String, List<ParsedBootOp>> ops = new LinkedHashMap<>();
        private final List<ParsedBootOp> runtimeOps = new ArrayList<>();
        private int opCount = 0;

        Set<String> getSubsystemNames() {
            return ops.keySet();
        }

        void addModelPhaseOps(String subsystem, List<ParsedBootOp> opsList) {
            ops.put(subsystem, opsList);
            opCount += opsList.size();
        }

        List<ParsedBootOp> getModelPhaseOps() {
            List<ParsedBootOp> list = new ArrayList<>(opCount);
            for (List<ParsedBootOp> item : ops.values()) {
                list.addAll(item);
            }
            return list;
        }

        @Override
        public int compareTo(OpChunk opChunk) {
            if (this == opChunk) {
                return 0;
            } else {
                int diff = opCount - opChunk.opCount;
                return diff == 0 ? -1 : diff;
            }
        }
    }

    private static class EntryComparator implements Comparator<Map.Entry<String, List<ParsedBootOp>>> {

        @Override
        public int compare(Map.Entry<String, List<ParsedBootOp>> e1, Map.Entry<String, List<ParsedBootOp>> e2) {
            if (e1 == e2) {
                return 0;
            }
            int diff = e2.getValue().size() - e1.getValue().size();
            return diff == 0 ? 1 : diff;
        }
    }
}
