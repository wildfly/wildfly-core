/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.mgmt;

import static java.security.AccessController.doPrivileged;

import java.io.File;
import java.security.PrivilegedAction;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.domain.controller.DomainController;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.as.protocol.mgmt.ManagementClientChannelStrategy;
import org.jboss.as.protocol.mgmt.ManagementPongRequestHandler;
import org.jboss.as.protocol.mgmt.support.ManagementChannelInitialization;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.remoting3.Channel;
import org.jboss.threads.JBossThreadFactory;

/**
 * Operation handler responsible for requests coming in from server processes on the host controller.
 * The server side counterpart is {@link org.jboss.as.server.mgmt.domain.HostControllerClient}
 *
 * @author John Bailey
 * @author Emanuel Muckenhuber
 * @author Kabir Khan
 */
public class ServerToHostOperationHandlerFactoryService implements ManagementChannelInitialization, Service<ManagementChannelInitialization> {

    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("management", "server", "to", "host", "controller");

    private final ExecutorService executorService;
    private final InjectedValue<ServerInventory> serverInventory = new InjectedValue<ServerInventory>();
    private final ServerToHostProtocolHandler.OperationExecutor operationExecutor;
    private final DomainController domainController;
    private final ExpressionResolver expressionResolver;
    private final File tempDir;

    private final ThreadFactory threadFactory = doPrivileged(new PrivilegedAction<JBossThreadFactory>() {
        public JBossThreadFactory run() {
            return new JBossThreadFactory(new ThreadGroup("server-registration-threads"), Boolean.FALSE, null, "%G - %t", null, null);
        }
    });
    private volatile ExecutorService registrations;

    ServerToHostOperationHandlerFactoryService(ExecutorService executorService, ServerToHostProtocolHandler.OperationExecutor operationExecutor, DomainController domainController, ExpressionResolver expressionResolver, File tempDir) {
        this.executorService = executorService;
        this.operationExecutor = operationExecutor;
        this.domainController = domainController;
        this.expressionResolver = expressionResolver;
        this.tempDir = tempDir;
    }

    public static void install(final ServiceTarget serviceTarget, final ServiceName serverInventoryName, ExecutorService executorService, ServerToHostProtocolHandler.OperationExecutor operationExecutor, DomainController domainController,
            ExpressionResolver expressionResolver, File tempDir) {
        final ServerToHostOperationHandlerFactoryService serverToHost = new ServerToHostOperationHandlerFactoryService(executorService, operationExecutor, domainController, expressionResolver, tempDir);
        serviceTarget.addService(ServerToHostOperationHandlerFactoryService.SERVICE_NAME, serverToHost)
            .addDependency(serverInventoryName, ServerInventory.class, serverToHost.serverInventory)
            .install();
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void start(StartContext context) throws StartException {
        this.registrations = Executors.newSingleThreadExecutor(threadFactory);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void stop(StopContext context) {
        final ExecutorService executorService = this.registrations;
        this.registrations = null;
        if(executorService != null) {
            executorService.shutdown();
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized ManagementChannelInitialization getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    @Override
    public ManagementChannelHandler startReceiving(final Channel channel) {
        final ManagementClientChannelStrategy strategy = ManagementClientChannelStrategy.create(channel);
        final ManagementChannelHandler channelHandler = new ManagementChannelHandler(strategy, executorService);
        channelHandler.getAttachments().attach(ManagementChannelHandler.TEMP_DIR, tempDir);
        final ServerToHostProtocolHandler registrationHandler = new ServerToHostProtocolHandler(serverInventory.getValue(), operationExecutor, domainController, channelHandler, registrations, expressionResolver);
        channelHandler.addHandlerFactory(new ManagementPongRequestHandler());
        channelHandler.addHandlerFactory(registrationHandler);
        channel.receiveMessage(channelHandler.getReceiver());
        return channelHandler;
    }

}
