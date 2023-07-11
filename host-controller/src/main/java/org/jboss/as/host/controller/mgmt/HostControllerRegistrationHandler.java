/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.mgmt;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.host.controller.logging.HostControllerLogger.DOMAIN_LOGGER;
import static org.jboss.as.process.protocol.ProtocolUtils.expectHeader;

import java.io.DataInput;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.remote.TransactionalProtocolClient;
import org.jboss.as.controller.transform.TransformationTarget;
import org.jboss.as.controller.transform.TransformationTargetImpl;
import org.jboss.as.controller.transform.TransformerRegistry;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.domain.controller.DomainController;
import org.jboss.as.domain.controller.HostConnectionInfo;
import org.jboss.as.domain.controller.HostRegistrations;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.domain.controller.SlaveRegistrationException;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.domain.controller.operations.ReadMasterDomainModelHandler;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.protocol.mgmt.ActiveOperation;
import org.jboss.as.protocol.mgmt.FlushableDataOutput;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.as.protocol.mgmt.ManagementProtocol;
import org.jboss.as.protocol.mgmt.ManagementRequestContext;
import org.jboss.as.protocol.mgmt.ManagementRequestHandler;
import org.jboss.as.protocol.mgmt.ManagementRequestHandlerFactory;
import org.jboss.as.protocol.mgmt.ManagementRequestHeader;
import org.jboss.as.protocol.mgmt.ManagementResponseHeader;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.threads.AsyncFutureTask;
import org.wildfly.common.Assert;

/**
 * Handler responsible for the host-controller registration process. This may involve assembling the correct
 * {@code ManagementRequestHandlerFactory} based on the version of the host-controller registering.
 *
 * @author Emanuel Muckenhuber
 */
public class HostControllerRegistrationHandler implements ManagementRequestHandlerFactory {

    private static final Operation READ_DOMAIN_MODEL;
    private static final ModelNode SUCCESSFUL_RESULT = new ModelNode();

    static {
        ModelNode mn = new ModelNode();
        mn.get(ModelDescriptionConstants.OP).set(ReadMasterDomainModelHandler.OPERATION_NAME);
        mn.get(ModelDescriptionConstants.OP_ADDR).setEmptyList();
        mn.protect();
        READ_DOMAIN_MODEL = OperationBuilder.create(mn).build();

        SUCCESSFUL_RESULT.get(OUTCOME).set(SUCCESS);
        SUCCESSFUL_RESULT.get(RESULT).setEmptyObject();
        SUCCESSFUL_RESULT.protect();
    }

    private final ManagementChannelHandler handler;
    private final OperationExecutor operationExecutor;
    private final DomainController domainController;
    private final Executor registrationExecutor;
    private final HostRegistrations slaveHostRegistrations;
    private final String address;
    private final DomainHostExcludeRegistry domainHostExcludeRegistry;

    public HostControllerRegistrationHandler(ManagementChannelHandler handler, DomainController domainController, OperationExecutor operationExecutor,
                                             Executor registrations, HostRegistrations slaveHostRegistrations,
                                             DomainHostExcludeRegistry domainHostExcludeRegistry) {
        this.handler = handler;
        this.operationExecutor = operationExecutor;
        this.domainController = domainController;
        this.registrationExecutor = registrations;
        this.slaveHostRegistrations = slaveHostRegistrations;
        this.domainHostExcludeRegistry = domainHostExcludeRegistry;
        this.address = HostControllerRegistrationHandler.this.handler.getRemoteAddress().getHostAddress();
    }

    @Override
    public ManagementRequestHandler<?, ?> resolveHandler(final RequestHandlerChain handlers, final ManagementRequestHeader header) {
        if (header.getVersion() != 1) {
            // Send subject
            handler.getAttachments().attach(TransactionalProtocolClient.SEND_IDENTITY, Boolean.TRUE);
        }
        final byte operationId = header.getOperationId();
        switch (operationId) {
            case DomainControllerProtocol.REGISTER_HOST_CONTROLLER_REQUEST: {
                // Start the registration process
                final RegistrationContext context = new RegistrationContext(domainController.getExtensionRegistry(),
                        true, domainHostExcludeRegistry);
                context.activeOperation = handlers.registerActiveOperation(header.getBatchId(), context, context);
                return new InitiateRegistrationHandler();
            }
            case DomainControllerProtocol.FETCH_DOMAIN_CONFIGURATION_REQUEST: {
                // Start the fetch the domain model process
                final RegistrationContext context = new RegistrationContext(domainController.getExtensionRegistry(),
                        false, domainHostExcludeRegistry);
                context.activeOperation = handlers.registerActiveOperation(header.getBatchId(), context, context);
                return new InitiateRegistrationHandler();
            }
            case DomainControllerProtocol.REQUEST_SUBSYSTEM_VERSIONS:
                // register the subsystem versions
                return new RegisterSubsystemVersionsHandler();
            case DomainControllerProtocol.COMPLETE_HOST_CONTROLLER_REGISTRATION:
                // Complete the registration process
                return new CompleteRegistrationHandler();
        }
        return handlers.resolveNext();
    }

    /**
     * Wrapper to the DomainController and the underlying {@code ModelController} to execute
     * a {@code OperationStepHandler} implementation directly, bypassing normal domain coordination layer.
     * TODO This interface probably should be adapted to provide use-case-specific methods instead of generic
     * "execute whatever I hand you" ones. The "installSlaveExtensions" method needed to be non-generic unless
     * I was willing to hand a ref to the root MRR to RemoteDomainConnnectionService.
     */
    public interface OperationExecutor {

        /**
         * Execute the operation.
         *
         * @param operation operation
         * @param handler the message handler
         * @param control the transaction control
         * @param step the step to be executed
         * @return the result
         */
        ModelNode execute(Operation operation, OperationMessageHandler handler, ModelController.OperationTransactionControl control, OperationStepHandler step);

        /**
         * Execute the operation to install extensions provided by a remote domain controller.
         *
         *
         * @param extensions@return the result
         */
        ModelNode installSlaveExtensions(List<ModelNode> extensions);

        /**
         * Execute an operation using the current management model.
         *
         * @param operation    the operation
         * @param handler      the operation handler to use
         * @return the operation result
         */
        ModelNode executeReadOnly(ModelNode operation, OperationStepHandler handler, ModelController.OperationTransactionControl control);

        /**
         * Execute an operation using given resource model.
         *
         * @param operation    the operation
         * @param model        the resource model
         * @param handler      the operation handler to use
         * @return the operation result
         */
        ModelNode executeReadOnly(ModelNode operation, Resource model, OperationStepHandler handler, ModelController.OperationTransactionControl control);

        /**
         * Attempts to acquire a non-exclusive read lock. After any operations requiring this lock have completed, #releaseReadlock
         * must be called to release the lock.
         * @param operationID - the operationID for this registration. Cannot be {@code null}.
         * @throws IllegalArgumentException - if operationID is null.
         * @throws InterruptedException - if the lock is not acquired.
         */
        void acquireReadlock(final Integer operationID) throws IllegalArgumentException, InterruptedException;

        /**
         * Release the non-exclusive read lock obtained from #acquireReadlock. This method must be called after any locked
         * operations have completed or aborted to release the shared lock.
         * @param operationID - the operationID for this registration. Cannot be {@code null}.
         * @throws IllegalArgumentException - if if the operationId is null.
         * @throws IllegalStateException - if the shared lock was not held.
         */
        void releaseReadlock(final Integer operationID) throws IllegalArgumentException;
    }

    class InitiateRegistrationHandler implements ManagementRequestHandler<Void, RegistrationContext> {

        @Override
        public void handleRequest(final DataInput input, final ActiveOperation.ResultHandler<Void> resultHandler, final ManagementRequestContext<RegistrationContext> context) throws IOException {
            expectHeader(input, DomainControllerProtocol.PARAM_HOST_ID);
            final String hostName = input.readUTF();
            final ModelNode hostInfo = new ModelNode();
            hostInfo.readExternal(input);

            final RegistrationContext registration = context.getAttachment();
            registration.initialize(hostName, hostInfo, context);

            if (domainController.getCurrentRunningMode() == RunningMode.ADMIN_ONLY) {
                registration.failed(SlaveRegistrationException.ErrorCode.MASTER_IS_ADMIN_ONLY, DomainControllerLogger.ROOT_LOGGER.adminOnlyModeCannotAcceptSlaves(RunningMode.ADMIN_ONLY));
                return;
            }
            if (!domainController.getLocalHostInfo().isMasterDomainController()) {
                registration.failed(SlaveRegistrationException.ErrorCode.HOST_IS_NOT_MASTER, DomainControllerLogger.ROOT_LOGGER.slaveControllerCannotAcceptOtherSlaves());
                return;
            }

            // Read the domain model async, this will block until the registration process is complete
            context.executeAsync(new ManagementRequestContext.AsyncTask<RegistrationContext>() {
                @Override
                public void execute(ManagementRequestContext<RegistrationContext> context) throws Exception {
                    if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("interrupted");
                    registration.processRegistration();
                }
            }, registrationExecutor);
        }

    }

    class RegisterSubsystemVersionsHandler implements ManagementRequestHandler<Void, RegistrationContext> {

        @Override
        public void handleRequest(DataInput input, ActiveOperation.ResultHandler<Void> resultHandler, ManagementRequestContext<RegistrationContext> context) throws IOException {
            final byte status = input.readByte();
            final ModelNode subsystems = new ModelNode();
            subsystems.readExternal(input);

            final RegistrationContext registration = context.getAttachment();
            if(status == DomainControllerProtocol.PARAM_OK) {
                registration.setSubsystems(subsystems, context);
            } else {
                registration.setSubsystems(null, context);
            }
        }

    }

    /**
     * Handler responsible for completing the registration request.
     */
    static class CompleteRegistrationHandler implements ManagementRequestHandler<Void, RegistrationContext> {

        @Override
        public void handleRequest(final DataInput input, final ActiveOperation.ResultHandler<Void> resultHandler, final ManagementRequestContext<RegistrationContext> context) throws IOException {
            final byte status = input.readByte();
            final String message = input.readUTF(); // Perhaps use message when the host failed
            final RegistrationContext registration = context.getAttachment();
            registration.completeRegistration(context, status == DomainControllerProtocol.PARAM_OK);
        }

    }

    class HostRegistrationStepHandler implements OperationStepHandler {
        private final TransformerRegistry transformerRegistry;
        private final RegistrationContext registrationContext;

        protected HostRegistrationStepHandler(final TransformerRegistry transformerRegistry, final RegistrationContext registrationContext) {
            this.registrationContext = registrationContext;
            this.transformerRegistry = transformerRegistry;
        }

        @Override
        public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {

            assert registrationContext != null;
            assert registrationContext.activeOperation != null;

            // acquires the shared-mode read lock.
            boolean locked = false;
            Integer operationID = registrationContext.activeOperation.getOperationId();

            Assert.checkNotNullParam("operationID", operationID);

            try {
                try {
                    operationExecutor.acquireReadlock(operationID);
                } catch (IllegalArgumentException e) {
                    throw new OperationFailedException(e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw ControllerLogger.ROOT_LOGGER.operationCancelledAsynchronously();
                }
                locked = true;

                if (domainController.isHostRegistered(registrationContext.hostName)) {
                    final String failureDescription = DomainControllerLogger.ROOT_LOGGER.slaveAlreadyRegistered(registrationContext.hostName);
                    registrationContext.failed(SlaveRegistrationException.ErrorCode.HOST_ALREADY_EXISTS, failureDescription);
                    context.getFailureDescription().set(failureDescription);
                    return;
                }

                // Read the extensions (with recursive true, otherwise the entries are runtime=true - which are going to be ignored for transformation)
                final Resource root = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS.append(PathElement.pathElement(EXTENSION)), true);
                // Check the mgmt version
                final HostInfo hostInfo = registrationContext.hostInfo;
                final int major = hostInfo.getManagementMajorVersion();
                final int minor = hostInfo.getManagementMinorVersion();
                final int micro = hostInfo.getManagementMicroVersion();

                // We reject any remote host running behind WildFly 23 => KernelAPIVersion.VERSION_16_0(16, 0, 0)
                // We no longer support domains for legacy remote hosts below WildFly 23, so we reject the registration here.
                boolean rejected = major < 16;
                if (rejected) {
                    final OperationFailedException failure = HostControllerLogger.ROOT_LOGGER.unsupportedManagementVersionForHost(major, minor, 16, 0);
                    registrationContext.failed(failure, SlaveRegistrationException.ErrorCode.INCOMPATIBLE_VERSION, failure.getMessage());
                    throw failure;
                }
                // Ensure feature stream of host is compatible
                LocalHostControllerInfo domainInfo = domainController.getLocalHostInfo();
                if (domainInfo.getFeatureStream() != hostInfo.getFeatureStream()) {
                    OperationFailedException failure = HostControllerLogger.ROOT_LOGGER.incompatibleFeatureStream(domainInfo.getFeatureStream(), hostInfo.getFeatureStream());
                    registrationContext.failed(failure, SlaveRegistrationException.ErrorCode.INCOMPATIBLE_VERSION, failure.getMessage());
                    throw failure;
                }
                // Initialize the transformers
                final TransformationTarget target = TransformationTargetImpl.createForHost(hostInfo.getHostName(), transformerRegistry,
                        ModelVersion.create(major, minor, micro), Collections.<PathAddress, ModelVersion>emptyMap(), hostInfo);
                final Transformers transformers = Transformers.Factory.create(target);
                try {
                    SlaveChannelAttachments.attachSlaveInfo(handler.getChannel(), registrationContext.hostName, transformers, hostInfo.getDomainIgnoredExtensions());
                } catch (IOException e) {
                    throw new OperationFailedException(e.getLocalizedMessage());
                }
                // Build the extensions list
                final ModelNode extensions = new ModelNode();
                final Transformers.TransformationInputs transformationInputs = Transformers.TransformationInputs.getOrCreate(context);
                final Resource transformed = transformers.transformRootResource(transformationInputs, root);
                final Collection<Resource.ResourceEntry> resources = transformed.getChildren(EXTENSION);
                for (final Resource.ResourceEntry entry : resources) {
                    if (!hostInfo.isResourceTransformationIgnored(PathAddress.pathAddress(entry.getPathElement()))) {
                        extensions.add(entry.getName());
                    }
                }
                if (!extensions.isDefined()) {
                    // TODO add a real error message
                    throw new OperationFailedException(extensions.toString(), extensions);
                }
                // Remotely resolve the subsystem versions and create the transformation
                registrationContext.processSubsystems(transformers, extensions);
                // Now run the read-domain model operation
                final ReadMasterDomainModelHandler handler = new ReadMasterDomainModelHandler(hostInfo, transformers, domainController.getExtensionRegistry(), false);
                context.addStep(READ_DOMAIN_MODEL.getOperation(), handler, OperationContext.Stage.MODEL);

                context.completeStep(new OperationContext.ResultHandler() {
                    @Override
                    public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                        try {
                            operationExecutor.releaseReadlock(operationID);
                        } catch (IllegalArgumentException e) {
                            HostControllerLogger.ROOT_LOGGER.hostRegistrationCannotReleaseSharedLock(operationID);
                        }
                    }
                });
                locked = false;
            } finally {
                if (locked) {
                    operationExecutor.releaseReadlock(operationID);
                }
            }
        }
    }

    private class RegistrationContext implements ModelController.OperationTransactionControl, ActiveOperation.CompletedCallback<Void> {

        private final ExtensionRegistry extensionRegistry;
        private final boolean registerProxyController;
        private volatile String hostName;
        private volatile HostInfo hostInfo;
        private ManagementRequestContext<RegistrationContext> responseChannel;

        private volatile IOTask<?> task;
        private volatile boolean failed;
        private volatile Transformers transformers;
        private ActiveOperation<Void, RegistrationContext> activeOperation;
        private final AtomicBoolean completed = new AtomicBoolean();
        private final DomainHostExcludeRegistry domainHostExcludeRegistry;

        private RegistrationContext(ExtensionRegistry extensionRegistry,
                                    boolean registerProxyController,
                                    DomainHostExcludeRegistry domainHostExcludeRegistry) {
            this.extensionRegistry = extensionRegistry;
            this.registerProxyController = registerProxyController;
            this.domainHostExcludeRegistry = domainHostExcludeRegistry;
        }

        private synchronized void initialize(final String hostName, final ModelNode hostInfo, final ManagementRequestContext<RegistrationContext> responseChannel) {
            this.hostName = hostName;
            this.hostInfo = HostInfo.fromModelNode(hostInfo, domainHostExcludeRegistry);
            this.responseChannel = responseChannel;
        }

        @Override
        public void completed(Void result) {
            //
        }

        @Override
        public void failed(Exception e) {
            failed(e, SlaveRegistrationException.ErrorCode.UNKNOWN, e.getClass().getName() + ":" + e.getMessage());
        }

        @Override
        public void cancelled() {
            //
        }

        @Override
        public void operationPrepared(final ModelController.OperationTransaction transaction, final ModelNode result) {
            if(failed) {
                transaction.rollback();
            } else {
                try {
                    registerHost(transaction, result);
                } catch (SlaveRegistrationException e) {
                    failed(e, e.getErrorCode(), e.getErrorMessage());
                } catch (Exception e) {
                    failed(e, SlaveRegistrationException.ErrorCode.UNKNOWN, e.getClass().getName() + ":" + e.getMessage());
                }
                if(failed) {
                    transaction.rollback();
                }
            }
        }

        /**
         *  Process the registration of the slave whose information was provided to {@code initialize()}.
         */
        private void processRegistration() {

            // Check for duplicate registrations
            if (domainController.isHostRegistered(hostName)) {
                // asynchronously ping the existing host to validate it's still connected
                // If not, the ping will remove it and a subsequent attempt by the new host will succeed
                // TODO look into doing the ping synchronously
                domainController.pingRemoteHost(hostName);
                // Quick hack -- wait a bit to let async ping detect a re-registration. This can easily be improved
                // via the TODO above
                boolean inter = false; // TODO this is not used
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // TODO why not set inter = true and do this in finally?
                } finally {
                    // Now see if the existing registration has been removed
                    if (domainController.isHostRegistered(hostName)) {
                        failed(SlaveRegistrationException.ErrorCode.HOST_ALREADY_EXISTS, DomainControllerLogger.ROOT_LOGGER.slaveAlreadyRegistered(hostName));
                    }
                }
            }

            if (!failed) {
                try {
                    // The domain model is going to be sent as part of the prepared notification

                    final OperationStepHandler handler = new HostRegistrationStepHandler(extensionRegistry.getTransformerRegistry(), this);
                    ModelNode result = operationExecutor.execute(READ_DOMAIN_MODEL, OperationMessageHandler.logging, this, handler);

                    if (FAILED.equals(result.get(OUTCOME).asString())) {
                        failed(SlaveRegistrationException.ErrorCode.UNKNOWN, result.get(FAILURE_DESCRIPTION).asString());
                        return;
                    }
                } catch (Exception e) {
                    failed(e);
                    return;
                }
                // Send a registered notification back
                sendCompletedMessage();
                // Make sure that the host controller gets unregistered when the channel is closed
                responseChannel.getChannel().addCloseHandler(new CloseHandler<Channel>() {
                    @Override
                    public void handleClose(Channel closed, IOException exception) {
                        boolean cleanShutdown = ! domainController.isHostRegistered(hostName);
                        domainController.unregisterRemoteHost(hostName, getRemoteConnectionId(), cleanShutdown);
                    }
                });
            }
        }


        /**
         * Create the transformers. This will remotely resolve the subsystem versions.
         *
         * @param extensions the extensions
         * @throws OperationFailedException
         */
        private void processSubsystems(final Transformers transformers, final ModelNode extensions) throws OperationFailedException {
            this.transformers = transformers;
            final ModelNode subsystems = executeBlocking(new IOTask<ModelNode>() {
                @Override
                void sendMessage(FlushableDataOutput output) throws IOException {
                    sendResponse(output, DomainControllerProtocol.PARAM_OK, extensions);
                }
            });
            if(failed) {
                throw new OperationFailedException("failed to setup transformers");
            }
            final TransformationTarget target = transformers.getTarget();
            for(final Property subsystem : subsystems.asPropertyList()) {
                final String subsystemName = subsystem.getName();
                final ModelNode version = subsystem.getValue();
                target.addSubsystemVersion(subsystemName, ModelVersion.fromString(version.asString()));
            }
        }

        protected void setSubsystems(final ModelNode resolved, final ManagementRequestContext<RegistrationContext> responseChannel) {
            this.responseChannel = responseChannel;
            completeTask(resolved);
        }

        /**
         * Once the "read-domain-mode" operation is in operationPrepared, send the model back to registering HC.
         * When the model was applied successfully on the client, we process registering the proxy in the domain,
         * otherwise we rollback.
         *
         * @param transaction the model controller tx
         * @param result the prepared result (domain model)
         * @throws SlaveRegistrationException
         */
        void registerHost(final ModelController.OperationTransaction transaction, final ModelNode result) throws SlaveRegistrationException {
            //
            if (sendResultToHost(transaction, result)) return;
            synchronized (this) {
                Long pingPongId = hostInfo.getRemoteConnectionId();
                // Register the slave
                domainController.registerRemoteHost(hostName, handler, transformers, pingPongId, registerProxyController);
                // Complete registration
                if(! failed) {
                    transaction.commit();
                } else {
                    transaction.rollback();
                    return;
                }
            }
            if (registerProxyController) {
                DOMAIN_LOGGER.registeredRemoteSlaveHost(hostName, hostInfo.getPrettyProductName());
            }
        }

        private boolean sendResultToHost(ModelController.OperationTransaction transaction, final ModelNode result) {
            final boolean registered = executeBlocking(new IOTask<Boolean>() {
                @Override
                void sendMessage(final FlushableDataOutput output) throws IOException {
                    sendResponse(output, DomainControllerProtocol.PARAM_OK, result);
                }
            });
            if (!registered) {
                transaction.rollback();
                return true;
            }
            return false;
        }

        void completeRegistration(final ManagementRequestContext<RegistrationContext> responseChannel, boolean commit) {
            this.responseChannel = responseChannel;
            failed |= ! commit;
            completeTask(!failed);
        }

        /**
         * @param t  - the cause of failure, must not be null
         * @param error code representing the failure cause {@link SlaveRegistrationException.ErrorCode}
         * @param message - text explaining the failure
         */
        void failed(final Throwable t, SlaveRegistrationException.ErrorCode error, String message) {
            byte errorCode = error.getCode();
            if(completed.compareAndSet(false, true)) {
                failed = true;
                final IOTask<?> task = this.task;
                if(task != null) {
                    task.failed(t);
                }
                try {
                    sendFailedResponse(responseChannel, errorCode, message);
                } catch (IOException e) {
                    DOMAIN_LOGGER.debugf(e, "failed to process message");
                }
                activeOperation.getResultHandler().done(null);
                addFailureEvent(error);
            }
        }

        void failed(final SlaveRegistrationException.ErrorCode error, final String message) {
            Exception ex = new Exception(message);
            failed(ex, error, message);
        }

        void addFailureEvent(SlaveRegistrationException.ErrorCode error) {
            final HostConnectionInfo.EventType eventType;
            switch (error) {
                case HOST_ALREADY_EXISTS:
                    eventType = HostConnectionInfo.EventType.REGISTRATION_EXISTING;
                    break;
                case INCOMPATIBLE_VERSION:
                    eventType = HostConnectionInfo.EventType.REGISTRATION_REJECTED;
                    break;
                default:
                    eventType = HostConnectionInfo.EventType.REGISTRATION_FAILED;
            }
            slaveHostRegistrations.addHostEvent(hostName, HostConnectionInfo.Events.create(eventType, address));
        }

        void sendCompletedMessage() {
            if(completed.compareAndSet(false, true)) {
                try {
                    sendResponse(responseChannel, DomainControllerProtocol.PARAM_OK, null);
                } catch (IOException e) {
                    DOMAIN_LOGGER.debugf(e, "failed to process message");
                }
                activeOperation.getResultHandler().done(null);
            }
        }

        Long getRemoteConnectionId() {
            return hostInfo.getRemoteConnectionId();
        }

        protected boolean completeTask(Object result) {
            synchronized (this) {
                if(failed) {
                    return false;
                }
                if(task != null) {
                    return task.completeStep(result);
                }
            }
            return false;
        }

        /**
         * Execute a task and wait for the response.
         *
         * @param task the task to execute
         * @param <T> the response type
         * @return the result
         */
        protected <T> T executeBlocking(final IOTask<T> task) {
            synchronized (this) {
                this.task = task;
                try {
                    final ManagementResponseHeader header = ManagementResponseHeader.create(responseChannel.getRequestHeader());
                    final FlushableDataOutput output = responseChannel.writeMessage(header);
                    try {
                        task.sendMessage(output);
                    } catch (IOException e) {
                        failed(e, SlaveRegistrationException.ErrorCode.UNKNOWN, DomainControllerLogger.ROOT_LOGGER.failedToSendMessage(e.getMessage()));
                        throw new IllegalStateException(e);
                    } finally {
                        StreamUtils.safeClose(output);
                    }
                } catch (IOException e) {
                    failed(e, SlaveRegistrationException.ErrorCode.UNKNOWN, DomainControllerLogger.ROOT_LOGGER.failedToSendResponseHeader(e.getMessage()));
                    throw new IllegalStateException(e);
                }
            }
            try {
                return task.get();
            } catch (InterruptedException e) {
                failed(e, SlaveRegistrationException.ErrorCode.UNKNOWN, DomainControllerLogger.ROOT_LOGGER.registrationTaskGotInterrupted());
                throw new IllegalStateException(e);
            } catch (ExecutionException e) {
                failed(e, SlaveRegistrationException.ErrorCode.UNKNOWN, DomainControllerLogger.ROOT_LOGGER.registrationTaskFailed(e.getMessage()));
                throw new IllegalStateException(e);
            }
        }

    }

    abstract static class IOTask<T> extends AsyncFutureTask<T> {

        IOTask() {
            super(null);
        }

        abstract void sendMessage(final FlushableDataOutput output) throws IOException;

        @SuppressWarnings("unchecked")
        boolean completeStep(Object result) {
            return setResult((T) result);
        }

        /**
         * @param t – the cause of failure, must not be null.
         * @return true if the result was successfully set, or false if a result was already set
         */
        boolean failed(Throwable t) {
            Assert.checkNotNullParam("Throwable", t);
            return super.setFailed(t);
        }
    }

    /**
     * Send an operation response.
     *
     * @param context the request context
     * @param responseType the response type
     * @param response the operation response
     * @throws IOException for any error
     */
    static void sendResponse(final ManagementRequestContext<RegistrationContext> context, final byte responseType, final ModelNode response) throws IOException {
        final ManagementResponseHeader header = ManagementResponseHeader.create(context.getRequestHeader());
        final FlushableDataOutput output = context.writeMessage(header);
        try {
            sendResponse(output, responseType, response);
        } finally {
            StreamUtils.safeClose(output);
        }
    }

    static void sendResponse(final FlushableDataOutput output, final byte responseType, final ModelNode response) throws IOException {
        // response type
        output.writeByte(responseType);
        if(response != null) {
            // operation result
            response.writeExternal(output);
        }
        // response end
        output.writeByte(ManagementProtocol.RESPONSE_END);
        output.close();
    }

    /**
     * Send a failed operation response.
     *
     * @param context the request context
     * @param errorCode the error code
     * @param message the operation message
     * @throws IOException for any error
     */
    static void sendFailedResponse(final ManagementRequestContext<RegistrationContext> context, final byte errorCode, final String message) throws IOException {
        final ManagementResponseHeader header = ManagementResponseHeader.create(context.getRequestHeader());
        final FlushableDataOutput output = context.writeMessage(header);
        try {
            // This is an error
            output.writeByte(DomainControllerProtocol.PARAM_ERROR);
            // send error code
            output.writeByte(errorCode);
            // error message
            if (message == null) {
                output.writeUTF("unknown error");
            } else {
                output.writeUTF(message);
            }
            // response end
            output.writeByte(ManagementProtocol.RESPONSE_END);
            output.close();
        } finally {
            StreamUtils.safeClose(output);
        }
    }

}
