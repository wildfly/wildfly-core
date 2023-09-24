/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import org.jboss.as.host.controller.discovery.DiscoveryOption;
import org.jboss.as.host.controller.discovery.DomainControllerManagementInterface;
import org.jboss.as.network.NetworkInterfaceBinding;
import org.jboss.as.server.services.net.NetworkInterfaceService;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * Service responsible for allowing a domain controller to be discovered by
 * slave host controllers.
 *
 * @author Farah Juma
 */
class DiscoveryService implements Service<Void> {

    static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("host", "controller", "discovery");

    private final Map<String, InjectedValue<NetworkInterfaceBinding>> interfaceBindings = new HashMap<String, InjectedValue<NetworkInterfaceBinding>>();
    private final InjectedValue<ExecutorService> executorService = new InjectedValue<ExecutorService>();
    private final List<DiscoveryOption> discoveryOptions;
    private final List<DomainControllerManagementInterface> managementInterfaces;
    private final boolean isMasterDomainController;

    /**
     * Create the DiscoveryService instance.
     *
     * @param discoveryOptions the list of discovery options
     * @param port the port number of the domain controller
     * @param isMasterDomainController whether or not the local host controller is the master
     */
    private DiscoveryService(List<DiscoveryOption> discoveryOptions, List<DomainControllerManagementInterface> managementInterfaces, boolean isMasterDomainController) {
        this.discoveryOptions = discoveryOptions;
        this.managementInterfaces = managementInterfaces;
        for(DomainControllerManagementInterface managementInterface : managementInterfaces) {
            interfaceBindings.put(managementInterface.getHost(), new InjectedValue<NetworkInterfaceBinding>());
        }
        this.isMasterDomainController = isMasterDomainController;
    }

    static void install(final ServiceTarget serviceTarget, final List<DiscoveryOption> discoveryOptions,
                        final List<DomainControllerManagementInterface> managementInterfaces, final boolean isMasterDomainController) {
        final DiscoveryService discovery = new DiscoveryService(discoveryOptions, managementInterfaces, isMasterDomainController);
        ServiceBuilder builder = serviceTarget.addService(DiscoveryService.SERVICE_NAME, discovery)
                .addDependency(HostControllerService.HC_EXECUTOR_SERVICE_NAME, ExecutorService.class, discovery.executorService);
        Set<String> alreadyDefinedInterfaces = new HashSet<String>();
        for(DomainControllerManagementInterface managementInterface : managementInterfaces) {
            if(!alreadyDefinedInterfaces.contains(managementInterface.getAddress())) {
                builder.addDependency(NetworkInterfaceService.JBOSS_NETWORK_INTERFACE.append(managementInterface.getAddress()), NetworkInterfaceBinding.class, discovery.interfaceBindings.get(managementInterface.getAddress()));
                alreadyDefinedInterfaces.add(managementInterface.getAddress());
            }
        }
       builder.install();
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void start(final StartContext context) throws StartException {
        final Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    if (isMasterDomainController && (discoveryOptions != null)) {
                        // Allow slave host controllers to discover this domain controller using any
                        // of the provided discovery options.

                        for(DomainControllerManagementInterface managementInterface : managementInterfaces) {
                            String host = interfaceBindings.get(managementInterface.getAddress()).getValue().getAddress().getHostAddress();
                            managementInterface.setHost(host);
                        }
                        for (DiscoveryOption discoveryOption : discoveryOptions) {
                            discoveryOption.allowDiscovery(managementInterfaces);
                        }
                    }
                    context.complete();
                } catch (Exception e) {
                    context.failed(new StartException(e));
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
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void stop(final StopContext context) {
        final Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    if (isMasterDomainController && (discoveryOptions != null)) {
                        for (DiscoveryOption discoveryOption : discoveryOptions) {
                            discoveryOption.cleanUp();
                        }
                    }
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
    }

    /** {@inheritDoc} */
    @Override
    public synchronized Void getValue() throws IllegalStateException, IllegalArgumentException {
       return null;
    }
}
