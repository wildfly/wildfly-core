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

import static java.security.AccessController.doPrivileged;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_MECHANISM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACTIVE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALLOW_RESOURCE_SERVICE_RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING_TIMEOUT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CANCELLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_UUID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROCESS_STATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLBACK_ON_RUNTIME_FAILURE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;
import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;
import static org.jboss.as.controller.logging.ControllerLogger.ROOT_LOGGER;

import java.io.IOException;
import java.security.AccessControlContext;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.as.controller.access.Authorizer;
import org.jboss.as.controller.audit.AuditLogger;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.capability.registry.CapabilityContext;
import org.jboss.as.controller.capability.registry.CapabilityId;
import org.jboss.as.controller.capability.registry.DelegatingRuntimeCapabilityRegistry;
import org.jboss.as.controller.capability.registry.RequirementRegistration;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistration;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.capability.registry.RuntimeRequirementRegistration;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.extension.ExtensionAddHandler;
import org.jboss.as.controller.extension.MutableRootResourceRegistrationProvider;
import org.jboss.as.controller.extension.ParallelExtensionAddHandler;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.notification.NotificationSupport;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.as.controller.registry.DelegatingResource;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.NotificationHandlerRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.core.security.AccessMechanism;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.threads.AsyncFuture;
import org.jboss.threads.AsyncFutureTask;
import org.wildfly.security.manager.action.GetAccessControlContextAction;


/**
 * Default {@link ModelController} implementation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class ModelControllerImpl implements ModelController {

    private static final String INITIAL_BOOT_OPERATION = "initial-boot-operation";
    private static final String POST_EXTENSION_BOOT_OPERATION = "post-extension-boot-operation";
    static final ModelNode EMPTY_ADDRESS = new ModelNode().setEmptyList();

    static {
        EMPTY_ADDRESS.protect();
    }

    private final ServiceRegistry serviceRegistry;
    private final ServiceTarget serviceTarget;
    private final ModelControllerLock controllerLock = new ModelControllerLock();
    private final ContainerStateMonitor stateMonitor;
    private final AtomicReference<ManagementModelImpl> managementModel = new AtomicReference<>();
    private final ConfigurationPersister persister;
    private final ProcessType processType;
    private final RunningModeControl runningModeControl;
    private final AtomicBoolean bootingFlag = new AtomicBoolean(true);
    private final OperationStepHandler prepareStep;
    private final ControlledProcessState processState;
    private final ExecutorService executorService;
    private final ExpressionResolver expressionResolver;
    private final Authorizer authorizer;

    private final ConcurrentMap<Integer, OperationContextImpl> activeOperations = new ConcurrentHashMap<>();
    private final ManagedAuditLogger auditLogger;
    private final BootErrorCollector bootErrorCollector;

    private final NotificationSupport notificationSupport;

    /** Tracks the relationship between domain resources and hosts and server groups */
    private final HostServerGroupTracker hostServerGroupTracker;
    private final Resource.ResourceEntry modelControllerResource;

    ModelControllerImpl(final ServiceRegistry serviceRegistry, final ServiceTarget serviceTarget,
                        final ManagementResourceRegistration rootRegistration,
                        final ContainerStateMonitor stateMonitor, final ConfigurationPersister persister,
                        final ProcessType processType, final RunningModeControl runningModeControl,
                        final OperationStepHandler prepareStep, final ControlledProcessState processState, final ExecutorService executorService,
                        final ExpressionResolver expressionResolver, final Authorizer authorizer,
                        final ManagedAuditLogger auditLogger, NotificationSupport notificationSupport, final BootErrorCollector bootErrorCollector) {
        assert serviceRegistry != null;
        this.serviceRegistry = serviceRegistry;
        assert serviceTarget != null;
        this.serviceTarget = serviceTarget;
        assert rootRegistration != null;
        ManagementModelImpl mmi = new ManagementModelImpl(rootRegistration, Resource.Factory.create(),
                new CapabilityRegistryImpl(processType.isServer()));
        mmi.publish();
        assert stateMonitor != null;
        this.stateMonitor = stateMonitor;
        assert persister != null;
        this.persister = persister;
        assert processType != null;
        this.processType = processType;
        assert runningModeControl != null;
        this.runningModeControl = runningModeControl;
        assert notificationSupport != null;
        this.notificationSupport = notificationSupport;
        this.prepareStep = prepareStep == null ? new DefaultPrepareStepHandler() : prepareStep;
        assert processState != null;
        this.processState = processState;
        this.serviceTarget.addListener(stateMonitor);
        this.executorService = executorService;
        assert expressionResolver != null;
        this.expressionResolver = expressionResolver;
        assert authorizer != null;
        this.authorizer = authorizer;
        assert auditLogger != null;
        this.auditLogger = auditLogger;
        assert bootErrorCollector != null;
        this.bootErrorCollector = bootErrorCollector;
        this.hostServerGroupTracker = processType.isManagedDomain() ? new HostServerGroupTracker() : null;
        this.modelControllerResource = new ModelControllerResource();
        auditLogger.startBoot();
    }

    /**
     * Executes an operation on the controller
     * @param operation the operation
     * @param handler the handler
     * @param control the transaction control
     * @param attachments the operation attachments
     * @return the result of the operation
     */
    public ModelNode execute(final ModelNode operation, final OperationMessageHandler handler, final OperationTransactionControl control, final OperationAttachments attachments) {
        return internalExecute(operation, handler, control, attachments, prepareStep, false);
    }

    /**
     * Executes an operation on the controller latching onto an existing transaction
     *
     * @param operation the operation
     * @param handler the handler
     * @param control the transaction control
     * @param attachments the operation attachments
     * @param prepareStep the prepare step to be executed before any other steps
     * @param operationId the id of the current transaction
     * @return the result of the operation
     */
    protected ModelNode executeReadOnlyOperation(final ModelNode operation, final OperationMessageHandler handler, final OperationTransactionControl control, final OperationAttachments attachments, final OperationStepHandler prepareStep, final int operationId) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ModelController.ACCESS_PERMISSION);
        }

        // Get the primary context to delegate the reads to
        final AbstractOperationContext delegateContext = activeOperations.get(operationId);
        if(delegateContext == null) {
            // TODO we might just allow this case too, but for now it's just wrong (internal) usage
            throw ControllerLogger.ROOT_LOGGER.noContextToDelegateTo(operationId);
        }
        final ModelNode response = new ModelNode();
        final OperationTransactionControl originalResultTxControl = control == null ? null : new OperationTransactionControl() {
            @Override
            public void operationPrepared(OperationTransaction transaction, ModelNode result) {
                control.operationPrepared(transaction, response);
            }
        };
        // Use a read-only context
        final ReadOnlyContext context = new ReadOnlyContext(processType, runningModeControl.getRunningMode(), originalResultTxControl, processState, false, delegateContext, this, operationId);
        context.addStep(response, operation, prepareStep, OperationContext.Stage.MODEL);
        CurrentOperationIdHolder.setCurrentOperationID(operationId);
        try {
            context.executeOperation();
        } finally {
            CurrentOperationIdHolder.setCurrentOperationID(null);
        }

        if (!response.hasDefined(RESPONSE_HEADERS) || !response.get(RESPONSE_HEADERS).hasDefined(PROCESS_STATE)) {
            ControlledProcessState.State state = processState.getState();
            switch (state) {
                case RELOAD_REQUIRED:
                case RESTART_REQUIRED:
                    response.get(RESPONSE_HEADERS, PROCESS_STATE).set(state.toString());
                    break;
                default:
                    break;
            }
        }
        return response;
    }

    /**
     * Executes an operation on the controller
     * @param operation the operation
     * @param handler the handler
     * @param control the transaction control
     * @param attachments the operation attachments
     * @param prepareStep the prepare step to be executed before any other steps
     * @param attemptLock set to {@code true} to try to obtain the controller lock
     * @return the result of the operation
     */
    protected ModelNode internalExecute(final ModelNode operation, final OperationMessageHandler handler, final OperationTransactionControl control,
                                        final OperationAttachments attachments, final OperationStepHandler prepareStep, final boolean attemptLock) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ModelController.ACCESS_PERMISSION);
        }

        final ModelNode headers = operation.has(OPERATION_HEADERS) ? operation.get(OPERATION_HEADERS) : null;
        final boolean rollbackOnFailure = headers == null || !headers.hasDefined(ROLLBACK_ON_RUNTIME_FAILURE) || headers.get(ROLLBACK_ON_RUNTIME_FAILURE).asBoolean();
        final EnumSet<OperationContextImpl.ContextFlag> contextFlags = rollbackOnFailure ? EnumSet.of(OperationContextImpl.ContextFlag.ROLLBACK_ON_FAIL) : EnumSet.noneOf(OperationContextImpl.ContextFlag.class);
        final boolean restartResourceServices = headers != null && headers.hasDefined(ALLOW_RESOURCE_SERVICE_RESTART) && headers.get(ALLOW_RESOURCE_SERVICE_RESTART).asBoolean();
        if (restartResourceServices) {
            contextFlags.add(OperationContextImpl.ContextFlag.ALLOW_RESOURCE_SERVICE_RESTART);
        }
        final ModelNode blockingTimeoutConfig = headers != null && headers.hasDefined(BLOCKING_TIMEOUT) ? headers.get(BLOCKING_TIMEOUT) : null;

        final ModelNode response = new ModelNode();
        // Report the correct operation response, otherwise the preparedResult would only contain
        // the result of the last active step in a composite operation
        final OperationTransactionControl originalResultTxControl = control == null ? null : new OperationTransactionControl() {
            @Override
            public void operationPrepared(OperationTransaction transaction, ModelNode result) {
                control.operationPrepared(transaction, response);
            }
        };

        AccessMechanism accessMechanism = null;
        AccessAuditContext accessContext = SecurityActions.currentAccessAuditContext();
        if (accessContext != null) {
            if (operation.hasDefined(OPERATION_HEADERS)) {
                ModelNode operationHeaders = operation.get(OPERATION_HEADERS);
                if (operationHeaders.hasDefined(DOMAIN_UUID)) {
                    accessContext.setDomainUuid(operationHeaders.get(DOMAIN_UUID).asString());
                }
                if (operationHeaders.hasDefined(ACCESS_MECHANISM)) {
                    accessContext
                            .setAccessMechanism(AccessMechanism.valueOf(operationHeaders.get(ACCESS_MECHANISM).asString()));
                }
            }
            accessMechanism = accessContext.getAccessMechanism();
        }

        for (;;) {
            // Create a random operation-id
            final Integer operationID = new Random(new SecureRandom().nextLong()).nextInt();
            final OperationContextImpl context = new OperationContextImpl(operationID, operation.get(OP).asString(),
                    operation.get(OP_ADDR), this, processType, runningModeControl.getRunningMode(),
                    contextFlags, handler, attachments, managementModel.get(), originalResultTxControl, processState, auditLogger,
                    bootingFlag.get(), hostServerGroupTracker, blockingTimeoutConfig, accessMechanism, notificationSupport);
            // Try again if the operation-id is already taken
            if(activeOperations.putIfAbsent(operationID, context) == null) {
                CurrentOperationIdHolder.setCurrentOperationID(operationID);
                boolean shouldUnlock = false;
                try {
                    if (attemptLock) {
                        if (!controllerLock.detectDeadlockAndGetLock(operationID)) {
                            response.get(OUTCOME).set(FAILED);
                            response.get(FAILURE_DESCRIPTION).set(ControllerLogger.ROOT_LOGGER.cannotGetControllerLock());
                            return response;
                        }
                        shouldUnlock = true;
                    }

                    context.addStep(response, operation, prepareStep, OperationContext.Stage.MODEL);
                    context.executeOperation();
                } finally {

                    if (!response.hasDefined(RESPONSE_HEADERS) || !response.get(RESPONSE_HEADERS).hasDefined(PROCESS_STATE)) {
                        ControlledProcessState.State state = processState.getState();
                        switch (state) {
                            case RELOAD_REQUIRED:
                            case RESTART_REQUIRED:
                                response.get(RESPONSE_HEADERS, PROCESS_STATE).set(state.toString());
                                break;
                            default:
                                break;
                        }
                    }

                    if (shouldUnlock) {
                        controllerLock.unlock(operationID);
                    }
                    activeOperations.remove(operationID);
                    CurrentOperationIdHolder.setCurrentOperationID(null);
                }
                break;
            }
        }
        return response;
    }

    boolean boot(final List<ModelNode> bootList, final OperationMessageHandler handler, final OperationTransactionControl control,
              final boolean rollbackOnRuntimeFailure) {

        final Integer operationID = new Random(new SecureRandom().nextLong()).nextInt();

        EnumSet<OperationContextImpl.ContextFlag> contextFlags = rollbackOnRuntimeFailure
                ? EnumSet.of(OperationContextImpl.ContextFlag.ROLLBACK_ON_FAIL)
                : EnumSet.noneOf(OperationContextImpl.ContextFlag.class);
        final OperationContextImpl context = new OperationContextImpl(operationID, INITIAL_BOOT_OPERATION, EMPTY_ADDRESS,
                this, processType, runningModeControl.getRunningMode(),
                contextFlags, handler, null, managementModel.get(), control, processState, auditLogger, bootingFlag.get(),
                hostServerGroupTracker, null, null, notificationSupport);

        // Add to the context all ops prior to the first ExtensionAddHandler as well as all ExtensionAddHandlers; save the rest.
        // This gets extensions registered before proceeding to other ops that count on these registrations
        BootOperations bootOperations = organizeBootOperations(bootList, operationID);
        for (ParsedBootOp initialOp : bootOperations.initialOps) {
            context.addBootStep(initialOp);
        }
        if (bootOperations.invalid) {
            // something was wrong so ensure we fail
            context.setRollbackOnly();
        }
        // Run the steps up to the last ExtensionAddHandler
        OperationContext.ResultAction resultAction = context.executeOperation();
        if (resultAction == OperationContext.ResultAction.KEEP && bootOperations.postExtensionOps != null) {

            // Success. Now any extension handlers are registered. Continue with remaining ops
            final OperationContextImpl postExtContext = new OperationContextImpl(operationID, POST_EXTENSION_BOOT_OPERATION,
                    EMPTY_ADDRESS, this, processType, runningModeControl.getRunningMode(),
                    contextFlags, handler, null, managementModel.get(), control, processState, auditLogger,
                            bootingFlag.get(), hostServerGroupTracker, null, null, notificationSupport);

            for (ParsedBootOp parsedOp : bootOperations.postExtensionOps) {
                if (parsedOp.handler == null) {
                    // The extension should have registered the handler now
                    parsedOp = new ParsedBootOp(parsedOp,
                            managementModel.get().getRootResourceRegistration().getOperationHandler(parsedOp.address, parsedOp.operationName));
                }
                if (parsedOp.handler == null) {
                    logNoHandler(parsedOp);
                    postExtContext.setRollbackOnly();
                    // stop
                    break;
                } else {
                    postExtContext.addBootStep(parsedOp);
                }
            }

            resultAction = postExtContext.executeOperation();
        }

        return  resultAction == OperationContext.ResultAction.KEEP;
    }

    /**
     * Organizes the list of boot operations such that all extension add operations are executed in the given context,
     * while all non-extension add operations found after the first extension add are stored for subsequent invocation
     * in a separate context. Also:
     * <ol>
     *     <li>Ensures that any operations affecting interfaces or sockets are run before any operations affecting
     *     subsystems. This improves boot performance by ensuring required services are available as soon as possible.
     *     </li>
     *     <li>If an executor service is available, organizes all extension add ops so the extension initialization
     *      can be done in parallel by the executor service.
     *     </li>
     *     <li>If an executor service is available and the controller type is SERVER, organizes all subsystem ops so
     *     they can be done in parallel by the executor service.
     *     </li>
     * </ol>
     *
     *
     * @param bootList the list of boot operations
     * @param lockPermit lockPermit to use in any {@link org.jboss.as.controller.ParallelBootOperationContext}
     * @return data structure organizing the boot ops for initial execution and post-extension-add execution
     */
    private BootOperations organizeBootOperations(List<ModelNode> bootList, final int lockPermit) {

        final List<ParsedBootOp> initialOps = new ArrayList<ParsedBootOp>();
        List<ParsedBootOp> postExtensionOps = null;
        boolean invalid = false;
        boolean sawExtensionAdd = false;
        ManagementResourceRegistration rootRegistration = managementModel.get().getRootResourceRegistration();
        ParallelExtensionAddHandler parallelExtensionAddHandler = executorService == null ? null : new ParallelExtensionAddHandler(executorService, getMutableRootResourceRegistrationProvider());
        ParallelBootOperationStepHandler parallelSubsystemHandler = (executorService != null && processType.isServer() && runningModeControl.getRunningMode() == RunningMode.NORMAL)
                ? new ParallelBootOperationStepHandler(executorService, rootRegistration, processState, this, lockPermit) : null;
        boolean registeredParallelSubsystemHandler = false;
        int subsystemIndex = 0;
        for (ModelNode bootOp : bootList) {
            final ParsedBootOp parsedOp = new ParsedBootOp(bootOp);
            if (postExtensionOps != null) {
                // Handle cases like AppClient where extension adds are interleaved with subsystem ops
                if (parsedOp.isExtensionAdd()) {
                    final ExtensionAddHandler stepHandler = (ExtensionAddHandler) rootRegistration.getOperationHandler(parsedOp.address, parsedOp.operationName);
                    if (parallelExtensionAddHandler != null) {
                        parallelExtensionAddHandler.addParsedOp(parsedOp, stepHandler);
                    } else {
                        initialOps.add(new ParsedBootOp(parsedOp, stepHandler));
                    }
                } else {
                    if (parallelSubsystemHandler == null || !parallelSubsystemHandler.addSubsystemOperation(parsedOp)) {
                        // Put any interface/socket op before the subsystem op
                        if (registeredParallelSubsystemHandler && (parsedOp.isInterfaceOperation() || parsedOp.isSocketOperation())) {
                            postExtensionOps.add(subsystemIndex++, parsedOp);
                        } else {
                            postExtensionOps.add(parsedOp);
                        }
                    } else if (!registeredParallelSubsystemHandler) {
                        postExtensionOps.add(parallelSubsystemHandler.getParsedBootOp());
                        subsystemIndex = postExtensionOps.size() - 1;
                        registeredParallelSubsystemHandler = true;
                    }
                }
            } else {
                final OperationStepHandler stepHandler = rootRegistration.getOperationHandler(parsedOp.address, parsedOp.operationName);
                if (!sawExtensionAdd && stepHandler == null) {
                    // Odd case. An op prior to the first extension add where there is no handler. This would really
                    // only happen during AS development
                    logNoHandler(parsedOp);
                    invalid = true;
                    // stop
                    break;
                } else if (stepHandler instanceof ExtensionAddHandler) {
                    if (parallelExtensionAddHandler != null) {
                        parallelExtensionAddHandler.addParsedOp(parsedOp, (ExtensionAddHandler) stepHandler);
                        if (!sawExtensionAdd) {
                            initialOps.add(parallelExtensionAddHandler.getParsedBootOp());
                        }
                    } else {
                        initialOps.add(new ParsedBootOp(parsedOp, stepHandler));
                    }
                    sawExtensionAdd = true;
                } else if (!sawExtensionAdd) {
                    // An operation prior to the first Extension Add
                    initialOps.add(new ParsedBootOp(parsedOp, stepHandler));
                } else {
                    // Start the postExtension list
                    postExtensionOps = new ArrayList<ParsedBootOp>(32);
                    if (parallelSubsystemHandler == null || !parallelSubsystemHandler.addSubsystemOperation(parsedOp)) {
                        postExtensionOps.add(parsedOp);
                    } else {
                        // First subsystem op; register the parallel handler and add the op to it
                        postExtensionOps.add(parallelSubsystemHandler.getParsedBootOp());
                        registeredParallelSubsystemHandler = true;
                    }
                }
            }
        }


        return new BootOperations(initialOps, postExtensionOps, invalid);
    }

    void finishBoot() {
        // Notify the audit logger that we're done booting
        auditLogger.bootDone();
        bootingFlag.set(false);
    }

    ManagementModel getManagementModel() {
        return managementModel.get();
    }

    Resource.ResourceEntry getModelControllerResource() {
        return modelControllerResource;
    }

    public ModelControllerClient createClient(final Executor executor) {

        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ModelController.ACCESS_PERMISSION);
        }

        return new ModelControllerClient() {

            @Override
            public void close() throws IOException {
                // whatever
            }

            @Override
            public ModelNode execute(ModelNode operation) throws IOException {
                return execute(operation, null);
            }

            @Override
            public ModelNode execute(Operation operation) throws IOException {
                return execute(operation, null);
            }

            @Override
            public ModelNode execute(final ModelNode operation, final OperationMessageHandler messageHandler) {
                return ModelControllerImpl.this.execute(operation, messageHandler, OperationTransactionControl.COMMIT, null);
            }

            @Override
            public ModelNode execute(Operation operation, OperationMessageHandler messageHandler) throws IOException {
                return ModelControllerImpl.this.execute(operation.getOperation(), messageHandler, OperationTransactionControl.COMMIT, operation);
            }

            @Override
            public AsyncFuture<ModelNode> executeAsync(ModelNode operation, OperationMessageHandler messageHandler) {
                return executeAsync(operation, messageHandler, null);
            }

            @Override
            public AsyncFuture<ModelNode> executeAsync(final Operation operation, final OperationMessageHandler messageHandler) {
                return executeAsync(operation.getOperation(), messageHandler, operation);
            }

            private AsyncFuture<ModelNode> executeAsync(final ModelNode operation, final OperationMessageHandler messageHandler, final OperationAttachments attachments) {
                if (executor == null) {
                    throw ControllerLogger.ROOT_LOGGER.nullAsynchronousExecutor();
                }
                final AtomicReference<Thread> opThread = new AtomicReference<Thread>();
                class OpTask extends AsyncFutureTask<ModelNode> {
                    OpTask() {
                        super(executor);
                    }

                    public void asyncCancel(final boolean interruptionDesired) {
                        Thread thread = opThread.getAndSet(Thread.currentThread());
                        if (thread == null) {
                            setCancelled();
                        } else {
                            // Interrupt the request execution
                            thread.interrupt();
                            // Wait for the cancellation to clear opThread
                            boolean interrupted = false;
                            synchronized (opThread) {
                                while (opThread.get() != null) {
                                    try {
                                        opThread.wait();
                                    } catch (InterruptedException ie) {
                                        interrupted = true;
                                    }
                                }
                            }
                            setCancelled();
                            if (interrupted) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }

                    void handleResult(final ModelNode result) {
                        if (result != null && result.hasDefined(OUTCOME) && CANCELLED.equals(result.get(OUTCOME).asString())) {
                            setCancelled();
                        } else {
                            setResult(result);
                        }
                    }
                }
                final OpTask opTask = new OpTask();
                final AccessControlContext acc = doPrivileged(GetAccessControlContextAction.getInstance());
                executor.execute(new Runnable() {
                    public void run() {
                        try {
                            if (opThread.compareAndSet(null, Thread.currentThread())) {
                                ModelNode response = doPrivileged(new PrivilegedAction<ModelNode>() {

                                    @Override
                                    public ModelNode run() {
                                        return ModelControllerImpl.this.execute(operation, messageHandler,
                                                OperationTransactionControl.COMMIT, attachments);
                                    }
                                }, acc);
                                opTask.handleResult(response);
                            }
                        } finally {
                            synchronized (opThread) {
                                opThread.set(null);
                                opThread.notifyAll();
                            }
                        }
                    }
                });
                return opTask;
            }
        };
    }

    ConfigurationPersister.PersistenceResource writeModel(final ManagementModelImpl model, Set<PathAddress> affectedAddresses) throws ConfigurationPersistenceException {
        ControllerLogger.MGMT_OP_LOGGER.tracef("persisting %s from %s", model.rootResource, model);
        final ModelNode newModel = Resource.Tools.readModel(model.rootResource);
        final ConfigurationPersister.PersistenceResource delegate = persister.store(newModel, affectedAddresses);
        return new ConfigurationPersister.PersistenceResource() {

            @Override
            public void commit() {
                // Discard the tracker first, so if there's any race the new OperationContextImpl
                // gets a cleared tracker
                if (hostServerGroupTracker != null) {
                    hostServerGroupTracker.invalidate();
                }
                model.publish();
                delegate.commit();
            }

            @Override
            public void rollback() {
                delegate.rollback();
            }
        };
    }

    void acquireLock(Integer permit, final boolean interruptibly) throws InterruptedException {
        if (interruptibly) {
            //noinspection LockAcquiredButNotSafelyReleased
            controllerLock.lockInterruptibly(permit);
        } else {
            //noinspection LockAcquiredButNotSafelyReleased
            controllerLock.lock(permit);
        }
    }

    boolean acquireLock(Integer permit, final boolean interruptibly, long timeout) throws InterruptedException {
        if (interruptibly) {
            //noinspection LockAcquiredButNotSafelyReleased
            return controllerLock.lockInterruptibly(permit, timeout, TimeUnit.SECONDS);
        } else {
            //noinspection LockAcquiredButNotSafelyReleased
            return controllerLock.lock(permit, timeout, TimeUnit.SECONDS);
        }
    }

    void releaseLock(Integer permit) {
        controllerLock.unlock(permit);
    }

    /**
     * Log a report of any problematic container state changes and reset container state change history
     * so another run of this method or of {@link #awaitContainerStateChangeReport(long, java.util.concurrent.TimeUnit)}
     * will produce a report not including any changes included in a report returned by this run.
     */
    void logContainerStateChangesAndReset() {
        stateMonitor.logContainerStateChangesAndReset();
    }

    /**
     * Await service container stability.
     *
     * @param timeout maximum period to wait for service container stability
     * @param timeUnit unit in which {@code timeout} is expressed
     * @param interruptibly {@code true} if thread interruption should be ignored
     *
     * @throws java.lang.InterruptedException if {@code interruptibly} is {@code false} and the thread is interrupted while awaiting service container stability
     * @throws java.util.concurrent.TimeoutException if service container stability is not reached before the specified timeout
     */
    void awaitContainerStability(long timeout, TimeUnit timeUnit, final boolean interruptibly)
            throws InterruptedException, TimeoutException {
        if (interruptibly) {
            stateMonitor.awaitStability(timeout, timeUnit);
        } else {
            stateMonitor.awaitStabilityUninterruptibly(timeout, timeUnit);
        }
    }

    /**
     * Await service container stability and then report on container state changes. Does not reset change history,
     * so another run of this method with no intervening call to {@link #logContainerStateChangesAndReset()} will produce a report including
     * any changes included in a report returned by the first run.
     *
     * @param timeout maximum period to wait for service container stability
     * @param timeUnit unit in which {@code timeout} is expressed
     *
     * @return a change report, or {@code null} if there is nothing to report
     *
     * @throws java.lang.InterruptedException if the thread is interrupted while awaiting service container stability
     * @throws java.util.concurrent.TimeoutException if service container stability is not reached before the specified timeout
     */
    ContainerStateMonitor.ContainerStateChangeReport awaitContainerStateChangeReport(long timeout, TimeUnit timeUnit)
            throws InterruptedException, TimeoutException {
        return stateMonitor.awaitContainerStateChangeReport(timeout, timeUnit);
    }

    ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }

    ServiceTarget getServiceTarget() {
        return serviceTarget;
    }

    @Override
    public NotificationHandlerRegistration getNotificationRegistry() {
        return notificationSupport.getNotificationRegistry();
    }

    NotificationSupport getNotificationSupport() {
        return notificationSupport;
    }

    ModelNode resolveExpressions(ModelNode node) throws OperationFailedException {
        return expressionResolver.resolveExpressions(node);
    }

    Authorizer getAuthorizer() {
        return authorizer;
    }

    private void logNoHandler(ParsedBootOp parsedOp) {
        ImmutableManagementResourceRegistration child = managementModel.get().getRootResourceRegistration().getSubModel(parsedOp.address);
        if (child == null) {
            ROOT_LOGGER.error(ROOT_LOGGER.noSuchResourceType(parsedOp.address));
        } else {
            ROOT_LOGGER.error(ROOT_LOGGER.noHandlerForOperation(parsedOp.operationName, parsedOp.address));
        }

    }

    AuditLogger getAuditLogger() {
        return auditLogger;
    }

    static MutableRootResourceRegistrationProvider getMutableRootResourceRegistrationProvider() {
        return MutableRootResourceRegistrationProviderImpl.INSTANCE;
    }

    void addFailureDescription(ModelNode operation, ModelNode failure) {
        if (bootingFlag.get()) {
            bootErrorCollector.addFailureDescription(operation, failure);
        }
    }

    private class DefaultPrepareStepHandler implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            if (MGMT_OP_LOGGER.isTraceEnabled()) {
                MGMT_OP_LOGGER.tracef("Executing %s %s", operation.get(OP), operation.get(OP_ADDR));
            }
            final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
            final String operationName =  operation.require(OP).asString();
            final OperationStepHandler stepHandler = resolveOperationHandler(address, operationName);
            if(stepHandler != null) {
                context.addStep(stepHandler, OperationContext.Stage.MODEL);
            } else {

                ImmutableManagementResourceRegistration child = managementModel.get().getRootResourceRegistration().getSubModel(address);
                if (child == null) {
                    context.getFailureDescription().set(ControllerLogger.ROOT_LOGGER.noSuchResourceType(address));
                } else {
                    context.getFailureDescription().set(ControllerLogger.ROOT_LOGGER.noHandlerForOperation(operationName, address));
                }
            }
            context.completeStep(OperationContext.ResultHandler.NOOP_RESULT_HANDLER);
        }
    }

    private OperationStepHandler resolveOperationHandler(final PathAddress address, final String operationName) {
        ManagementResourceRegistration rootRegistration = managementModel.get().getRootResourceRegistration();
        OperationStepHandler result = rootRegistration.getOperationHandler(address, operationName);
        if (result == null && address.size() > 0) {
            // For wildcard elements, check specific registrations where the same OSH is used
            // for all such registrations
            PathElement pe = address.getLastElement();
            if (pe.isWildcard()) {
                String type = pe.getKey();
                PathAddress parent = address.subAddress(0, address.size() - 1);
                Set<PathElement> children = rootRegistration.getChildAddresses(parent);
                if (children != null) {
                    OperationStepHandler found = null;
                    for (PathElement child : children) {
                        if (type.equals(child.getKey())) {
                            OperationEntry oe = rootRegistration.getOperationEntry(parent.append(child), operationName);
                            OperationStepHandler osh = oe == null ? null : oe.getOperationHandler();
                            if (osh == null || (found != null && !found.equals(osh))) {
                                // Not all children have the same handler; give up
                                found = null;
                                break;
                            }
                            // We have a candidate OSH
                            found = osh;
                        }
                    }
                    if (found != null) {
                        result = found;
                    }
                }
            }
        }
        return result;
    }

    private static final class BootOperations {
        private final List<ParsedBootOp> initialOps;
        private final List<ParsedBootOp> postExtensionOps;
        private final boolean invalid;

        private BootOperations(List<ParsedBootOp> initialOps, List<ParsedBootOp> postExtensionOps, boolean invalid) {
            this.initialOps = initialOps;
            this.postExtensionOps = postExtensionOps;
            this.invalid = invalid;
        }
    }

    private final class ModelControllerResource extends PlaceholderResource.PlaceholderResourceEntry {

        private ModelControllerResource() {
            super(SERVICE, MANAGEMENT_OPERATIONS);
        }

        @Override
        public boolean hasChild(PathElement element) {
            try {
                return ACTIVE_OPERATION.equals(element.getKey())
                        && activeOperations.containsKey(Integer.valueOf(element.getValue()));
            } catch (NumberFormatException e) {
                return false;
            }
        }

        @Override
        public Resource getChild(PathElement element) {
            Resource result = null;
            if (ACTIVE_OPERATION.equals(element.getKey())) {
                try {
                    OperationContextImpl context = activeOperations.get(Integer.valueOf(element.getValue()));
                    if (context != null) {
                        result = context.getActiveOperationResource();
                    }
                } catch (NumberFormatException e) {
                    // just return null
                }
            }
            return result;
        }

        @Override
        public Resource requireChild(PathElement element) {
            final Resource resource = getChild(element);
            if(resource == null) {
                throw new NoSuchResourceException(element);
            }
            return resource;
        }

        @Override
        public boolean hasChildren(String childType) {
            return ACTIVE_OPERATION.equals(childType) && activeOperations.size() > 0;
        }

        @Override
        public Set<String> getChildTypes() {
            return Collections.singleton(ACTIVE_OPERATION);
        }

        @Override
        public Set<String> getChildrenNames(String childType) {
            Set<String> result = new HashSet<String>(activeOperations.size());
            for (Integer id : activeOperations.keySet()) {
                result.add(id.toString());
            }
            return result;
        }

        @Override
        public Set<ResourceEntry> getChildren(String childType) {
            Set<ResourceEntry> result = new HashSet<ResourceEntry>(activeOperations.size());
            for (OperationContextImpl context : activeOperations.values()) {
                result.add(context.getActiveOperationResource());
            }
            return result;
        }
    }

    private static class MutableRootResourceRegistrationProviderImpl implements MutableRootResourceRegistrationProvider {
        private static final MutableRootResourceRegistrationProvider INSTANCE = new MutableRootResourceRegistrationProviderImpl();

        @Override
        public ManagementResourceRegistration getRootResourceRegistrationForUpdate(OperationContext context) {
            assert context instanceof AbstractOperationContext;
            return ((AbstractOperationContext) context).getRootResourceRegistrationForUpdate();
        }
    }

    final class ManagementModelImpl implements ManagementModel {
        // The root MRR
        private final ManagementResourceRegistration resourceRegistration;
        // The possibly unpublished root Resource
        private final Resource rootResource;
        // The root MRR we expose
        private final ManagementResourceRegistration delegatingResourceRegistration;
        // The root Resource we expose
        private final Resource delegatingResource;
        // The capability registry
        private final CapabilityRegistryImpl capabilityRegistry;
        // The capability registry we expose
        private final RuntimeCapabilityRegistry delegatingCapabilityRegistry;
        private volatile boolean published;

        ManagementModelImpl(final ManagementResourceRegistration resourceRegistration,
                            final Resource rootResource,
                            final CapabilityRegistryImpl capabilityRegistry) {
            this.resourceRegistration = resourceRegistration;
            this.rootResource = rootResource;
            this.capabilityRegistry = capabilityRegistry;
            // What we expose depends on the state of our 'published' field. If 'true' we've been published
            // to the ModelController, and from then on callers should get whatever the MC has as current.
            // If 'false' we haven't been published; we are a local copy created by some OperationContext,
            // so callers should see our local members

            // TODO use this if we ever actually support cloning the MRR
//            this.delegatingResourceRegistration = new DelegatingManagementResourceRegistration(new DelegatingManagementResourceRegistration.RegistrationDelegateProvider() {
//                @Override
//                public ManagementResourceRegistration getDelegateRegistration() {
//                    ManagementResourceRegistration result;
//                    if (published) {
//                        result = ModelControllerImpl.this.managementModel.get().resourceRegistration;
//                    } else {
//                        result = resourceRegistration;
//                    }
//                    return result;
//                }
//            });
            this.delegatingResourceRegistration = resourceRegistration;
            this.delegatingResource = new DelegatingResource(new DelegatingResource.ResourceDelegateProvider() {
                @Override
                public Resource getDelegateResource() {
                    Resource result;
                    if (published) {
                        result = ModelControllerImpl.this.managementModel.get().rootResource;
                    } else {
                        result = rootResource;
                    }
                    return result;
                }
            });
            this.delegatingCapabilityRegistry = new DelegatingRuntimeCapabilityRegistry(new DelegatingRuntimeCapabilityRegistry.CapabilityRegistryDelegateProvider() {
                @Override
                public RuntimeCapabilityRegistry getDelegateCapabilityRegistry() {
                    RuntimeCapabilityRegistry result;
                    if (published) {
                        result = ModelControllerImpl.this.managementModel.get().capabilityRegistry;
                    } else {
                        result = capabilityRegistry;
                    }
                    return result;
                }
            });
        }

        @Override
        public ManagementResourceRegistration getRootResourceRegistration() {
            return delegatingResourceRegistration;
        }

        @Override
        public Resource getRootResource() {
            return delegatingResource;
        }

        @Override
        public RuntimeCapabilityRegistry getCapabilityRegistry() {
            return delegatingCapabilityRegistry;
        }

        /**
         * Creates a new {@code ManagementModelImpl} that uses a clone of this one's root {@link ManagementResourceRegistration}.
         * The caller can safely modify that {@code ManagementResourceRegistration} without changes being exposed
         * to other callers. Use {@link org.jboss.as.controller.ModelControllerImpl#writeModel(org.jboss.as.controller.ModelControllerImpl.ManagementModelImpl, java.util.Set)}
         * to publish changes.
         *
         * @return the new {@code ManagementModelImpl}. Will not return {@code null}
         */
        /* TODO enable this if we ever support actually cloning the MRR
        ManagementModelImpl cloneRootResourceRegistration() {
            ManagementResourceRegistration mrr;
            Resource currentResource;
            CapabilityRegistryImpl currentCaps;
            if (published) {
                // This is the first clone since this was published. Use the current stuff as the basis
                // to ensure that the clone is based on the latest even if we are not the latest.
                ManagementModelImpl currentPublished = ModelControllerImpl.this.managementModel.get();
                mrr = currentPublished.resourceRegistration;
                currentResource = currentPublished.rootResource;
            } else {
                // We've already been cloned, which means the thread calling this has the controller lock
                // and our stuff hasn't been superceded by another thread. So use our stuff
                mrr = resourceRegistration;
                currentResource = rootResource;
            }
            ManagementResourceRegistration clone = mrr.clone();
            ManagementModelImpl result = new ManagementModelImpl(clone, currentResource, currentCaps);
            ControllerLogger.MGMT_OP_LOGGER.tracef("cloned to %s to create %s and %s", mrr, clone, result);
            return result;
        }
        */

        /**
         * Creates a new {@code ManagementModelImpl} that uses a clone of this one's root {@link Resource}.
         * The caller can safely modify that {@code Resource} without changes being exposed
         * to other callers. Use {@link org.jboss.as.controller.ModelControllerImpl#writeModel(org.jboss.as.controller.ModelControllerImpl.ManagementModelImpl, java.util.Set)}
         * to publish changes.
         *
         * @return the new {@code ManagementModelImpl}. Will not return {@code null}
         */
        ManagementModelImpl cloneRootResource() {
            ManagementResourceRegistration mrr;
            Resource currentResource;
            CapabilityRegistryImpl currentCaps;
            if (published) {
                // This is the first clone since this was published. Use the current stuff as the basis
                // to ensure that the clone is based on the latest even if we are not the latest.
                ManagementModelImpl currentPublished = ModelControllerImpl.this.managementModel.get();
                mrr = currentPublished.resourceRegistration;
                currentResource = currentPublished.rootResource;
                currentCaps = currentPublished.capabilityRegistry;
            } else {
                // We've already been cloned, which means the thread calling this has the controller lock
                // and our stuff hasn't been superceded by another thread. So use our stuff
                mrr = resourceRegistration;
                currentResource = rootResource;
                currentCaps = capabilityRegistry;
            }
            Resource clone = currentResource.clone();
            ManagementModelImpl result = new ManagementModelImpl(mrr, clone, currentCaps);
            ControllerLogger.MGMT_OP_LOGGER.tracef("cloned to %s to create %s and %s", currentResource, clone, result);
            return result;
        }

        ManagementModelImpl cloneCapabilityRegistry() {
            ManagementResourceRegistration mrr;
            Resource currentResource;
            CapabilityRegistryImpl currentCaps;
            if (published) {
                // This is the first clone since this was published. Use the current stuff as the basis
                // to ensure that the clone is based on the latest even if we are not the latest.
                ManagementModelImpl currentPublished = ModelControllerImpl.this.managementModel.get();
                mrr = currentPublished.resourceRegistration;
                currentResource = currentPublished.rootResource;
                currentCaps = currentPublished.capabilityRegistry;
            } else {
                // We've already been cloned, which means the thread calling this has the controller lock
                // and our stuff hasn't been superceded by another thread. So use our stuff
                mrr = resourceRegistration;
                currentResource = rootResource;
                currentCaps = capabilityRegistry;
            }
            CapabilityRegistryImpl clone = currentCaps.copy();
            ManagementModelImpl result = new ManagementModelImpl(mrr, currentResource, clone);
            ControllerLogger.MGMT_OP_LOGGER.tracef("cloned to %s to create %s and %s", currentCaps, clone, result);
            return result;
        }

        /**
         * Compares the registered requirements to the registered capabilities, returning any missing requirements.
         *
         * Cannot be called while other threads may be adding or removing capabilities
         *
         * @return a map whose keys are missing capabilities and whose values are the names of other capabilities
         *         that require that capability. Will not return {@code null} but may be empty
         */
        Map<CapabilityId, Set<RuntimeRequirementRegistration>> validateCapabilityRegistry() {
            if (!published) {
                return capabilityRegistry.getMissingRequirements();
            } else {
                // we're unmodified so nothing to validate
                return Collections.emptyMap();
            }
        }

        private void publish() {
            ModelControllerImpl.this.managementModel.set(this);
            ControllerLogger.MGMT_OP_LOGGER.tracef("published %s", this);
            published = true;
        }
    }

    /** Capability registry implementation. */
    static class CapabilityRegistryImpl implements RuntimeCapabilityRegistry {

        private final Map<CapabilityId, RuntimeCapabilityRegistration> capabilities = new HashMap<>();
        private final Map<CapabilityId, Set<RuntimeRequirementRegistration>> requirements = new HashMap<>();
        private final boolean forServer;
        private final Map<CapabilityContext, Set<CapabilityContext>> satisfiedByMap;

        CapabilityRegistryImpl(boolean forServer) {
            this.forServer =  forServer;
            satisfiedByMap = forServer ? null : new HashMap<CapabilityContext, Set<CapabilityContext>>();
        }

        @Override
        public synchronized void registerCapability(RuntimeCapabilityRegistration capabilityRegistration) {

            CapabilityId capabilityId = capabilityRegistration.getCapabilityId();
            if (capabilities.containsKey(capabilityId)) {
                throw ControllerLogger.MGMT_OP_LOGGER.capabilityAlreadyRegisteredInContext(capabilityId.getName(),
                        capabilityId.getContext().getName());
            }

            capabilities.put(capabilityId, capabilityRegistration);
            for (String req : capabilityRegistration.getCapability().getRequirements()) {
                registerRequirement(new RuntimeRequirementRegistration(req, capabilityRegistration));
            }

            if (!forServer) {
                CapabilityContext capContext = capabilityRegistration.getCapabilityContext();
                if (!satisfiedByMap.containsKey(capContext)) {
                    Set<CapabilityContext> satisfiesUs = new HashSet<>();
                    satisfiedByMap.put(capContext, satisfiesUs);
                    for (Map.Entry<CapabilityContext, Set<CapabilityContext>> entry : satisfiedByMap.entrySet()) {
                        if (entry.getKey().canSatisfyRequirements(capContext)) {
                            satisfiesUs.add(entry.getKey());
                        }
                        if (capContext.canSatisfyRequirements(entry.getKey())) {
                            entry.getValue().add(capContext);
                        }
                    }
                }
            }
        }

        @Override
        public synchronized void registerAdditionalCapabilityRequirement(RuntimeRequirementRegistration requirement) {
            registerRequirement(requirement);
        }

        private void registerRequirement(RuntimeRequirementRegistration requirement) {
            CapabilityId dependentId = requirement.getDependentId();
            if (!capabilities.containsKey(dependentId)) {
                throw ControllerLogger.MGMT_OP_LOGGER.unknownCapabilityInContext(dependentId.getName(),
                        dependentId.getContext().getName());
            }
            Set<RuntimeRequirementRegistration> dependents = requirements.get(dependentId);
            if (dependents == null) {
                dependents = new HashSet<>();
                requirements.put(dependentId, dependents);
            }
            dependents.add(requirement);
        }

        @Override
        public synchronized void removeCapabilityRequirement(RequirementRegistration requirementRegistration) {
            removeRequirement(requirementRegistration);
        }

        @Override
        public synchronized RuntimeCapabilityRegistration removeCapability(String capabilityName, CapabilityContext context) {
            CapabilityId capabilityId = new CapabilityId(capabilityName, context);
            RuntimeCapabilityRegistration removed = capabilities.remove(capabilityId);
            if (removed != null) {
                for (String req : removed.getCapability().getRequirements()) {
                    removeRequirement(new RuntimeRequirementRegistration(req, removed));
                }
                for (String req : removed.getCapability().getOptionalRequirements()) {
                    removeRequirement(new RuntimeRequirementRegistration(req, removed));
                }
            }
            return removed;
        }

        private synchronized void removeRequirement(RequirementRegistration requirementRegistration) {
            Set<RuntimeRequirementRegistration> dependents = requirements.get(requirementRegistration.getDependentId());
            if (dependents != null) {
                // RequirementRegistration is a valid key for RuntimeRequirementRegistration
                //noinspection SuspiciousMethodCalls
                dependents.remove(requirementRegistration);
                if (dependents.size() == 0) {
                    requirements.remove(requirementRegistration.getDependentId());
                }
            }
        }

        @Override
        public synchronized boolean hasCapability(String capabilityName, CapabilityContext capabilityContext) {
            return findSatisfactoryCapability(capabilityName, capabilityContext) != null;
        }

        @Override
        public synchronized  <T> T getCapabilityRuntimeAPI(String capabilityName, CapabilityContext capabilityContext, Class<T> apiType) {
            CapabilityId capabilityId = findSatisfactoryCapability(capabilityName, capabilityContext);
            if (capabilityId == null) {
                if (forServer) {
                    throw ControllerLogger.MGMT_OP_LOGGER.unknownCapability(capabilityName);
                } else {
                    throw ControllerLogger.MGMT_OP_LOGGER.unknownCapabilityInContext(capabilityName, capabilityContext.getName());
                }
            }
            RuntimeCapabilityRegistration reg = capabilities.get(capabilityId);
            Object api = reg.getCapability().getRuntimeAPI();
            if (api == null) {
                throw ControllerLogger.MGMT_OP_LOGGER.capabilityDoesNotExposeRuntimeAPI(capabilityName);
            }
            return apiType.cast(api);
        }

        synchronized CapabilityRegistryImpl copy() {
            CapabilityRegistryImpl result = new CapabilityRegistryImpl(forServer);
            result.capabilities.putAll(this.capabilities);
            for (Map.Entry<CapabilityId, Set<RuntimeRequirementRegistration>> entry : this.requirements.entrySet()) {
                result.requirements.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
            if (!forServer) {
                result.satisfiedByMap.putAll(this.satisfiedByMap);
            }
            return result;
        }

        synchronized Map<CapabilityId, Set<RuntimeRequirementRegistration>> getMissingRequirements() {
            Map<CapabilityId, Set<RuntimeRequirementRegistration>> result = new HashMap<>();
            for (Map.Entry<CapabilityId, Set<RuntimeRequirementRegistration>> entry : requirements.entrySet()) {
                CapabilityContext dependentContext = entry.getKey().getContext();
                for (RuntimeRequirementRegistration req : entry.getValue()) {
                    CapabilityId satisfiesId = findSatisfactoryCapability(req.getRequiredName(), dependentContext);
                    if (satisfiesId == null) {
                        CapabilityId basicId = new CapabilityId(req.getRequiredName(), dependentContext);
                        Set<RuntimeRequirementRegistration> set = result.get(basicId);
                        if (set == null) {
                            set = new HashSet<>();
                            result.put(basicId, set);
                        }
                        set.add(req);
                    }
                }
            }
            return result;
        }

        private CapabilityId findSatisfactoryCapability(String capabilityName, CapabilityContext capabilityContext) {

            // Check for a simple match
            CapabilityId requestedId = new CapabilityId(capabilityName, capabilityContext);
            if (capabilities.containsKey(requestedId)) {
                return requestedId;
            }

            if (!forServer) {
                // Try other contexts that satisfy the requested one
                for (CapabilityContext satisfies : satisfiedByMap.get(capabilityContext)) {
                    CapabilityId satisfiesId = new CapabilityId(capabilityName, satisfies);
                    if (capabilities.containsKey(satisfiesId)) {
                        return satisfiesId;
                    }
                }
            }
            return null;
        }
    }

}
