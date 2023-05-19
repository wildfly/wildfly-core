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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESUME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SHUTDOWN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.START_MODE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND_TIMEOUT;
import static org.jboss.as.host.controller.logging.HostControllerLogger.ROOT_LOGGER;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.jboss.as.controller.CurrentOperationIdHolder;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProxyOperationAddressTranslator;
import org.jboss.as.controller.TransformingProxyController;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.controller.remote.BlockingQueueOperationListener;
import org.jboss.as.controller.remote.TransactionalProtocolClient;
import org.jboss.as.controller.remote.TransactionalProtocolHandlers;
import org.jboss.as.controller.transform.TransformationTarget;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.process.ProcessControllerClient;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.as.server.DomainServerCommunicationServices;
import org.jboss.as.server.ServerStartTask;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.SimpleClassResolver;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.threads.AsyncFuture;

/**
 * Represents a managed server.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 * @author Brian Stansberry
 * @author Emanuel Muckenhuber
 */
class ManagedServer {

    private static final Logger.Level DEBUG_LEVEL = Logger.Level.TRACE;
    private static final MarshallerFactory MARSHALLER_FACTORY;
    private static final MarshallingConfiguration CONFIG;

    static {
        MARSHALLER_FACTORY = Marshalling.getMarshallerFactory("river", Marshalling.class.getClassLoader());

        final ClassLoader cl = ManagedServer.class.getClassLoader();
        final MarshallingConfiguration config = new MarshallingConfiguration();
        config.setVersion(2);
        config.setClassResolver(new SimpleClassResolver(cl));
        CONFIG = config;
    }

    /**
     * Prefix applied to a server's name to create it's process name.
     */
    private static final String SERVER_PROCESS_NAME_PREFIX = "Server:";

    static String getServerProcessName(String serverName) {
        return SERVER_PROCESS_NAME_PREFIX + serverName;
    }

    private static final String SERVER_PROCESS_ID_PREFIX = "pcid:";

    static String getServerProcessId(int serverId) {
        return SERVER_PROCESS_ID_PREFIX + serverId;
    }

    static boolean isServerProcess(String serverProcessName) {
        return serverProcessName.startsWith(SERVER_PROCESS_NAME_PREFIX);
    }

    static String getServerName(String serverProcessName) {
        return serverProcessName.substring(SERVER_PROCESS_NAME_PREFIX.length());
    }

    /*
     * Token to be used for the server to authenticate back over the management interface.
     *
     * This is independent of the pcKey used by processes to loop back to the process controller.
     */
    private final String serverAuthToken;
    private final String serverName;
    private final String serverProcessName;
    private final String hostControllerName;

    private final URI managementURI;
    private final ProcessControllerClient processControllerClient;

    private final ManagedServerProxy protocolClient;
    private final TransformingProxyController proxyController;

    private volatile boolean requiresReload;

    private volatile InternalState requiredState = InternalState.STOPPED;
    private volatile InternalState internalState = InternalState.STOPPED;

    private volatile int operationID = CurrentOperationIdHolder.getCurrentOperationID();
    private volatile ManagedServerBootConfiguration bootConfiguration;
    private volatile boolean unstable;

    private final PathAddress address;

    ManagedServer(final String hostControllerName, final String serverName, final String serverAuthToken,
                  final ProcessControllerClient processControllerClient, final URI managementURI,
                  final TransformationTarget transformationTarget) {

        assert hostControllerName  != null : "hostControllerName is null";
        assert serverName  != null : "serverName is null";
        assert processControllerClient != null : "processControllerSlave is null";
        assert managementURI != null : "managementURI is null";

        this.hostControllerName = hostControllerName;
        this.serverName = serverName;
        this.serverProcessName = getServerProcessName(serverName);
        this.processControllerClient = processControllerClient;
        this.managementURI = managementURI;

        this.serverAuthToken = serverAuthToken;

        // Setup the proxy controller
        final PathElement serverPath = PathElement.pathElement(RUNNING_SERVER, serverName);
        address = PathAddress.EMPTY_ADDRESS.append(PathElement.pathElement(HOST, hostControllerName), serverPath);
        this.protocolClient = new ManagedServerProxy(this);
        this.proxyController = TransformingProxyController.Factory.create(protocolClient,
                Transformers.Factory.create(transformationTarget), address, ProxyOperationAddressTranslator.SERVER);
    }

    /**
     * Get the process auth key.
     *
     * @return the auth key
     */
    String getAuthToken() {
        return serverAuthToken;
    }

    /**
     * Get the server name.
     *
     * @return the server name
     */
    String getServerName() {
        return serverName;
    }

    /**
     * Get the transforming proxy controller instance.
     *
     * @return the proxy controller
     */
    TransformingProxyController getProxyController() {
        return proxyController;
    }

    /**
     * Determine the current state the server is in.
     *
     * @return the server status
     */
    ServerStatus getState() {
        final InternalState requiredState = this.requiredState;
        final InternalState state = internalState;
        if(requiredState == InternalState.FAILED) {
            return ServerStatus.FAILED;
        }
        switch (state) {
            case STOPPED:
                return ServerStatus.STOPPED;
            case SERVER_STARTED:
                return  ServerStatus.STARTED;
            default: {
                if(requiredState == InternalState.SERVER_STARTED) {
                    return ServerStatus.STARTING;
                } else {
                    return ServerStatus.STOPPING;
                }
            }
        }
    }

    boolean isRequiresReload() {
        return requiresReload;
    }

    /**
     * Require a reload on the the next reconnect.
     */
    void requireReload() {
        requiresReload = true;
    }

    /**
     * Reload a managed server.
     *
     * @param permit the controller permit
     * @return whether the state was changed successfully or not
     */
    synchronized boolean reload(int permit, boolean suspend) {
        return internalSetState(new ReloadTask(permit, suspend), InternalState.SERVER_STARTED, InternalState.RELOADING);
    }

    /**
     * Start a managed server.
     *
     * @param factory the boot command factory
     */
    synchronized void start(final ManagedServerBootCmdFactory factory) {
        final InternalState required = this.requiredState;
        // Ignore if the server is already started
        if(required == InternalState.SERVER_STARTED) {
            return;
        }
        // In case the server failed to start, try to start it again
        if(required != InternalState.FAILED) {
            final InternalState current = this.internalState;
            if(current != required) {
                // TODO this perhaps should wait?
                throw new IllegalStateException();
            }
        }
        operationID = CurrentOperationIdHolder.getCurrentOperationID();
        bootConfiguration = factory.createConfiguration();
        requiredState = InternalState.SERVER_STARTED;
        ROOT_LOGGER.startingServer(serverName);
        transition();
    }

    /**
     * Stop a managed server.
     */
    synchronized void stop(Integer timeout) {
        final InternalState required = this.requiredState;
        if(required != InternalState.STOPPED) {
            this.requiredState = InternalState.STOPPED;
            ROOT_LOGGER.stoppingServer(serverName);
            // Only send the stop operation if the server is started
            if (internalState == InternalState.SERVER_STARTED) {
                internalSetState(new ServerStopTask(timeout), internalState, InternalState.PROCESS_STOPPING);
            } else {
                transition(false);
            }
        }
    }

    synchronized void destroy() {
        InternalState required = this.requiredState;
        if(required == InternalState.STOPPED) {
            if(internalState != InternalState.STOPPED) {
                try {
                    processControllerClient.destroyProcess(serverProcessName);
                } catch (IOException e) {
                    ROOT_LOGGER.logf(DEBUG_LEVEL, e, "failed to send destroy_process message to %s", serverName);
                }
            }
        } else {
            // Do the normal stop stuff first
            stop(null);
            required = this.requiredState;
            if (required == InternalState.STOPPED) {
                // Now proceed to destroy, assuming that there was a reason the user
                // invoked this op, and that stop() alone will not suffice
                // The PC will give it a bit of time to do a normal stop before destroying
                destroy();
            } // else something is odd but avoid looping. User would have to invoke the op again
        }
    }

    synchronized void kill() {
        InternalState required = this.requiredState;
        if(required == InternalState.STOPPED) {
            if(internalState != InternalState.STOPPED) {
                try {
                    processControllerClient.killProcess(serverProcessName);
                } catch (IOException e) {
                    ROOT_LOGGER.logf(DEBUG_LEVEL, e, "failed to send kill_process message to %s", serverName);
                }
            }
        } else {
            // Do the normal stop stuff first
            stop(null);
            required = this.requiredState;
            if (required == InternalState.STOPPED) {
                // Now proceed to kill, assuming that there was a reason the user
                // invoked this op, and that stop() alone will not suffice
                // The PC will give it a bit of time to do a normal stop before killing
                kill();
            } // else something is odd but avoid looping. User would have to invoke the op again
        }
    }

    /**
     * Try to reconnect to a started server.
     */
    synchronized void reconnectServerProcess(final ManagedServerBootCmdFactory factory) {
        if(this.requiredState != InternalState.SERVER_STARTED) {
            this.bootConfiguration = factory;
            this.requiredState = InternalState.SERVER_STARTED;
            ROOT_LOGGER.reconnectingServer(serverName);
            internalSetState(new ReconnectTask(), InternalState.STOPPED, InternalState.SEND_STDIN);
        }
    }

    /**
     * On host controller reload, remove a not running server registered in the process controller declared as down.
     */
    synchronized void removeServerProcess() {
        this.requiredState = InternalState.STOPPED;
        internalSetState(new ProcessRemoveTask(), InternalState.STOPPED, InternalState.PROCESS_REMOVING);
    }

    /**
     * On host controller reload, remove a not running server registered in the process controller declared as stopping.
     */
    synchronized void setServerProcessStopping() {
        this.requiredState = InternalState.STOPPED;
        internalSetState(null, InternalState.STOPPED, InternalState.PROCESS_STOPPING);
    }

    /**
     * Await a state.
     *
     * @param expected the expected state
     * @return {@code true} if the state was reached, {@code false} otherwise
     */
    boolean awaitState(final InternalState expected) {
        synchronized (this) {
            final InternalState initialRequired = this.requiredState;
            for(;;) {
                final InternalState required = this.requiredState;
                // Stop in case the server failed to reach the state
                if(required == InternalState.FAILED) {
                    return false;
                // Stop in case the required state changed
                } else if (initialRequired != required) {
                    return false;
                }
                final InternalState current = this.internalState;
                if(expected == current) {
                    return true;
                }
                try {
                    wait();
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
    }

    /**
     * Notification that the process was added
     */
    void processAdded() {
        finishTransition(InternalState.PROCESS_ADDING, InternalState.PROCESS_ADDED);
    }

    /**
     * Notification that the process was started.
     */
    void processStarted() {
        finishTransition(InternalState.PROCESS_STARTING, InternalState.PROCESS_STARTED);
    }

    /**
     * Notification that the process has become unstable.
     *
     * @return {@code true} if this is a change in status
     */
    boolean processUnstable() {
        boolean change = !unstable;
        if (change) {  // Only once until the process is removed. A process is unstable until removed.
            unstable = true;
            HostControllerLogger.ROOT_LOGGER.managedServerUnstable(serverName);
        }
        return change;
    }

    synchronized TransactionalProtocolClient channelRegistered(final ManagementChannelHandler channelAssociation) {
        final InternalState current = this.internalState;
        // Create the remote controller client
        channelAssociation.getAttachments().attach(TransactionalProtocolClient.SEND_IDENTITY, Boolean.TRUE);
        channelAssociation.getAttachments().attach(TransactionalProtocolClient.SEND_IN_VM, Boolean.TRUE);
        final TransactionalProtocolClient remoteClient = TransactionalProtocolHandlers.createClient(channelAssociation);
        if      (current == InternalState.RELOADING) {
            internalSetState(new TransitionTask() {
                @Override
                public boolean execute(ManagedServer server) throws Exception {
                    // Update the current remote connection
                    protocolClient.connected(remoteClient);
                    // clear reload required state
                    requiresReload = false;
                    return true;
                }
            }, InternalState.RELOADING, InternalState.SERVER_STARTING);
        } else {
            internalSetState(new TransitionTask() {
                @Override
                public boolean execute(final ManagedServer server) throws Exception {
                    // Update the current remote connection
                    protocolClient.connected(remoteClient);
                    return true;
                }
            // TODO we just check that we are in the correct state, perhaps introduce a new state
            }, InternalState.SEND_STDIN, InternalState.SERVER_STARTING);
        }
        return remoteClient;
    }

    synchronized void serverStarted(final TransitionTask task) {
        internalSetState(task, InternalState.SERVER_STARTING, InternalState.SERVER_STARTED);
    }

    synchronized void serverStartFailed() {
        internalSetState(null, InternalState.SERVER_STARTING, InternalState.FAILED);
    }

    /**
     * Unregister the mgmt channel.
     *
     * @param old the proxy controller to unregister
     * @param shuttingDown whether the server inventory is shutting down
     * @return whether the registration can be removed from the domain-controller
     */
    boolean callbackUnregistered(final TransactionalProtocolClient old, final boolean shuttingDown) {
        // Disconnect the remote connection.
        // WFCORE-196 Do this out of the sync block to avoid deadlocks where in-flight requests can't
        // be informed that the channel has closed
        protocolClient.disconnected(old);

        synchronized (this) {
            // If the connection dropped without us stopping the process ask for reconnection
            if (!shuttingDown && requiredState == InternalState.SERVER_STARTED) {
                final InternalState state = internalState;
                if (state == InternalState.PROCESS_STOPPED
                        || state == InternalState.PROCESS_STOPPING
                        || state == InternalState.STOPPED) {
                    // In case it stopped we don't reconnect
                    return true;
                }
                // In case we are reloading, it will reconnect automatically
                if (state == InternalState.RELOADING) {
                    return true;
                }
                try {
                    ROOT_LOGGER.logf(DEBUG_LEVEL, "trying to reconnect to %s current-state (%s) required-state (%s)", serverName, state, requiredState);
                    internalSetState(new ReconnectTask(), state, InternalState.SEND_STDIN);
                } catch (Exception e) {
                    ROOT_LOGGER.logf(DEBUG_LEVEL, e, "failed to send reconnect task");
                }
                return false;
            } else {
                return true;
            }
        }
    }

    /**
     * Notification that the server process finished.
     */
    synchronized void processFinished() {
        final InternalState required = this.requiredState;
        final InternalState state = this.internalState;
        // If the server was not stopped
        if(required == InternalState.STOPPED && state == InternalState.PROCESS_STOPPING) {
            finishTransition(InternalState.PROCESS_STOPPING, InternalState.PROCESS_STOPPED);
        } else {
            this.requiredState = InternalState.STOPPED;
            if ( !(internalSetState(getTransitionTask(InternalState.PROCESS_STOPPING), internalState, InternalState.PROCESS_STOPPING)
                    && internalSetState(getTransitionTask(InternalState.PROCESS_REMOVING), internalState, InternalState.PROCESS_REMOVING)
                    && internalSetState(getTransitionTask(InternalState.STOPPED), internalState, InternalState.STOPPED)) ){
                this.requiredState = InternalState.FAILED;
                internalSetState(null, internalState, InternalState.PROCESS_STOPPED);
            }
        }
    }

    /**
     * Notification that the process got removed from the process controller.
     */
    void processRemoved() {
        finishTransition(InternalState.PROCESS_REMOVING, InternalState.STOPPED);
        unstable = false;
    }

    private void transition() {
        transition(true);
    }

    private synchronized void transition(boolean checkAsync) {
        final InternalState required = this.requiredState;
        final InternalState current = this.internalState;
        // Check if we are waiting for a notification from the server
        if(checkAsync && current.isAsync()) {
            return;
        }
        final InternalState next = nextState(current, required);
        if (next != null) {
            final TransitionTask task = getTransitionTask(next);
            internalSetState(task, current, next);
        }
    }

    /**
     * Notification that a state transition failed.
     *
     * @param state the failed transition
     */
    synchronized void transitionFailed(final InternalState state) {
        final InternalState current = this.internalState;
        if(state == current) {
            // Revert transition and mark as failed
            switch (current) {
                case PROCESS_ADDING:
                    this.internalState = InternalState.PROCESS_STOPPED;
                    break;
                case PROCESS_STARTED:
                    internalSetState(getTransitionTask(InternalState.PROCESS_STOPPING), InternalState.PROCESS_STARTED, InternalState.PROCESS_ADDED);
                    break;
                case PROCESS_STARTING:
                    this.internalState = InternalState.PROCESS_ADDED;
                    break;
                case SEND_STDIN:
                case SERVER_STARTING:
                    this.internalState = InternalState.PROCESS_STARTED;
                    break;
            }
            this.requiredState = InternalState.FAILED;
            notifyAll();
        }
    }

    private synchronized void transitionFailed(final InternalState state, Exception cause) {
        if (state == this.internalState) {
            switch (this.requiredState) {
                case PROCESS_ADDED:
                case PROCESS_ADDING:
                case PROCESS_STARTED:
                case PROCESS_STARTING:
                case SERVER_STARTING:
                case SERVER_STARTED:
                    ROOT_LOGGER.failedToStartServer(cause, serverName);
                    break;
                case PROCESS_REMOVING:
                case PROCESS_STOPPED:
                case PROCESS_STOPPING:
                case STOPPED:
                    ROOT_LOGGER.failedToStopServer(cause, serverName);
                    break;
            }
        }
        transitionFailed(state);
    }
    /**
     * Finish a state transition from a notification.
     *
     * @param current
     * @param next
     */
    private synchronized void finishTransition(final InternalState current, final InternalState next) {
        internalSetState(getTransitionTask(next), current, next);
        transition();
    }

    private boolean internalSetState(final TransitionTask task, final InternalState current, final InternalState next) {
        assert Thread.holdsLock(this); // Call under lock
        final InternalState internalState = this.internalState;
        ROOT_LOGGER.logf(DEBUG_LEVEL, "changing server state (%s) from %s to %s", serverName, current, next);
        if(internalState == current) {
            try {
                if(task != null) {
                    if(! task.execute(this)) {
                        return true; // Not a failure condition
                    }
                }
                this.internalState = next;
                return true;
            } catch (final Exception e) {
                ROOT_LOGGER.logf(DEBUG_LEVEL, e, "transition (%s > %s) failed for server \"%s\"", current, next, serverName);
                transitionFailed(current, e);
            } finally {
                notifyAll();
            }
        }
        return false;
    }

    private TransitionTask getTransitionTask(final InternalState next) {
        switch (next) {
            case PROCESS_ADDING: {
                return new ProcessAddTask();
            } case PROCESS_STARTING: {
                return new ProcessStartTask();
            } case SEND_STDIN: {
                return new SendStdInTask();
            } case SERVER_STARTED: {
                return new ServerStartedTask();
            } case PROCESS_STOPPING: {
                return new ServerStopTask(null);
            } case PROCESS_REMOVING: {
                return new ProcessRemoveTask();
            } default: {
                return null;
            }
        }
    }

    private static InternalState nextState(final InternalState state, final InternalState required) {
        switch (state) {
            case STOPPED: {
                if(required == InternalState.SERVER_STARTED) {
                    return InternalState.PROCESS_ADDING;
                }
                break;
            } case PROCESS_ADDING: {
                if(required == InternalState.SERVER_STARTED) {
                    return InternalState.PROCESS_ADDED;
                }
                break;
            } case PROCESS_ADDED: {
                if(required == InternalState.SERVER_STARTED) {
                    return InternalState.PROCESS_STARTING;
                } else if( required == InternalState.STOPPED) {
                    return InternalState.PROCESS_REMOVING;
                }
                break;
            } case PROCESS_STARTING: {
                if(required == InternalState.SERVER_STARTED) {
                    return InternalState.PROCESS_STARTED;
                }
                break;
            } case PROCESS_STARTED: {
                if(required == InternalState.SERVER_STARTED) {
                    return InternalState.SEND_STDIN;
                } else if( required == InternalState.STOPPED) {
                    return InternalState.PROCESS_STOPPING;
                }
                break;
            } case SEND_STDIN: {
                if(required == InternalState.SERVER_STARTED) {
                    return InternalState.SERVER_STARTING;
                } else if( required == InternalState.STOPPED) {
                    return InternalState.PROCESS_STOPPING;
                }
                break;
            } case SERVER_STARTING: {
                if(required == InternalState.SERVER_STARTED) {
                    return InternalState.SERVER_STARTED;
                } else if( required == InternalState.STOPPED) {
                    return InternalState.PROCESS_STOPPING;
                }
                break;
            } case SERVER_STARTED: {
                if(required == InternalState.STOPPED) {
                    return InternalState.PROCESS_STOPPING;
                }
                break;
            } case RELOADING: {
                if (required == InternalState.SERVER_STARTED) {
                    return InternalState.SERVER_STARTED;
                } else if (required == InternalState.STOPPED) {
                    return InternalState.PROCESS_STOPPING;
                }
                break;
            } case PROCESS_STOPPING: {
                if(required == InternalState.STOPPED) {
                    return InternalState.PROCESS_STOPPED;
                }
                break;
            } case PROCESS_STOPPED: {
                if(required == InternalState.SERVER_STARTED) {
                    return InternalState.PROCESS_STARTING;
                } else if( required == InternalState.STOPPED) {
                    return InternalState.PROCESS_REMOVING;
                }
                break;
            } case PROCESS_REMOVING: {
                if(required == InternalState.STOPPED) {
                    return InternalState.STOPPED;
                }
                break;
            } default: {
                return null;
            }
        }
        return null;
    }

    AsyncFuture<OperationResponse> resume(final BlockingQueueOperationListener<TransactionalProtocolClient.Operation> listener) throws IOException {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(RESUME);
        operation.get(OP_ADDR).setEmptyList();

        return protocolClient.execute(listener, operation, OperationMessageHandler.DISCARD, OperationAttachments.EMPTY);
    }

    AsyncFuture<OperationResponse> suspend(int timeoutInSeconds, final BlockingQueueOperationListener<TransactionalProtocolClient.Operation> listener) throws IOException {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(SUSPEND);
        operation.get(OP_ADDR).setEmptyList();
        operation.get(SUSPEND_TIMEOUT).set(timeoutInSeconds);

        return protocolClient.execute(listener, operation, OperationMessageHandler.DISCARD, OperationAttachments.EMPTY);
    }

    private AsyncFuture<OperationResponse> shutdown(int timeoutInSeconds, final BlockingQueueOperationListener<TransactionalProtocolClient.Operation> listener) throws IOException {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(SHUTDOWN);
        operation.get(OP_ADDR).setEmptyList();
        operation.get(SUSPEND_TIMEOUT).set(timeoutInSeconds);

        return protocolClient.execute(listener, operation, OperationMessageHandler.DISCARD, OperationAttachments.EMPTY);
    }

    enum InternalState {

        STOPPED,
        PROCESS_ADDING(true),
        PROCESS_ADDED,
        PROCESS_STARTING(true),
        PROCESS_STARTED,
        SEND_STDIN(true),
        SERVER_STARTING(true),
        SERVER_STARTED,
        RELOADING(true),
        PROCESS_STOPPING(true),
        PROCESS_STOPPED,
        PROCESS_REMOVING(true),
        SUSPENDING(true),
        FAILED,
        ;

        /** State transition creates an async task. */
        private final boolean async;

        InternalState() {
            this(false);
        }

        InternalState(boolean async) {
            this.async = async;
        }

        boolean isAsync() {
            return async;
        }
    }

    @FunctionalInterface
    interface TransitionTask {

        boolean execute(ManagedServer server) throws Exception;

    }

    private class ProcessAddTask implements TransitionTask {

        @Override
        public boolean execute(ManagedServer server) throws Exception {
            assert Thread.holdsLock(ManagedServer.this); // Call under lock
            final List<String> command = bootConfiguration.getServerLaunchCommand();
            final Map<String, String> env = bootConfiguration.getServerLaunchEnvironment();
            final HostControllerEnvironment environment = bootConfiguration.getHostControllerEnvironment();
            final int processId = bootConfiguration.getServerProcessId();
            // Add the process to the process controller
            processControllerClient.addProcess(serverProcessName, processId, command.toArray(new String[command.size()]), environment.getHomeDir().getAbsolutePath(), env);
            return true;
        }

    }

    private class ProcessRemoveTask implements TransitionTask {
        @Override
        public boolean execute(ManagedServer server) throws Exception {
            assert Thread.holdsLock(ManagedServer.this); // Call under lock
            // Remove process
            processControllerClient.removeProcess(serverProcessName);
            return true;
        }
    }


    private class ProcessStartTask implements TransitionTask {

        @Override
        public boolean execute(ManagedServer server) throws Exception {
            assert Thread.holdsLock(ManagedServer.this); // Call under lock
            // Start the process
            processControllerClient.startProcess(serverProcessName);
            return true;
        }

    }

    private class SendStdInTask implements TransitionTask {

        @Override
        public boolean execute(ManagedServer server) throws Exception {
            assert Thread.holdsLock(ManagedServer.this); // Call under lock
            // Get the standalone boot updates
            final List<ModelNode> bootUpdates = Collections.emptyList(); // bootConfiguration.getBootUpdates();
            final Map<String, String> launchProperties = bootConfiguration.getServerLaunchProperties();
            final boolean useSubsystemEndpoint = bootConfiguration.isManagementSubsystemEndpoint();
            final ModelNode endpointConfig = bootConfiguration.getSubsystemEndpointConfiguration();
            // Send std.in
            final ServiceActivator hostControllerCommActivator = DomainServerCommunicationServices.create(endpointConfig, managementURI, serverName, serverProcessName, serverAuthToken, useSubsystemEndpoint, bootConfiguration.getSSLContextSupplier());
            final ServerStartTask startTask = new ServerStartTask(hostControllerName, serverName, 0, operationID,
                    Collections.<ServiceActivator>singletonList(hostControllerCommActivator), bootUpdates, launchProperties,
                    bootConfiguration.isSuspended(), bootConfiguration.isGracefulStartup());
            final Marshaller marshaller = MARSHALLER_FACTORY.createMarshaller(CONFIG);
            final OutputStream os = processControllerClient.sendStdin(serverProcessName);
            marshaller.start(Marshalling.createByteOutput(os));
            marshaller.writeObject(startTask);
            marshaller.finish();
            marshaller.close();
            os.close();
            return true;
        }
    }

    private class ServerStartedTask implements TransitionTask {

        @Override
        public boolean execute(ManagedServer server) throws Exception {
            return true;
        }

    }

    private class ServerStopTask implements TransitionTask {
        private final Integer gracefulTimeout;

        private ServerStopTask(Integer gracefulTimeout) {
            this.gracefulTimeout = gracefulTimeout;
        }

        @Override
        public boolean execute(ManagedServer server) throws Exception {
            assert Thread.holdsLock(ManagedServer.this); // Call under lock

            try {
                //graceful shutdown
                //this just suspends the server, it does not actually shut it down
                //All catch use the suspend error log traces because the operation at the end is a suspend
                if ( gracefulTimeout != null ){
                    BlockingQueueOperationListener<TransactionalProtocolClient.Operation> listener = new BlockingQueueOperationListener<>();
                    AsyncFuture<OperationResponse> future = null;

                    try {
                        future = shutdown(gracefulTimeout, listener);
                        final TransactionalProtocolClient.PreparedOperation<?> prepared = listener.retrievePreparedOperation();

                        if (prepared.isFailed()) {
                            return false;
                        }

                        //we stop the server via an operation
                        prepared.commit();
                        prepared.getFinalResult().get();

                    } catch (IOException e) {
                        HostControllerLogger.ROOT_LOGGER.suspendExecutionFailed(e, serverName);
                        return false;
                    } catch (InterruptedException e) {
                        HostControllerLogger.ROOT_LOGGER.interruptedAwaitingSuspendResponse(e, serverName);
                        future.asyncCancel(true);
                        Thread.currentThread().interrupt();
                        return false;
                    } catch (ExecutionException e) {
                        HostControllerLogger.ROOT_LOGGER.suspendListenerFailed(e, serverName);
                        future.asyncCancel(true);
                        return false;
                    }
                }
            } finally {
                try {
                    processControllerClient.stopProcess(serverProcessName);
                } catch (IOException ignore) {

                }
            }
            return true;
        }
    }

    private class ReconnectTask implements TransitionTask {

        @Override
        public boolean execute(ManagedServer server) throws Exception {
            assert Thread.holdsLock(ManagedServer.this); // Call under lock
            // Reconnect
            processControllerClient.reconnectServerProcess(serverProcessName, managementURI, bootConfiguration.isManagementSubsystemEndpoint(), serverAuthToken);
            return true;
        }
    }

    private class ReloadTask implements TransitionTask {

        private final int permit;
        private final boolean suspend;
        private ReloadTask(int permit, boolean suspend) {
            this.permit = permit;
            this.suspend = suspend;
        }

        @Override
        public boolean execute(ManagedServer server) throws Exception {

            final ModelNode operation = new ModelNode();
            operation.get(OP).set(RELOAD);
            operation.get(OP_ADDR).setEmptyList();
            if(suspend) {
                operation.get(START_MODE).set(SUSPEND);
            }
            // See WFCORE-1791, Operation-id is sent back again to the HC in
            // HostControllerConnection.ServerRegisterRequest.sendRequest method.
            // With this operation-id ServerRegistrationStepHandler is able to acquire
            // the lock and register the server in the domain.
            operation.get("operation-id").set(permit);

            try {
                final TransactionalProtocolClient.PreparedOperation<?> prepared = TransactionalProtocolHandlers.executeBlocking(operation, protocolClient);
                if (prepared.isFailed()) {
                    return false;
                }
                prepared.commit(); // Just commit and discard the result
            } catch (IOException ignore) {
                //
            }
            return true;
        }
    }

    PathAddress getAddress(){
        return address;
    }

}
