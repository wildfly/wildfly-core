/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.host.controller.mgmt;

import static org.jboss.as.process.protocol.ProtocolUtils.expectHeader;
import static org.xnio.IoUtils.safeClose;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;

import org.jboss.as.controller.HashUtil;
import org.jboss.as.domain.controller.DomainController;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.protocol.mgmt.ActiveOperation;
import org.jboss.as.protocol.mgmt.FlushableDataOutput;
import org.jboss.as.protocol.mgmt.ManagementProtocol;
import org.jboss.as.protocol.mgmt.ManagementRequestContext;
import org.jboss.as.protocol.mgmt.ManagementRequestHandler;
import org.jboss.as.protocol.mgmt.ManagementRequestHandlerFactory;
import org.jboss.as.protocol.mgmt.ManagementRequestHeader;
import org.jboss.as.protocol.mgmt.ManagementResponseHeader;
import org.jboss.as.protocol.mgmt.RequestProcessingException;
import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.HostFileRepository;
import org.jboss.as.repository.RemoteFileRequestAndHandler.RootFileReader;

/**
 * Handles for requests from slave DC to master DC on the 'domain' channel.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class MasterDomainControllerOperationHandlerImpl implements ManagementRequestHandlerFactory {

    private final DomainController domainController;
    private final Executor asyncExecutor;

    public MasterDomainControllerOperationHandlerImpl(final DomainController domainController, final Executor asyncExecutor) {
        this.domainController = domainController;
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    public ManagementRequestHandler<?, ?> resolveHandler(RequestHandlerChain handlers, ManagementRequestHeader header) {
        final byte operationId = header.getOperationId();
        switch (operationId) {
            case DomainControllerProtocol.UNREGISTER_HOST_CONTROLLER_REQUEST: {
                handlers.registerActiveOperation(header.getBatchId(), null);
                return new UnregisterOperation();
            } case DomainControllerProtocol.GET_FILE_REQUEST: {
                handlers.registerActiveOperation(header.getBatchId(), null);
                return new GetFileOperation();
            } case DomainControllerProtocol.SERVER_INSTABILITY_REQUEST: {
                handlers.registerActiveOperation(header.getBatchId(), null);
                return new ServerUnstableHandler();
            }
        }
        return handlers.resolveNext();
    }

    private class UnregisterOperation extends AbstractHostRequestHandler {

        @Override
        void handleRequest(String hostId, DataInput input, ActiveOperation.ResultHandler<Void> resultHandler, ManagementRequestContext<Void> context) throws IOException {
            domainController.unregisterRemoteHost(hostId, null, true);
            FlushableDataOutput os = writeGenericResponseHeader(context);
            try {
                os.write(ManagementProtocol.RESPONSE_END);
                os.close();
                os = null; //avoid double close
                resultHandler.done(null); // call stack (AbstractMessageHandler) handles failures
            } finally {
                safeClose(os);
            }
        }

    }

    private class GetFileOperation extends AbstractHostRequestHandler {

        private final DomainRemoteFileRequestAndHandler remoteSupport = new DomainRemoteFileRequestAndHandler(asyncExecutor);

        @Override
        void handleRequest(String hostId, DataInput input, ActiveOperation.ResultHandler<Void> resultHandler, ManagementRequestContext<Void> context) throws IOException {
            DomainControllerLogger.ROOT_LOGGER.tracef("Handling GetFileOperation with id %d from %s", context.getOperationId(), hostId);
            final RootFileReader reader = new RootFileReader() {
                public File readRootFile(byte rootId, String filePath) throws RequestProcessingException {
                    final HostFileRepository localFileRepository = domainController.getLocalFileRepository();

                    switch (rootId) {
                        case DomainControllerProtocol.PARAM_ROOT_ID_FILE: {
                            return localFileRepository.getFile(filePath);
                        }
                        case DomainControllerProtocol.PARAM_ROOT_ID_CONFIGURATION: {
                            return localFileRepository.getConfigurationFile(filePath);
                        }
                        case DomainControllerProtocol.PARAM_ROOT_ID_DEPLOYMENT: {
                            byte[] hash = HashUtil.hexStringToByteArray(filePath);
                            return localFileRepository.getDeploymentRoot(new ContentReference(filePath, hash));
                        }
                        default: {
                            throw HostControllerLogger.ROOT_LOGGER.invalidRootId(rootId);
                        }
                    }
                }
            };

            remoteSupport.handleRequest(input, reader, resultHandler, context);
        }
    }

    abstract static class AbstractHostRequestHandler implements ManagementRequestHandler<Void, Void> {

        abstract void handleRequest(final String hostId, DataInput input, ActiveOperation.ResultHandler<Void> resultHandler, ManagementRequestContext<Void> context) throws IOException;

        @Override
        public void handleRequest(DataInput input, ActiveOperation.ResultHandler<Void> resultHandler, ManagementRequestContext<Void> context) throws IOException {
            expectHeader(input, DomainControllerProtocol.PARAM_HOST_ID);
            final String hostId = input.readUTF();
            handleRequest(hostId, input, resultHandler, context);
        }

        protected FlushableDataOutput writeGenericResponseHeader(final ManagementRequestContext<Void> context) throws IOException {
            final ManagementResponseHeader header = ManagementResponseHeader.create(context.getRequestHeader());
            return context.writeMessage(header);
        }

    }

    /**
     * Handler responsible for handling a server instability notification.
     */
    private class ServerUnstableHandler implements ManagementRequestHandler<Void, Void> {

        private ServerUnstableHandler() {
        }

        @Override
        public void handleRequest(final DataInput input, final ActiveOperation.ResultHandler<Void> resultHandler, final ManagementRequestContext<Void> context) throws IOException {
            expectHeader(input, DomainControllerProtocol.PARAM_SERVER_ID);
            final String serverName = input.readUTF();
            expectHeader(input, DomainControllerProtocol.PARAM_HOST_ID);
            final String hostName = input.readUTF();
            context.executeAsync(new ManagementRequestContext.AsyncTask<Void>() {
                @Override
                public void execute(ManagementRequestContext<Void> context) throws Exception {
                    try {
                        HostControllerLogger.ROOT_LOGGER.managedServerUnstable(serverName, hostName);
                    } finally {
                        resultHandler.done(null);
                    }
                }
            });
        }
    }

}
