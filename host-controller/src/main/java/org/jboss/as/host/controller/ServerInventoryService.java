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

import static org.jboss.as.host.controller.logging.HostControllerLogger.ROOT_LOGGER;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.domain.controller.DomainController;
import org.jboss.as.host.controller.security.ServerVerificationService;
import org.jboss.as.network.NetworkInterfaceBinding;
import org.jboss.as.network.NetworkUtils;
import org.jboss.as.remoting.management.ManagementChannelRegistryService;
import org.jboss.as.server.services.net.NetworkInterfaceService;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.threads.AsyncFutureTask;
import org.wildfly.common.Assert;
import org.wildfly.security.evidence.Evidence;

/**
 * Service providing the {@link ServerInventory}
 *
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
class ServerInventoryService implements Service<ServerInventory> {

    static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("host", "controller", "server-inventory");

    private final InjectedValue<ProcessControllerConnectionService> client = new InjectedValue<ProcessControllerConnectionService>();
    private final InjectedValue<NetworkInterfaceBinding> interfaceBinding = new InjectedValue<NetworkInterfaceBinding>();
    private final DomainController domainController;
    private final HostControllerEnvironment environment;
    private final HostRunningModeControl runningModeControl;
    private final ExtensionRegistry extensionRegistry;
    private final int port;
    private final String protocol;
    private final InjectedValue<ExecutorService> executorService = new InjectedValue<ExecutorService>();
    private final InjectedValue<Consumer<Predicate<Evidence>>> evidenceVerifierConsumer = new InjectedValue<>();

    private final FutureServerInventory futureInventory = new FutureServerInventory();

    private ServerInventoryImpl serverInventory;

    private ServerInventoryService(final DomainController domainController, final HostRunningModeControl runningModeControl,
                                   final HostControllerEnvironment environment, final ExtensionRegistry extensionRegistry, final int port,
                                   final String protocol) {
        this.extensionRegistry = extensionRegistry;
        this.domainController = domainController;
        this.runningModeControl = runningModeControl;
        this.environment = environment;
        this.port = port;
        this.protocol = protocol;
    }

    static Future<ServerInventory> install(final ServiceTarget serviceTarget, final DomainController domainController, final HostRunningModeControl runningModeControl, final HostControllerEnvironment environment,
                                           final ExtensionRegistry extensionRegistry,
                                           final String interfaceBinding, final int port, final String protocol){

        final ServerInventoryService inventory = new ServerInventoryService(domainController, runningModeControl, environment, extensionRegistry, port, protocol);
        final ServiceBuilder sb = serviceTarget.addService(ServerInventoryService.SERVICE_NAME, inventory);
        sb.addDependency(HostControllerService.HC_EXECUTOR_SERVICE_NAME, ExecutorService.class, inventory.executorService);
        sb.addDependency(ProcessControllerConnectionService.SERVICE_NAME, ProcessControllerConnectionService.class, inventory.getClient());
        sb.addDependency(NetworkInterfaceService.JBOSS_NETWORK_INTERFACE.append(interfaceBinding), NetworkInterfaceBinding.class, inventory.interfaceBinding);
        sb.addDependency(ServerVerificationService.REGISTRATION_NAME, Consumer.class, inventory.evidenceVerifierConsumer);
        sb.requires(ManagementChannelRegistryService.SERVICE_NAME);
        sb.install();
        return inventory.futureInventory;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void start(StartContext context) throws StartException {
        ROOT_LOGGER.debug("Starting Host Controller Server Inventory");
        try {
            final ProcessControllerConnectionService processControllerConnectionService = client.getValue();
            URI managementURI = new URI(protocol, null, NetworkUtils.formatAddress(getNonWildCardManagementAddress()), port, null, null, null);
            serverInventory = new ServerInventoryImpl(domainController, environment, managementURI, processControllerConnectionService.getClient(), extensionRegistry);
            processControllerConnectionService.setServerInventory(serverInventory);
            futureInventory.setInventory(serverInventory);
            evidenceVerifierConsumer.getValue().accept(serverInventory::validateServerEvidence);
        } catch (Exception e) {
            futureInventory.setFailure(e);
            throw new StartException(e);
        }
    }

    private InetAddress getNonWildCardManagementAddress() throws UnknownHostException {
        InetAddress binding = interfaceBinding.getValue().getAddress();
        return binding.isAnyLocalAddress() ? InetAddress.getLocalHost() : binding;
    }

    /**
     * Stops all servers.
     *
     * {@inheritDoc}
     */
    @Override
    public synchronized void stop(final StopContext context) {
        final boolean shutdownServers = runningModeControl.getRestartMode() == RestartMode.SERVERS;
        if (shutdownServers) {
            Runnable task = new Runnable() {
                @Override
                public void run() {
                    try {
                        serverInventory.shutdown(true, -1, true); // TODO graceful shutdown
                        serverInventory = null;
                        // client.getValue().setServerInventory(null);
                    } finally {
                        context.complete();
                    }
                }
            };
            try {
                executorService.getValue().execute(task);
            } catch (RejectedExecutionException e) {
                task.run();
            } finally {
                context.asynchronous();
            }
        } else {
            // We have to set the shutdown flag in any case
            serverInventory.shutdown(false, -1, true);
            serverInventory = null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized ServerInventory getValue() throws IllegalStateException, IllegalArgumentException {
        final ServerInventory serverInventory = this.serverInventory;
        if(serverInventory == null) {
            throw new IllegalStateException();
        }
        return serverInventory;
    }

    InjectedValue<ProcessControllerConnectionService> getClient() {
        return client;
    }

    private class FutureServerInventory extends AsyncFutureTask<ServerInventory>{

        protected FutureServerInventory() {
            super(null);
        }

        private void setInventory(ServerInventory inventory) {
            super.setResult(inventory);
        }

        private void setFailure(final Throwable t) {
            Assert.checkNotNullParam("Throwable", t);
            super.setFailed(t);
        }
    }

}
