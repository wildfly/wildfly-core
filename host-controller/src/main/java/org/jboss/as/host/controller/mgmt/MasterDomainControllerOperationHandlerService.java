/*
* JBoss, Home of Professional Open Source.
* Copyright 2006, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.host.controller.mgmt;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.CurrentOperationIdHolder;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.remote.AbstractModelControllerOperationHandlerFactoryService;
import org.jboss.as.controller.remote.ModelControllerClientOperationHandler;
import org.jboss.as.controller.remote.ModelControllerClientOperationHandlerFactoryService;
import org.jboss.as.controller.remote.ResponseAttachmentInputStreamSupport;
import org.jboss.as.controller.remote.TransactionalProtocolOperationHandler;
import org.jboss.as.domain.controller.DomainController;
import org.jboss.as.domain.controller.HostRegistrations;
import org.jboss.as.domain.controller.operations.FetchMissingConfigurationHandler;
import org.jboss.as.domain.controller.operations.coordination.DomainControllerLockIdUtils;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.protocol.mgmt.ManagementChannelAssociation;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.as.protocol.mgmt.ManagementClientChannelStrategy;
import org.jboss.as.protocol.mgmt.ManagementPongRequestHandler;
import org.jboss.as.protocol.mgmt.ManagementRequestContext;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.remoting3.Channel;

/**
 * Installs {@link MasterDomainControllerOperationHandlerImpl} which handles requests from slave DC to master DC.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class MasterDomainControllerOperationHandlerService extends AbstractModelControllerOperationHandlerFactoryService {

    public static final ServiceName SERVICE_NAME = DomainController.SERVICE_NAME.append(ModelControllerClientOperationHandlerFactoryService.OPERATION_HANDLER_NAME_SUFFIX);

    private final DomainController domainController;
    private final HostControllerRegistrationHandler.OperationExecutor operationExecutor;
    private final TransactionalOperationExecutor txOperationExecutor;
    private final ManagementPongRequestHandler pongRequestHandler = new ManagementPongRequestHandler();
    private final File tempDir;
    private final HostRegistrations slaveHostRegistrations;
    private final DomainHostExcludeRegistry domainHostExcludeRegistry;

    public MasterDomainControllerOperationHandlerService(
            final Consumer<AbstractModelControllerOperationHandlerFactoryService> serviceConsumer,
            final Supplier<ModelController> modelControllerSupplier,
            final Supplier<ExecutorService> executorSupplier,
            final Supplier<ScheduledExecutorService> scheduledExecutorSupplier,
            final DomainController domainController, final HostControllerRegistrationHandler.OperationExecutor operationExecutor,
            final TransactionalOperationExecutor txOperationExecutor,
            final File tempDir, final HostRegistrations slaveHostRegistrations, DomainHostExcludeRegistry domainHostExcludeRegistry) {
        super(serviceConsumer, modelControllerSupplier, executorSupplier, scheduledExecutorSupplier);
        this.domainController = domainController;
        this.operationExecutor = operationExecutor;
        this.txOperationExecutor = txOperationExecutor;
        this.tempDir = tempDir;
        this.slaveHostRegistrations = slaveHostRegistrations;
        this.domainHostExcludeRegistry = domainHostExcludeRegistry;
    }

    @Override
    public synchronized void start(StartContext context) throws StartException {
        pongRequestHandler.resetConnectionId();
        super.start(context);
    }

    @Override
    public ManagementChannelHandler startReceiving(final Channel channel) {
        final ManagementChannelHandler handler = new ManagementChannelHandler(ManagementClientChannelStrategy.create(channel), getExecutor());
        handler.getAttachments().attach(ManagementChannelHandler.TEMP_DIR, tempDir);
        // Assemble the request handlers for the domain channel
        handler.addHandlerFactory(new HostControllerRegistrationHandler(handler, domainController, operationExecutor,
                getExecutor(), slaveHostRegistrations, domainHostExcludeRegistry));
        handler.addHandlerFactory(new ModelControllerClientOperationHandler(getController(), handler, getResponseAttachmentSupport(), getClientRequestExecutor()));
        handler.addHandlerFactory(new MasterDomainControllerOperationHandlerImpl(domainController, getExecutor()));
        handler.addHandlerFactory(pongRequestHandler);
        handler.addHandlerFactory(new DomainTransactionalProtocolOperationHandler(txOperationExecutor, handler, getResponseAttachmentSupport()));
        channel.receiveMessage(handler.getReceiver());
        return handler;
    }

    private class DomainTransactionalProtocolOperationHandler extends TransactionalProtocolOperationHandler {
        private final TransactionalOperationExecutor executor;

        private volatile SlaveRequest activeSlaveRequest;

        public DomainTransactionalProtocolOperationHandler(TransactionalOperationExecutor executor, ManagementChannelAssociation channelAssociation,
                                                           ResponseAttachmentInputStreamSupport responseAttachmentSupport) {
            super(null, channelAssociation, responseAttachmentSupport);
            this.executor = executor;
        }

        @Override
        protected OperationResponse internalExecute(final Operation operation, final ManagementRequestContext<?> context, final OperationMessageHandler messageHandler, final ModelController.OperationTransactionControl control) {

            final ModelNode operationNode = operation.getOperation();
            final OperationStepHandler handler;
            final String operationName = operation.getOperation().require(OP).asString();
            if (operationName.equals(FetchMissingConfigurationHandler.OPERATION_NAME)) {
                handler = new FetchMissingConfigurationHandler(SlaveChannelAttachments.getHostName(context.getChannel()),
                        SlaveChannelAttachments.getTransformers(context.getChannel()),
                        domainController.getExtensionRegistry());
            } else {
                throw HostControllerLogger.ROOT_LOGGER.cannotExecuteTransactionalOperationFromSlave(operationName);
            }

            Integer domainControllerLockId;
            if (operationNode.get(OPERATION_HEADERS).hasDefined(DomainControllerLockIdUtils.DOMAIN_CONTROLLER_LOCK_ID)) {
                domainControllerLockId = operationNode.get(OPERATION_HEADERS, DomainControllerLockIdUtils.DOMAIN_CONTROLLER_LOCK_ID).asInt();
            } else {
                domainControllerLockId = null;
            }

            if (domainControllerLockId == null) {
                synchronized (this) {
                    SlaveRequest slaveRequest = this.activeSlaveRequest;
                    if (slaveRequest != null) {
                        domainControllerLockId = slaveRequest.domainId;
                        slaveRequest.refCount.incrementAndGet();
                    }
                }
            }

            try {
                if (domainControllerLockId != null) {
                    assert operation.getInputStreams().size() == 0; // we don't support associating streams with an active op
                    ModelNode responseNode = executor.joinActiveOperation(operation.getOperation(), messageHandler, control, handler, domainControllerLockId);
                    return OperationResponse.Factory.createSimple(responseNode);
                } else {
                    return executor.executeAndAttemptLock(operation, messageHandler, control, new OperationStepHandler() {
                        @Override
                        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                            //Grab the lock id and store it
                            Integer domainControllerLockId = CurrentOperationIdHolder.getCurrentOperationID();
                            synchronized (this) {
                                activeSlaveRequest = new SlaveRequest(domainControllerLockId);
                            }
                            context.addStep(operation, handler, OperationContext.Stage.MODEL);
                        }
                    });
                }
            } finally {
                synchronized (this) {
                    SlaveRequest slaveRequest = this.activeSlaveRequest;
                    if (slaveRequest != null) {
                        int refcount = slaveRequest.refCount.decrementAndGet();
                        if (refcount == 0) {
                            activeSlaveRequest = null;
                        }
                    }
                }
            }
        }
    }

    public interface TransactionalOperationExecutor {
        OperationResponse executeAndAttemptLock(Operation operation, OperationMessageHandler handler, ModelController.OperationTransactionControl control, OperationStepHandler step);

        ModelNode joinActiveOperation(ModelNode operation, OperationMessageHandler handler, ModelController.OperationTransactionControl control, OperationStepHandler step, int permit);
    }

    private final class SlaveRequest {
        private final int domainId;
        private final AtomicInteger refCount = new AtomicInteger(1);

        SlaveRequest(int domainId){
            this.domainId = domainId;
        }
    }
}
