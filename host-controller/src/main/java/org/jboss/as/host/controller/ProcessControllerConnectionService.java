/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller;

import static java.security.AccessController.doPrivileged;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.process.ProcessControllerClient;
import org.jboss.as.process.ProcessInfo;
import org.jboss.as.process.ProcessMessageHandler;
import org.jboss.as.process.protocol.ProtocolClient;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.threads.EnhancedQueueExecutor;
import org.jboss.threads.JBossThreadFactory;

/**
 * Provides a client for interacting with the process controller.
 *
 * @author Emanuel Muckenhuber
 */
class ProcessControllerConnectionService implements Service<ProcessControllerConnectionService> {

    static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("host", "controller", "process-controller-connection");

    private final HostControllerEnvironment environment;
    private final String authCode;
    private volatile ProcessControllerClient client;
    private volatile ServerInventory serverInventory;

    private static final int WORK_QUEUE_SIZE = 256;
    private static final int THREAD_POOL_CORE_SIZE = 1;
    private static final int THREAD_POOL_MAX_SIZE = 4;

    ProcessControllerConnectionService(final HostControllerEnvironment environment, final String authCode) {
        this.environment = environment;
        this.authCode = authCode;
    }

    void setServerInventory(ServerInventory serverInventory) {
        this.serverInventory = serverInventory;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void start(StartContext context) throws StartException {
        final ProcessControllerClient client;
        try {
            final ThreadFactory threadFactory = doPrivileged(new PrivilegedAction<JBossThreadFactory>() {
                public JBossThreadFactory run() {
                    return new JBossThreadFactory(ThreadGroupHolder.THREAD_GROUP, Boolean.FALSE, null, "%G - %t", null, null);
                }
            });
            final ExecutorService executorService;
            if (EnhancedQueueExecutor.DISABLE_HINT) {
                executorService = new ThreadPoolExecutor(THREAD_POOL_CORE_SIZE, THREAD_POOL_MAX_SIZE, 30L, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>(WORK_QUEUE_SIZE), threadFactory);
            } else {
                executorService = new EnhancedQueueExecutor.Builder()
                    .setCorePoolSize(THREAD_POOL_CORE_SIZE)
                    .setMaximumPoolSize(THREAD_POOL_MAX_SIZE)
                    .setKeepAliveTime(30L, TimeUnit.MILLISECONDS)
                    .setMaximumQueueSize(WORK_QUEUE_SIZE)
                    .setThreadFactory(threadFactory)
                    .build();
            }

            final ProtocolClient.Configuration configuration = new ProtocolClient.Configuration();
            configuration.setReadExecutor(executorService);
            configuration.setServerAddress(new InetSocketAddress(environment.getProcessControllerAddress(), environment.getProcessControllerPort().intValue()));
            configuration.setBindAddress(new InetSocketAddress(environment.getHostControllerAddress(), environment.getHostControllerPort()));
            configuration.setThreadFactory(threadFactory);
            configuration.setSocketFactory(SocketFactory.getDefault());
            client = ProcessControllerClient.connect(configuration, authCode, new ProcessMessageHandler() {
                @Override
                public void handleProcessAdded(final ProcessControllerClient client, final String processName) {
                    if (serverInventory == null){
                        throw HostControllerLogger.ROOT_LOGGER.noServerInventory();
                    }
                    if(ManagedServer.isServerProcess(processName)) {
                        serverInventory.serverProcessAdded(processName);
                    }
                }

                @Override
                public void handleProcessStarted(final ProcessControllerClient client, final String processName) {
                    if (serverInventory == null){
                        throw HostControllerLogger.ROOT_LOGGER.noServerInventory();
                    }
                    if(ManagedServer.isServerProcess(processName)) {
                        serverInventory.serverProcessStarted(processName);
                    }
                }

                @Override
                public void handleProcessStopped(final ProcessControllerClient client, final String processName, final long uptimeMillis) {
                    if (serverInventory == null){
                        throw HostControllerLogger.ROOT_LOGGER.noServerInventory();
                    }
                    if(ManagedServer.isServerProcess(processName)) {
                        serverInventory.serverProcessStopped(processName);
                    }
                }

                @Override
                public void handleProcessRemoved(final ProcessControllerClient client, final String processName) {
                    if (serverInventory == null){
                        throw HostControllerLogger.ROOT_LOGGER.noServerInventory();
                    }
                    if(ManagedServer.isServerProcess(processName)) {
                        serverInventory.serverProcessRemoved(processName);
                    }
                }

                @Override
                public void handleProcessInventory(final ProcessControllerClient client, final Map<String, ProcessInfo> inventory) {
                    if (serverInventory == null){
                        throw HostControllerLogger.ROOT_LOGGER.noServerInventory();
                    }
                    serverInventory.processInventory(inventory);
                }

                @Override
                public void handleConnectionShutdown(final ProcessControllerClient client) {
                    if(serverInventory == null) {
                        return;
                    }
                    serverInventory.connectionFinished();
                }

                @Override
                public void handleConnectionFailure(final ProcessControllerClient client, final IOException cause) {
                    if(serverInventory == null) {
                        return;
                    }
                    serverInventory.connectionFinished();
                }

                @Override
                public void handleConnectionFinished(final ProcessControllerClient client) {
                    if(serverInventory == null) {
                        return;
                    }
                    serverInventory.connectionFinished();
                }

                @Override
                public void handleOperationFailed(ProcessControllerClient client, OperationType operation, String processName) {
                    if (serverInventory == null){
                        throw HostControllerLogger.ROOT_LOGGER.noServerInventory();
                    }
                    if(ManagedServer.isServerProcess(processName)) {
                        serverInventory.operationFailed(processName, operation);
                    }
                }
            });
        } catch(IOException e) {
            throw new StartException(e);
        }
        this.client = client;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void stop(StopContext context) {
        final ProcessControllerClient client = this.client;
        this.client = null;
        StreamUtils.safeClose(client);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized ProcessControllerConnectionService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public synchronized ProcessControllerClient getClient() throws IllegalStateException, IllegalArgumentException {
        final ProcessControllerClient client = this.client;
        if(client == null) {
            throw new IllegalStateException();
        }
        return client;
    }

    // Wrapper class to delay thread group creation until when it's needed.
    private static class ThreadGroupHolder {
        private static final ThreadGroup THREAD_GROUP = new ThreadGroup("ProcessControllerConnection-thread");
    }
}
