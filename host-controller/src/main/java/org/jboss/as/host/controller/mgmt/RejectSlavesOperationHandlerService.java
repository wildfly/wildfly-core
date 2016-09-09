/*
Copyright 2016 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.host.controller.mgmt;

import static org.jboss.as.host.controller.logging.HostControllerLogger.DOMAIN_LOGGER;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;

import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.remote.AbstractModelControllerOperationHandlerFactoryService;
import org.jboss.as.domain.controller.SlaveRegistrationException;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.protocol.mgmt.ActiveOperation;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.as.protocol.mgmt.ManagementClientChannelStrategy;
import org.jboss.as.protocol.mgmt.ManagementRequestContext;
import org.jboss.as.protocol.mgmt.ManagementRequestHandler;
import org.jboss.as.protocol.mgmt.ManagementRequestHandlerFactory;
import org.jboss.as.protocol.mgmt.ManagementRequestHeader;
import org.jboss.as.protocol.mgmt.support.ManagementChannelShutdownHandle;
import org.jboss.remoting3.Channel;

/**
 * {@link org.jboss.as.protocol.mgmt.support.ManagementChannelInitialization} that installs a handler
 * that rejects slave registration requests.
 *
 * @author Brian Stansberry
 */
public class RejectSlavesOperationHandlerService extends AbstractModelControllerOperationHandlerFactoryService
        implements ManagementRequestHandlerFactory, ManagementRequestHandler<Void, Void>, ActiveOperation.CompletedCallback<Void> {

    private final boolean isMaster;
    private final File tempDir;

    public RejectSlavesOperationHandlerService(boolean isMaster, File tempDir) {
        this.isMaster = isMaster;
        this.tempDir = tempDir;
    }

    @Override
    public ManagementChannelShutdownHandle startReceiving(Channel channel) {
        final ManagementChannelHandler handler = new ManagementChannelHandler(ManagementClientChannelStrategy.create(channel), getExecutor());
        handler.getAttachments().attach(ManagementChannelHandler.TEMP_DIR, tempDir);
        // Assemble the request handlers for the domain channel
        handler.addHandlerFactory(this);
        channel.receiveMessage(handler.getReceiver());
        return handler;
    }

    @Override
    public ManagementRequestHandler<?, ?> resolveHandler(RequestHandlerChain handlers, ManagementRequestHeader header) {
        final byte operationId = header.getOperationId();
        switch (operationId) {
            case DomainControllerProtocol.REGISTER_HOST_CONTROLLER_REQUEST:
            case DomainControllerProtocol.FETCH_DOMAIN_CONFIGURATION_REQUEST: {
                handlers.registerActiveOperation(header.getBatchId(), this, this);
                return this;
            }
        }
        return handlers.resolveNext();
    }

    @Override
    public void handleRequest(final DataInput input, final ActiveOperation.ResultHandler<Void> resultHandler, final ManagementRequestContext<Void> context) throws IOException {

        SlaveRegistrationException.ErrorCode errorCode = isMaster
                ? SlaveRegistrationException.ErrorCode.MASTER_IS_ADMIN_ONLY
                : SlaveRegistrationException.ErrorCode.HOST_IS_NOT_MASTER;
        String message = isMaster
                ? DomainControllerLogger.ROOT_LOGGER.adminOnlyModeCannotAcceptSlaves(RunningMode.ADMIN_ONLY)
                : DomainControllerLogger.ROOT_LOGGER.slaveControllerCannotAcceptOtherSlaves();

        try {
            HostControllerRegistrationHandler.sendFailedResponse(context, errorCode.getCode(), message);
        } catch (IOException e) {
            DOMAIN_LOGGER.debugf(e, "failed to process message");
        }
        resultHandler.done(null);
    }

    @Override
    public void completed(Void result) {
        //
    }

    @Override
    public void failed(Exception e) {

        //
    }

    @Override
    public void cancelled() {
        //
    }
}
