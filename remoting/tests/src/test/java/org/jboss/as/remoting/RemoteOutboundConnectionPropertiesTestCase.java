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
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * Reproducer for WFLY-16388: non-SASL properties (e.g. HEARTBEAT_INTERVAL) configured on a
 * remote-outbound-connection are silently dropped and never reach the remoting channel.
 */
public class RemoteOutboundConnectionPropertiesTestCase extends AbstractSubsystemTest {

    public RemoteOutboundConnectionPropertiesTestCase() {
        super(RemotingExtension.SUBSYSTEM_NAME, new RemotingExtension());
    }

    /**
     * Verifies that HEARTBEAT_INTERVAL configured in {@code <remote-outbound-connection>/<properties>}
     * reaches the remoting endpoint so EJB clients can apply it when establishing connections.
     */
    @Test
    public void testHeartbeatIntervalPropertyIsPreservedInOutboundConnection() throws Exception {
        KernelServices services = createKernelServicesBuilder(createRuntimeAdditionalInitialization())
                .setSubsystemXml(readResource("remoting-with-heartbeat-property.xml"))
                .build();
        assertTrue("Subsystem boot must succeed", services.isSuccessfulBoot());

        ServiceName remotingEndpointSN = RemotingSubsystemRootResource.REMOTING_ENDPOINT_CAPABILITY.getCapabilityServiceName(Endpoint.class);
        ServiceName remotingConnSN = AbstractOutboundConnectionResourceDefinition.OUTBOUND_CONNECTION_CAPABILITY
                .getCapabilityServiceName("remote-ejb-connection");
        DependenciesRetrievalService dependencies = DependenciesRetrievalService.create(services, remotingEndpointSN, remotingConnSN);

        Endpoint endpoint = dependencies.getService(remotingEndpointSN);
        assertNotNull("Endpoint service was null", endpoint);
        assertNotNull(endpoint.getName());

        RemoteOutboundConnectionService conn = dependencies.getService(remotingConnSN);
        assertNotNull("OutboundConnection service was null", conn);

        URI destUri = conn.getDestinationUri();

        // After the fix, per-URI transport options (HEARTBEAT_INTERVAL, KEEP_ALIVE, READ_TIMEOUT)
        // must be registered in EndpointImpl.connectionOptions for the destination URI.
        Map<URI, OptionMap> perUriOptions = EndpointWrapper.getOptionMap(endpoint);
        assertTrue(
                "Per-URI transport options for " + destUri + " must be registered in the endpoint "
                        + "(WFLY-16388: non-SASL <properties> are silently dropped)",
                perUriOptions.containsKey(destUri));
        assertEquals(
                "HEARTBEAT_INTERVAL must be 2000ms as configured on remote-ejb-connection <properties>",
                Integer.valueOf(2000),
                perUriOptions.get(destUri).get(RemotingOptions.HEARTBEAT_INTERVAL));
    }

    private AdditionalInitialization createRuntimeAdditionalInitialization() {
        return new AdditionalInitialization() {
            @Override
            protected void setupController(ControllerInitializer controllerInitializer) {
                controllerInitializer.addRemoteOutboundSocketBinding("dummy-outbound-socket", "localhost", 6799);
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
