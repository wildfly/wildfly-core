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

package org.jboss.as.host.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.host.controller.logging.HostControllerLogger.ROOT_LOGGER;

import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;

import org.jboss.as.controller.BlockingTimeout;
import org.jboss.as.controller.CurrentOperationIdHolder;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.remote.BlockingQueueOperationListener;
import org.jboss.as.controller.remote.TransactionalProtocolClient;
import org.jboss.as.controller.transform.TransformationTarget;
import org.jboss.as.controller.transform.TransformationTargetImpl;
import org.jboss.as.controller.transform.TransformerRegistry;
import org.jboss.as.domain.controller.DomainController;
import org.jboss.as.domain.management.security.DomainManagedServerCallbackHandler;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.process.ProcessController;
import org.jboss.as.process.ProcessControllerClient;
import org.jboss.as.process.ProcessInfo;
import org.jboss.as.process.ProcessMessageHandler;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.as.version.Version;
import org.jboss.dmr.ModelNode;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.threads.AsyncFuture;
import org.wildfly.security.auth.callback.CredentialCallback;
import org.wildfly.security.auth.callback.EvidenceVerifyCallback;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.evidence.PasswordGuessEvidence;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.interfaces.DigestPassword;
import org.wildfly.security.password.spec.DigestPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.EncryptablePasswordSpec;

/**
 * Inventory of the managed servers.
 *
 * @author Emanuel Muckenhuber
 * @author Kabir Khan
 */
public class ServerInventoryImpl implements ServerInventory {

    /** The managed servers. */
    private final ConcurrentMap<String, ManagedServer> servers = new ConcurrentHashMap<String, ManagedServer>();

    private final HostControllerEnvironment environment;
    private final ProcessControllerClient processControllerClient;
    private final URI managementURI;
    private final DomainController domainController;
    private final ExtensionRegistry extensionRegistry;

    private volatile boolean shutdown;
    private volatile boolean connectionFinished;

    //
    private volatile CountDownLatch processInventoryLatch;
    private volatile Map<String, ProcessInfo> processInfos;

    private final Object shutdownCondition = new Object();

    ServerInventoryImpl(final DomainController domainController, final HostControllerEnvironment environment, final URI managementURI,
                        final ProcessControllerClient processControllerClient, final ExtensionRegistry extensionRegistry) {
        this.domainController = domainController;
        this.environment = environment;
        this.managementURI = managementURI;
        this.processControllerClient = processControllerClient;
        this.extensionRegistry = extensionRegistry;
    }

    @Override
    public String getServerProcessName(String serverName) {
        return ManagedServer.getServerProcessName(serverName);
    }

    @Override
    public String getProcessServerName(String processName) {
        return ManagedServer.getServerName(processName);
    }

    @Override
    public synchronized Map<String, ProcessInfo> determineRunningProcesses() {
        processInventoryLatch = new CountDownLatch(1);
        try {
            processControllerClient.requestProcessInventory();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            if (!processInventoryLatch.await(30, TimeUnit.SECONDS)){
                throw HostControllerLogger.ROOT_LOGGER.couldNotGetServerInventory(30L, TimeUnit.SECONDS.toString().toLowerCase(Locale.US));
            }
        } catch (InterruptedException e) {
            throw HostControllerLogger.ROOT_LOGGER.couldNotGetServerInventory(30L, TimeUnit.SECONDS.toString().toLowerCase(Locale.US));
        }
        return processInfos;
    }

    @Override
    public Map<String, ProcessInfo> determineRunningProcesses(final boolean serversOnly) {
        final Map<String, ProcessInfo> processInfos = determineRunningProcesses();
        if (!serversOnly) {
            return processInfos;
        }
        final Map<String, ProcessInfo> processes = new HashMap<String, ProcessInfo>();
        for (Map.Entry<String, ProcessInfo> procEntry : processInfos.entrySet()) {
            if (ManagedServer.isServerProcess(procEntry.getKey())) {
                processes.put(procEntry.getKey(), procEntry.getValue());
            }
        }
        return processes;
    }

    @Override
    public ServerStatus determineServerStatus(final String serverName) {
        final ManagedServer server = servers.get(serverName);
        if(server == null) {
            return ServerStatus.STOPPED;
        }
        return server.getState();
    }

    @Override
    public ServerStatus startServer(final String serverName, final ModelNode domainModel) {
        return startServer(serverName, domainModel, false, false);
    }

    @Override
    public ServerStatus startServer(final String serverName, final ModelNode domainModel, final boolean blocking, boolean suspend) {
        if(shutdown || connectionFinished) {
            throw HostControllerLogger.ROOT_LOGGER.hostAlreadyShutdown();
        }
        ManagedServer server = servers.get(serverName);
        if (server != null && server.getState() == ServerStatus.FAILED) {
            //If the server failed stop it to
            //  refresh the parameters passed in to the new process
            //  read it in the process controller
            HostControllerLogger.ROOT_LOGGER.failedToStartServer(null, serverName);
            //The gracefulTimeout value does not seem to be used in this case, but initialise it to 1s just in case it gets added.
            //In practise the server has already stopped so the time should be less than this
            stopServer(serverName, 1000, true);
            server = null;
        }
        if(server == null) {
            // Create a new authKey
            final byte[] authBytes = new byte[ProcessController.AUTH_BYTES_LENGTH];
            new Random(new SecureRandom().nextLong()).nextBytes(authBytes);
            String authKey = Base64.getEncoder().encodeToString(authBytes);
            // Create the managed server
            final ManagedServer newServer = createManagedServer(serverName, authKey);
            server = servers.putIfAbsent(serverName, newServer);
            if(server == null) {
                server = newServer;
            }
        }
        // Start the server
        server.start(createBootFactory(serverName, domainModel, suspend));
        synchronized (shutdownCondition) {
            shutdownCondition.notifyAll();
        }
        if(blocking) {
            // Block until the server started message
            server.awaitState(ManagedServer.InternalState.SERVER_STARTED);
        } else {
            // Wait until the server opens the mgmt connection
            server.awaitState(ManagedServer.InternalState.SERVER_STARTING);
        }
        return server.getState();
    }

    @Override
    public ServerStatus restartServer(final String serverName, final int gracefulTimeout, final ModelNode domainModel) {
        return restartServer(serverName, gracefulTimeout, domainModel, false, false);
    }

    @Override
    public ServerStatus restartServer(final String serverName, final int gracefulTimeout, final ModelNode domainModel, final boolean blocking, final boolean suspend) {
        stopServer(serverName, gracefulTimeout);
        synchronized (shutdownCondition) {
            for(;;) {
                if(shutdown || connectionFinished) {
                    throw HostControllerLogger.ROOT_LOGGER.hostAlreadyShutdown();
                }
                if(! servers.containsKey(serverName)) {
                    break;
                }
                try {
                    shutdownCondition.wait();
                } catch (InterruptedException e){
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        startServer(serverName, domainModel, blocking, suspend);
        return determineServerStatus(serverName);
    }

    @Override
    public ServerStatus stopServer(final String serverName, final int gracefulTimeout) {
        return stopServer(serverName, gracefulTimeout, false);
    }

    @Override
    public ServerStatus stopServer(final String serverName, final int gracefulTimeout, final boolean blocking) {
        final ManagedServer server = servers.get(serverName);
        if(server == null) {
            return ServerStatus.STOPPED;
        }
        Integer currentOperationID = CurrentOperationIdHolder.getCurrentOperationID();
        server.stop(currentOperationID == null ? null : gracefulTimeout);
        if(blocking) {
            server.awaitState(ManagedServer.InternalState.STOPPED);
        }
        return server.getState();
    }

    @Override
    public void reconnectServer(final String serverName, final ModelNode domainModel, final String authKey, final boolean running, final boolean stopping) {
        if(shutdown || connectionFinished) {
            throw HostControllerLogger.ROOT_LOGGER.hostAlreadyShutdown();
        }
        ManagedServer existing = servers.get(serverName);
        if(existing != null) {
            ROOT_LOGGER.existingServerWithState(serverName, existing.getState());
            return;
        }
        final ManagedServer server = createManagedServer(serverName, authKey);
        if ((existing = servers.putIfAbsent(serverName, server)) != null) {
            ROOT_LOGGER.existingServerWithState(serverName, existing.getState());
            return;
        }
        if(running) {
            if(!stopping) {
                 server.reconnectServerProcess(createBootFactory(serverName, domainModel, false));
            } else {
                 server.setServerProcessStopping();
            }
        } else {
            server.removeServerProcess();
        }
        synchronized (shutdownCondition) {
            shutdownCondition.notifyAll();
        }
    }

    @Override
    public ServerStatus reloadServer(final String serverName, final boolean blocking, boolean suspend) {
        if (shutdown || connectionFinished) {
            throw HostControllerLogger.ROOT_LOGGER.hostAlreadyShutdown();
        }
        final ManagedServer server = servers.get(serverName);
        if (server == null) {
            return ServerStatus.STOPPED;
        }
        if (server.reload(CurrentOperationIdHolder.getCurrentOperationID(), suspend)) {
            // Reload with current permit
            if (blocking) {
                server.awaitState(ManagedServer.InternalState.SERVER_STARTED);
            } else {
                server.awaitState(ManagedServer.InternalState.SERVER_STARTING);
            }
        }
        return determineServerStatus(serverName);
    }

    @Override
    public void destroyServer(String serverName) {
        final ManagedServer server = servers.get(serverName);
        if(server == null) {
            return;
        }
        server.destroy();
    }

    @Override
    public void killServer(String serverName) {
        final ManagedServer server = servers.get(serverName);
        if(server == null) {
            return;
        }
        server.kill();
    }

    @Override
    public void stopServers(final int gracefulTimeout) {
        stopServers(gracefulTimeout, false);
    }

    @Override
    public void stopServers(final int gracefulTimeout, final boolean blockUntilStopped) {
        for(final ManagedServer server : servers.values()) {
            Integer currentOperationID = CurrentOperationIdHolder.getCurrentOperationID();
            server.stop(currentOperationID == null ? null : gracefulTimeout);
        }
        if(blockUntilStopped) {
            synchronized (shutdownCondition) {
                for(;;) {
                    if(connectionFinished) {
                        break;
                    }
                    int count = 0;
                    for(final ManagedServer server : servers.values()) {
                        final ServerStatus state = server.getState();
                        switch (state) {
                            case DISABLED:
                            case FAILED:
                            case STOPPED:
                                break;
                            default:
                                count++;
                        }
                    }
                    if(count == 0) {
                        break;
                    }
                    try {
                        shutdownCondition.wait();
                    } catch(InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    @Override
    // Hmm, maybe have startServer return some sort of Future, so the caller can decide to wait
    public void awaitServersState(final Collection<String> serverNames, final boolean started) {
        for (final String serverName : serverNames) {
            final ManagedServer server = servers.get(serverName);
            if(server == null) {
                continue;
            }
            server.awaitState(started ? ManagedServer.InternalState.SERVER_STARTED : ManagedServer.InternalState.STOPPED);
        }
    }


    @Override
    public List<ModelNode> suspendServers(Set<String> serverNames, BlockingTimeout blockingTimeout) {
        return suspendServers(serverNames, 0, blockingTimeout);
    }

    @Override
    public List<ModelNode> resumeServers(Set<String> serverNames, BlockingTimeout blockingTimeout) {
        List<ModelNode> errorResults = new ArrayList<>();

        Map<String, OperationData> operationDataMap = new HashMap<>();
        for (String serverName : serverNames) {
            final ManagedServer server = servers.get(serverName);
            if (server != null) {
                try {
                    int blockingTimeoutValue = blockingTimeout.getProxyBlockingTimeout(server.getAddress(), server.getProxyController());
                    BlockingQueueOperationListener<TransactionalProtocolClient.Operation> listener = new  BlockingQueueOperationListener<>();
                    AsyncFuture<OperationResponse> future = server.resume(listener);

                    operationDataMap.put(serverName, this.new OperationData(blockingTimeoutValue, future, listener));

                } catch (IOException e) {
                    HostControllerLogger.ROOT_LOGGER.resumeExecutionFailed(e, serverName);
                    errorResults.add( new ModelNode(
                            HostControllerLogger.ROOT_LOGGER.resumeExecutionFailedMsg(serverName)
                    ));
                }
            }
        }

        for (Map.Entry<String, OperationData> operationDataEntry : operationDataMap.entrySet()) {
            final OperationData operationData = operationDataEntry.getValue();
            final String serverName = operationDataEntry.getKey();
            final int timeout = operationData.blockingTimeout;
            final AsyncFuture<OperationResponse> future = operationData.future;
            final BlockingQueueOperationListener<TransactionalProtocolClient.Operation> listener = operationData.listener;

            try {
                final TransactionalProtocolClient.PreparedOperation<?> prepared =
                        listener.retrievePreparedOperation(timeout, TimeUnit.MILLISECONDS);
                if (prepared == null){
                    HostControllerLogger.ROOT_LOGGER.timedOutAwaitingResumeResponse(timeout, serverName);
                    errorResults.add( new ModelNode(
                            HostControllerLogger.ROOT_LOGGER.timedOutAwaitingResumeResponseMsg(timeout, serverName)
                    ));
                    future.asyncCancel(true);
                    continue;
                }
                if (prepared.isFailed()) {
                    errorResults.add(appendServerNameToFailureResponse(serverName, prepared.getPreparedResult()));
                    continue;
                }
                prepared.commit();
                prepared.getFinalResult().get(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                HostControllerLogger.ROOT_LOGGER.interruptedAwaitingResumeResponse(e, serverName);
                errorResults.add( new ModelNode(
                        HostControllerLogger.ROOT_LOGGER.interruptedAwaitingResumeResponseMsg(serverName)
                ));
                future.asyncCancel(true);
                Thread.currentThread().interrupt();
            } catch (TimeoutException e) {
                HostControllerLogger.ROOT_LOGGER.timedOutAwaitingResumeResponse(timeout, serverName);
                errorResults.add( new ModelNode(
                        HostControllerLogger.ROOT_LOGGER.timedOutAwaitingResumeResponseMsg(timeout, serverName)
                ));
                future.asyncCancel(true);
            } catch (ExecutionException e) {
                HostControllerLogger.ROOT_LOGGER.resumeListenerFailed(e, serverName);
                errorResults.add( new ModelNode(
                        HostControllerLogger.ROOT_LOGGER.resumeListenerFailedMsg(serverName)
                ));
                future.asyncCancel(true);
            }
        }

        return errorResults;
    }

    @Override
    public List<ModelNode> suspendServers(Set<String> serverNames, int timeoutInSeconds, BlockingTimeout blockingTimeout) {
        List<ModelNode> errorResults = new ArrayList<>();

        Map<String, OperationData> operationDataMap = new HashMap<>();
        for (String serverName : serverNames) {
            final ManagedServer server = servers.get(serverName);
            if (server != null) {
                try {
                    int blockingTimeoutValue = blockingTimeout.getProxyBlockingTimeout(server.getAddress(), server.getProxyController());
                    BlockingQueueOperationListener<TransactionalProtocolClient.Operation> listener = new  BlockingQueueOperationListener<>();
                    AsyncFuture<OperationResponse> future = server.suspend(timeoutInSeconds, listener);

                    operationDataMap.put(serverName, this.new OperationData(blockingTimeoutValue, future, listener));

                } catch (IOException e) {
                    HostControllerLogger.ROOT_LOGGER.suspendExecutionFailed(e, serverName);
                    errorResults.add(
                            new ModelNode(HostControllerLogger.ROOT_LOGGER.suspendExecutionFailedMsg(serverName)
                    ));
                }
            }
        }

        for (Map.Entry<String, OperationData> operationDataEntry : operationDataMap.entrySet()) {
            final OperationData operationData = operationDataEntry.getValue();
            final String serverName = operationDataEntry.getKey();
            final int timeout = operationData.blockingTimeout;
            final AsyncFuture<OperationResponse> future = operationData.future;
            final BlockingQueueOperationListener<TransactionalProtocolClient.Operation> listener = operationData.listener;

            try {
                final TransactionalProtocolClient.PreparedOperation<?> prepared =
                        listener.retrievePreparedOperation(timeout, TimeUnit.MILLISECONDS);
                if (prepared == null){
                    HostControllerLogger.ROOT_LOGGER.timedOutAwaitingSuspendResponse(timeout, serverName);
                    errorResults.add( new ModelNode(
                            HostControllerLogger.ROOT_LOGGER.timedOutAwaitingSuspendResponseMsg(timeout, serverName)
                    ));
                    future.asyncCancel(true);
                    continue;
                }
                if (prepared.isFailed()) {
                    errorResults.add(appendServerNameToFailureResponse(serverName, prepared.getPreparedResult()));
                    continue;
                }
                prepared.commit();
                prepared.getFinalResult().get(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                HostControllerLogger.ROOT_LOGGER.interruptedAwaitingSuspendResponse(e, serverName);
                errorResults.add( new ModelNode(
                        HostControllerLogger.ROOT_LOGGER.interruptedAwaitingSuspendResponseMsg(serverName)
                ));
                future.asyncCancel(true);
                Thread.currentThread().interrupt();
            } catch (TimeoutException e) {
                HostControllerLogger.ROOT_LOGGER.timedOutAwaitingSuspendResponse(timeout, serverName);
                errorResults.add( new ModelNode(
                        HostControllerLogger.ROOT_LOGGER.timedOutAwaitingSuspendResponseMsg(timeout, serverName)
                ));
                future.asyncCancel(true);
            } catch (ExecutionException e) {
                HostControllerLogger.ROOT_LOGGER.suspendListenerFailed(e, serverName);
                errorResults.add( new ModelNode(
                        HostControllerLogger.ROOT_LOGGER.suspendListenerFailedMsg(serverName)
                ));
                future.asyncCancel(true);
            }
        }

        return errorResults;
    }

    void shutdown(final boolean shutdownServers, final int gracefulTimeout, final boolean blockUntilStopped) {
        final boolean shutdown = this.shutdown;
        this.shutdown = true;
        if(! shutdown) {
            if(connectionFinished) {
                // In case the connection to the ProcessController is closed we won't be able to shutdown the servers from here
                // nor can expect to receive any further notifications notifications.
                return;
            }
            if(shutdownServers) {
                // Shutdown the servers as well
                stopServers(gracefulTimeout, blockUntilStopped);
            }
        }
    }

    @Override
    public ProxyController serverCommunicationRegistered(final String serverProcessName, final ManagementChannelHandler channelAssociation) {
        if(shutdown || connectionFinished) {
            throw HostControllerLogger.ROOT_LOGGER.hostAlreadyShutdown();
        }
        final String serverName = ManagedServer.getServerName(serverProcessName);
        final ManagedServer server = servers.get(serverName);
        if(server == null) {
            ROOT_LOGGER.noServerAvailable(serverName);
            return null;
        }
        try {
            final TransactionalProtocolClient client = server.channelRegistered(channelAssociation);
            final Channel channel = channelAssociation.getChannel();
            channel.addCloseHandler(new CloseHandler<Channel>() {

                public void handleClose(final Channel closed, final IOException exception) {
                    final boolean shuttingDown = shutdown || connectionFinished;
                    // Unregister right away
                    if(server.callbackUnregistered(client, shuttingDown)) {
                        domainController.unregisterRunningServer(server.getServerName());
                    }
                }
            });
            return server.getProxyController();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean serverReconnected(String serverProcessName, ManagementChannelHandler channelHandler) {
        final String serverName = ManagedServer.getServerName(serverProcessName);
        final ManagedServer server = servers.get(serverName);
        // Register the new communication channel
        serverCommunicationRegistered(serverProcessName, channelHandler);
        // Mark the server as started
        serverStarted(serverProcessName);
        // Register the server proxy at the domain controller
        domainController.registerRunningServer(server.getProxyController());
        // If the server requires a reload, means we are out of sync
        return server.isRequiresReload() == false;
    }

    @Override
    public void serverProcessStopped(final String serverProcessName) {
        final String serverName = ManagedServer.getServerName(serverProcessName);
        final ManagedServer server = servers.get(serverName);
        if(server == null) {
            ROOT_LOGGER.noServerAvailable(serverName);
            return;
        }
        // always un-register in case the process exits
        domainController.unregisterRunningServer(server.getServerName());
        server.processFinished();
        synchronized (shutdownCondition) {
            shutdownCondition.notifyAll();
        }
    }

    @Override
    public void connectionFinished() {
        this.connectionFinished = true;
        ROOT_LOGGER.debug("process controller connection closed.");
        synchronized (shutdownCondition) {
            shutdownCondition.notifyAll();
        }
    }

    @Override
    public void serverStarted(String serverProcessName) {
        final String serverName = ManagedServer.getServerName(serverProcessName);
        final ManagedServer server = servers.get(serverName);
        if(server == null) {
            ROOT_LOGGER.noServerAvailable(serverName);
            return;
        }
        server.serverStarted(null);
        synchronized (shutdownCondition) {
            shutdownCondition.notifyAll();
        }
    }

    @Override
    public void serverStartFailed(final String serverProcessName) {
        final String serverName = ManagedServer.getServerName(serverProcessName);
        final ManagedServer server = servers.get(serverName);
        if(server == null) {
            ROOT_LOGGER.noServerAvailable(serverName);
            return;
        }
        server.serverStartFailed();
        synchronized (shutdownCondition) {
            shutdownCondition.notifyAll();
        }
    }

    @Override
    public void serverProcessAdded(final String serverProcessName) {
        final String serverName = ManagedServer.getServerName(serverProcessName);
        final ManagedServer server = servers.get(serverName);
        if(server == null) {
            ROOT_LOGGER.noServerAvailable(serverName);
            return;
        }
        server.processAdded();
        synchronized (shutdownCondition) {
            shutdownCondition.notifyAll();
        }
    }

    @Override
    public void serverProcessStarted(final String serverProcessName) {
        final String serverName = ManagedServer.getServerName(serverProcessName);
        final ManagedServer server = servers.get(serverName);
        if(server == null) {
            ROOT_LOGGER.noServerAvailable(serverName);
            return;
        }
        server.processStarted();
        synchronized (shutdownCondition) {
            shutdownCondition.notifyAll();
        }
    }

    @Override
    public void serverUnstable(final String serverProcessName) {
        final String serverName = ManagedServer.getServerName(serverProcessName);
        final ManagedServer server = servers.get(serverName);
        boolean change = true;
        if (server != null) {
            change = server.processUnstable();
        }
        if (change) {
            // Pass the news on to the DC
            domainController.reportServerInstability(serverName);
        }
    }

    @Override
    public void serverProcessRemoved(final String serverProcessName) {
        final String serverName = ManagedServer.getServerName(serverProcessName);
        final ManagedServer server = servers.remove(serverName);
        if(server == null) {
            ROOT_LOGGER.noServerAvailable(serverName);
            return;
        }
        server.processRemoved();
        synchronized (shutdownCondition) {
            shutdownCondition.notifyAll();
        }
    }

    @Override
    public void operationFailed(final String serverProcessName, final ProcessMessageHandler.OperationType type) {
        final String serverName = ManagedServer.getServerName(serverProcessName);
        final ManagedServer server = servers.get(serverName);
        if(server == null) {
            ROOT_LOGGER.noServerAvailable(serverName);
            return;
        }
        switch (type) {
            case ADD:
                server.transitionFailed(ManagedServer.InternalState.PROCESS_ADDING);
                break;
            case START:
                server.transitionFailed(ManagedServer.InternalState.PROCESS_STARTING);
                break;
            case STOP:
                server.transitionFailed(ManagedServer.InternalState.PROCESS_STOPPING);
                break;
            case SEND_STDIN:
            case RECONNECT:
                server.transitionFailed(ManagedServer.InternalState.SERVER_STARTING);
                break;
            case REMOVE:
                server.transitionFailed(ManagedServer.InternalState.PROCESS_REMOVING);
                break;
        }
    }

    @Override
    public void processInventory(final Map<String, ProcessInfo> processInfos) {
        this.processInfos = processInfos;
        if (processInventoryLatch != null){
            processInventoryLatch.countDown();
        }
    }

    private ManagedServer createManagedServer(final String serverName, final String authKey) {
        final String hostControllerName = domainController.getLocalHostInfo().getLocalHostName();
        // final ManagedServerBootConfiguration configuration = combiner.createConfiguration();
        final Map<PathAddress, ModelVersion> subsystems = TransformerRegistry.resolveVersions(extensionRegistry);
        final ModelVersion modelVersion = ModelVersion.create(Version.MANAGEMENT_MAJOR_VERSION, Version.MANAGEMENT_MINOR_VERSION, Version.MANAGEMENT_MICRO_VERSION);
        //We don't need any transformation between host and server
        final TransformationTarget target = TransformationTargetImpl.create(hostControllerName, extensionRegistry.getTransformerRegistry(),
                modelVersion, subsystems, TransformationTarget.TransformationTargetType.SERVER);
        return new ManagedServer(hostControllerName, serverName, authKey, processControllerClient, managementURI, target);
    }

    private ManagedServerBootCmdFactory createBootFactory(final String serverName, final ModelNode domainModel, boolean suspend) {
        final String hostControllerName = domainController.getLocalHostInfo().getLocalHostName();
        final ModelNode hostModel = domainModel.require(HOST).require(hostControllerName);
        return new ManagedServerBootCmdFactory(serverName, domainModel, hostModel, environment, domainController.getExpressionResolver(), suspend);
    }

    @Override
    public CallbackHandler getServerCallbackHandler() {
        return new CallbackHandler() {
            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                List<Callback> toRespondTo = new LinkedList<Callback>();

                String userName = null;
                String realm = null;
                ManagedServer server = null;

                // A single pass may be sufficient but by using a two pass approach the Callbackhandler will not
                // fail if an unexpected order is encountered.

                // First Pass - is to double check no unsupported callbacks and to retrieve
                // information from the callbacks passing in information.
                for (Callback current : callbacks) {

                    if (current instanceof AuthorizeCallback) {
                        toRespondTo.add(current);
                    } else if (current instanceof NameCallback) {
                        NameCallback nameCallback = (NameCallback) current;
                        userName = nameCallback.getDefaultName();
                        if (userName.startsWith(DomainManagedServerCallbackHandler.DOMAIN_SERVER_AUTH_PREFIX)) {
                            server = servers.get(userName.substring(DomainManagedServerCallbackHandler.DOMAIN_SERVER_AUTH_PREFIX.length()));
                        }
                    } else if (current instanceof PasswordCallback) {
                        toRespondTo.add(current);
                    } else if (current instanceof EvidenceVerifyCallback) {
                        toRespondTo.add(current);
                    } else if (current instanceof CredentialCallback) {
                        toRespondTo.add(current);
                    } else if (current instanceof RealmCallback) {
                        realm = ((RealmCallback)current).getDefaultText();
                    } else {
                        throw new UnsupportedCallbackException(current);
                    }
                }

                /*
                * At the moment this is a special CallbackHandler where we know the setting of a password will be double checked
                 * before going back to the base realm.
                */
                if (server == null) {
                    return;
                }

                // Second Pass - Now iterate the Callback(s) requiring a response.
                for (Callback current : toRespondTo) {
                    if (current instanceof AuthorizeCallback) {
                        AuthorizeCallback authorizeCallback = (AuthorizeCallback) current;
                        // Don't support impersonating another identity
                        authorizeCallback.setAuthorized(authorizeCallback.getAuthenticationID().equals(authorizeCallback.getAuthorizationID()));
                    } else if (current instanceof PasswordCallback) {
                        ((PasswordCallback) current).setPassword(server.getAuthKey().toCharArray());
                    } else if (current instanceof EvidenceVerifyCallback) {
                        EvidenceVerifyCallback vpc = (EvidenceVerifyCallback) current;
                        vpc.setVerified(server.getAuthKey().equals(vpc.applyToEvidence(PasswordGuessEvidence.class, e -> new String(e.getGuess()))));
                    } else if (current instanceof CredentialCallback) {
                        CredentialCallback dhc = (CredentialCallback) current;
                        try {
                            if (realm == null) {
                                throw HostControllerLogger.ROOT_LOGGER.insufficientInformationToGenerateHash();
                            }
                            final PasswordFactory instance = PasswordFactory.getInstance(DigestPassword.ALGORITHM_DIGEST_MD5);
                            final Password password = instance.generatePassword(new EncryptablePasswordSpec(server.getAuthKey().toCharArray(), new DigestPasswordAlgorithmSpec(userName, realm)));
                            dhc.setCredential(new PasswordCredential(password));
                        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                            throw HostControllerLogger.ROOT_LOGGER.unableToGenerateHash(e);
                        }
                    }
                }

            }
        };
    }

    private ModelNode appendServerNameToFailureResponse(String serverName, ModelNode failureResponse) {
        String currentDescription = failureResponse.get(FAILURE_DESCRIPTION).asString();
        return new ModelNode(String.format("%s server: %s", currentDescription, serverName));
    }

    private class OperationData {
        int blockingTimeout;
        AsyncFuture<OperationResponse> future;
        BlockingQueueOperationListener<TransactionalProtocolClient.Operation> listener;

        public OperationData(int blockingTimeout, AsyncFuture<OperationResponse> future, BlockingQueueOperationListener<TransactionalProtocolClient.Operation> listener) {
            this.blockingTimeout = blockingTimeout;
            this.future = future;
            this.listener = listener;
        }
    }

}
