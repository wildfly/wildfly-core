/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.mgmt.domain;

import static java.security.AccessController.doPrivileged;

import java.io.Closeable;
import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import org.jboss.as.controller.AbstractControllerService;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.access.InVmAccess;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.protocol.mgmt.AbstractManagementRequest;
import org.jboss.as.protocol.mgmt.ActiveOperation;
import org.jboss.as.protocol.mgmt.FlushableDataOutput;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.as.protocol.mgmt.ManagementRequestContext;
import org.jboss.as.repository.RemoteFileRequestAndHandler;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.operations.ServerProcessStateHandler;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Client used to interact with the local host controller.
 *
 * @author Emanuel Muckenhuber
 */
public class HostControllerClient implements AbstractControllerService.ControllerInstabilityListener, Closeable {

    private final String serverName;
    private final HostControllerConnection connection;
    private final ManagementChannelHandler channelHandler;
    private final RemoteFileRepositoryExecutorImpl repositoryExecutor;
    private volatile ModelController controller;
    private final boolean managementSubsystemEndpoint;
    private final ExecutorService executorService;
    private final Runnable unstableNotificationRunnable;

    HostControllerClient(final String serverName, final ManagementChannelHandler channelHandler, final HostControllerConnection connection,
                         final boolean managementSubsystemEndpoint, final ExecutorService executorService) {
        this.serverName = serverName;
        this.connection = connection;
        this.channelHandler = channelHandler;
        this.repositoryExecutor = new RemoteFileRepositoryExecutorImpl();
        this.managementSubsystemEndpoint = managementSubsystemEndpoint;
        this.executorService = executorService;
        // Create and cache the objects that will send any controller instability requests
        // in order to increase the potential that it will execute in low memory situations
        final ControllerInstabilityNotificationRequest request = new ControllerInstabilityNotificationRequest();
        this.unstableNotificationRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    channelHandler.executeRequest(request, null);
                } catch (Throwable t) {
                    // not much we can do. Likely an OOME
                }
            }
        };
    }

    /**
     * Get the server name.
     *
     * @return the server name
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Resolve the boot updates and register at the local HC.
     *
     * @param controller the model controller
     * @param callback the completed callback
     * @throws Exception for any error
     */
    void resolveBootUpdates(final ModelController controller, final ActiveOperation.CompletedCallback<ModelNode> callback) throws Exception {
        connection.openConnection(controller, callback);
        // Keep a reference to the the controller
        this.controller = controller;
    }

    public void reconnect(final URI uri, final AuthenticationContext authenticationContext, final boolean mgmtSubsystemEndpoint) throws IOException, URISyntaxException {
        // In case the server is out of sync after the reconnect, set reload required
        final boolean mgmtEndpointChanged = this.managementSubsystemEndpoint != mgmtSubsystemEndpoint;
        connection.asyncReconnect(uri, authenticationContext, new HostControllerConnection.ReconnectCallback() {

            @Override
            public void reconnected(boolean inSync) {
                if (!inSync || mgmtEndpointChanged) {
                    Function<ModelController, ModelNode> function = new Function<ModelController, ModelNode>() {
                        @Override
                        public ModelNode apply(ModelController modelController) {
                            return InVmAccess.runInVm((PrivilegedAction<ModelNode>) () -> executeRequireReload(modelController));
                        }
                    };

                    privilegedExecution().execute(function, controller);
                }
            }

        });
    }

    private static ModelNode executeRequireReload(ModelController controller) {
        final ModelNode operation = new ModelNode();
        operation.get(ModelDescriptionConstants.OP).set(ServerProcessStateHandler.REQUIRE_RELOAD_OPERATION);
        operation.get(ModelDescriptionConstants.OP_ADDR).setEmptyList();
        return controller.execute(operation, OperationMessageHandler.DISCARD, ModelController.OperationTransactionControl.COMMIT, OperationAttachments.EMPTY);
    }

    /**
     * Get the remote file repository.
     *
     * @return the remote file repository
     */
    RemoteFileRepositoryExecutor getRemoteFileRepository() {
        return repositoryExecutor;
    }

    @Override
    public void close() throws IOException {
        if(connection != null) {
            connection.close();
        }
    }

    /**
     * Sends the instability notification on to the managing HostController.
     * {@inheritDoc}
     */
    @Override
    public void controllerUnstable() {
        try {
            executorService.submit(unstableNotificationRunnable);
        } catch (Throwable t) {
            // See if we can run it directly
            unstableNotificationRunnable.run();
        }
    }

    private static class GetFileRequest extends AbstractManagementRequest<File, Void> {
        private final String hash;
        private final File localDeploymentFolder;

        private GetFileRequest(final String hash, final File localDeploymentFolder) {
            this.hash = hash;
            this.localDeploymentFolder = localDeploymentFolder;
        }

        @Override
        public byte getOperationType() {
            return DomainServerProtocol.GET_FILE_REQUEST;
        }

        @Override
        protected void sendRequest(ActiveOperation.ResultHandler<File> resultHandler, ManagementRequestContext<Void> context, FlushableDataOutput output) throws IOException {
            //The root id does not matter here
            ServerToHostRemoteFileRequestAndHandler.INSTANCE.sendRequest(output, (byte)0, hash);
        }

        @Override
        public void handleRequest(DataInput input, ActiveOperation.ResultHandler<File> resultHandler, ManagementRequestContext<Void> context) throws IOException {
            try {
                File first = new File(localDeploymentFolder, hash.substring(0,2));
                File localPath = new File(first, hash.substring(2));
                ServerToHostRemoteFileRequestAndHandler.INSTANCE.handleResponse(input, localPath, ServerLogger.ROOT_LOGGER, resultHandler, context);
                resultHandler.done(null);
            } catch (RemoteFileRequestAndHandler.CannotCreateLocalDirectoryException e) {
                resultHandler.failed(ServerLogger.ROOT_LOGGER.cannotCreateLocalDirectory(e.getDir()));
            } catch (RemoteFileRequestAndHandler.DidNotReadEntireFileException e) {
                resultHandler.failed(ServerLogger.ROOT_LOGGER.didNotReadEntireFile(e.getMissing()));
            }
        }
    }

    private class RemoteFileRepositoryExecutorImpl implements RemoteFileRepositoryExecutor {
        public File getFile(final String relativePath, final byte repoId, final File localDeploymentFolder) {
            try {
                return channelHandler.executeRequest(new GetFileRequest(relativePath, localDeploymentFolder), null).getResult().get();
            } catch (Exception e) {
                throw ServerLogger.ROOT_LOGGER.failedToGetFileFromRemoteRepository(e);
            }
        }
    }

    private static class ControllerInstabilityNotificationRequest extends AbstractManagementRequest<Void, Void> {

        private ControllerInstabilityNotificationRequest() {
        }

        @Override
        public byte getOperationType() {
            return DomainServerProtocol.SERVER_INSTABILITY_REQUEST;
        }

        @Override
        protected void sendRequest(ActiveOperation.ResultHandler<Void> resultHandler, ManagementRequestContext<Void> context, FlushableDataOutput output) throws IOException {
            // nothing to add
        }

        @Override
        public void handleRequest(DataInput input, ActiveOperation.ResultHandler<Void> resultHandler, ManagementRequestContext<Void> context) throws IOException {
            resultHandler.done(null);
        }
    }

    /** Provides function execution in a doPrivileged block if a security manager is checking privileges */
    private static Execution privilegedExecution() {
        return WildFlySecurityManager.isChecking() ? Execution.PRIVILEGED : Execution.NON_PRIVILEGED;
    }

    /** Executes a function */
    private interface Execution {
        <T, R> R execute(Function<T, R> function, T t);

        Execution NON_PRIVILEGED = new Execution() {
            @Override
            public <T, R> R execute(Function<T, R> function, T t) {
                return function.apply(t);
            }
        };

        Execution PRIVILEGED = new Execution() {
            @Override
            public <T, R> R execute(Function<T, R> function, T t) {
                try {
                    return doPrivileged((PrivilegedExceptionAction<R>) () -> NON_PRIVILEGED.execute(function, t) );
                } catch (PrivilegedActionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    } else if (cause instanceof Error) {
                        throw (Error) cause;
                    } else {
                        // Not possible as Function doesn't throw any checked exception
                        throw new RuntimeException(cause);
                    }
                }
            }
        };

    }

}
