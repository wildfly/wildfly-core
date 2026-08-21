/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import static org.jboss.as.controller.capability.RuntimeCapability.buildDynamicCapabilityName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.AbsolutePathService;
import org.jboss.as.remoting.AbstractRemotingSubsystemBaseTest.DependenciesRetrievalService;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.ControllerInitializer;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.EndpointWrapper;
import org.jboss.remoting3.RemotingOptions;
import org.junit.Test;
import org.wildfly.extension.io.WorkerService;
import org.wildfly.io.IOServiceDescriptor;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

public class RemoteOutboundConnectionPropertiesTestCase extends AbstractSubsystemTest {

    public RemoteOutboundConnectionPropertiesTestCase() {
        super(RemotingExtension.SUBSYSTEM_NAME, new RemotingExtension());
    }

    @Test
    public void testOutboundConnectionPropertiesReachEndpoint() throws Exception {
        KernelServices services = createKernelServicesBuilder(createRuntimeAdditionalInitialization())
                .setSubsystemXml(readResource("remoting-with-heartbeat-property.xml"))
                .build();
        assertTrue("Subsystem boot must succeed", services.isSuccessfulBoot());

        ServiceName remotingEndpointSN = RemotingSubsystemRootResource.REMOTING_ENDPOINT_CAPABILITY.getCapabilityServiceName(Endpoint.class);
        ServiceName conn1SN = AbstractOutboundConnectionResourceDefinition.OUTBOUND_CONNECTION_CAPABILITY
                .getCapabilityServiceName("remote-ejb-connection");
        ServiceName conn2SN = AbstractOutboundConnectionResourceDefinition.OUTBOUND_CONNECTION_CAPABILITY
                .getCapabilityServiceName("remote-ejb-connection-2");
        DependenciesRetrievalService dependencies = DependenciesRetrievalService.create(services, remotingEndpointSN, conn1SN, conn2SN);

        Endpoint endpoint = dependencies.getService(remotingEndpointSN);
        assertNotNull("Endpoint service was null", endpoint);

        RemoteOutboundConnectionService conn1 = dependencies.getService(conn1SN);
        assertNotNull("conn1 service was null", conn1);
        RemoteOutboundConnectionService conn2 = dependencies.getService(conn2SN);
        assertNotNull("conn2 service was null", conn2);

        URI destUri1 = conn1.getDestinationUri();
        URI destUri2 = conn2.getDestinationUri();

        Map<URI, OptionMap> perUriOptions = EndpointWrapper.getOptionMap(endpoint);

        // conn1: per-URI transport options must be registered and reflect the configured property values.
        assertTrue("Per-URI options for conn1 must be registered in endpoint (WFLY-16388)", perUriOptions.containsKey(destUri1));
        OptionMap opts1 = perUriOptions.get(destUri1);
        assertEquals("HEARTBEAT_INTERVAL must be 2000ms as configured on remote-ejb-connection",
                Integer.valueOf(2000), opts1.get(RemotingOptions.HEARTBEAT_INTERVAL));
        assertEquals("READ_TIMEOUT must be 4000ms as configured on remote-ejb-connection",
                Integer.valueOf(4000), opts1.get(Options.READ_TIMEOUT));
        assertTrue("KEEP_ALIVE must be true as configured on remote-ejb-connection",
                opts1.get(Options.KEEP_ALIVE));

        // conn2: no per-connection overrides — endpoint-level heartbeat-interval=5000 must be inherited.
        assertTrue("Per-URI options for conn2 must be registered", perUriOptions.containsKey(destUri2));
        assertEquals("conn2 must inherit the endpoint-level heartbeat-interval=5000",
                Integer.valueOf(5000), perUriOptions.get(destUri2).get(RemotingOptions.HEARTBEAT_INTERVAL));

        // The endpoint-level default option map must reflect the configured heartbeat-interval.
        OptionMap defaultOpts = EndpointWrapper.getDefaultOptionMap(endpoint);
        assertEquals("Endpoint-level heartbeat-interval must be 5000ms",
                Integer.valueOf(5000), defaultOpts.get(RemotingOptions.HEARTBEAT_INTERVAL));
    }

    private AdditionalInitialization createRuntimeAdditionalInitialization() {
        return new AdditionalInitialization() {
            @Override
            protected void setupController(ControllerInitializer controllerInitializer) {
                controllerInitializer.addRemoteOutboundSocketBinding("dummy-outbound-socket", "localhost", 6799);
                controllerInitializer.addRemoteOutboundSocketBinding("dummy-outbound-socket-2", "localhost", 6800);
            }

            @Override
            protected void addExtraServices(ServiceTarget target) {
                AbsolutePathService.addService(ServerEnvironment.CONTROLLER_TEMP_DIR,
                        new File("target/temp" + System.currentTimeMillis()).getAbsolutePath(), target);

                ServiceBuilder<?> builder = target.addService(
                        ServiceName.parse(IOServiceDescriptor.WORKER.getName()).append("default"));
                Consumer<XnioWorker> workerConsumer = builder.provides(
                        ServiceName.parse(IOServiceDescriptor.WORKER.getName()).append("default"),
                        ServiceName.parse(IOServiceDescriptor.DEFAULT_WORKER.getName()));
                builder.setInstance(new WorkerService(workerConsumer,
                        () -> Executors.newFixedThreadPool(1),
                        Xnio.getInstance().createWorkerBuilder().setWorkerIoThreads(2)));
                builder.setInitialMode(ServiceController.Mode.ON_DEMAND);
                builder.install();

                builder = target.addService(ServiceName.parse(IOServiceDescriptor.WORKER.getName()).append("default-remoting"));
                workerConsumer = builder.provides(ServiceName.parse(IOServiceDescriptor.WORKER.getName()).append("default-remoting"));
                builder.setInstance(new WorkerService(workerConsumer,
                        () -> Executors.newFixedThreadPool(1),
                        Xnio.getInstance().createWorkerBuilder().setWorkerIoThreads(2)));
                builder.setInitialMode(ServiceController.Mode.ON_DEMAND);
                builder.install();
            }

            @Override
            @SuppressWarnings("rawtypes")
            protected void initializeExtraSubystemsAndModel(ExtensionRegistry extensionRegistry,
                    Resource rootResource, ManagementResourceRegistration rootRegistration,
                    RuntimeCapabilityRegistry capabilityRegistry) {
                super.initializeExtraSubystemsAndModel(extensionRegistry, rootResource, rootRegistration, capabilityRegistry);
                Map<String, Class> capabilities = new HashMap<>();
                capabilities.put(buildDynamicCapabilityName(IOServiceDescriptor.WORKER.getName(), "default"), XnioWorker.class);
                capabilities.put(buildDynamicCapabilityName(IOServiceDescriptor.WORKER.getName(), "default-remoting"), XnioWorker.class);
                AdditionalInitialization.registerServiceCapabilities(capabilityRegistry, capabilities);
            }
        };
    }
}
