/*
 *
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2014, Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags. See the copyright.txt file in the
 *  distribution for a full listing of individual contributors.
 *
 *  This is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this software; if not, write to the Free
 *  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  02110-1301 USA, or see the FSF site: http://www.fsf.org.
 * /
 */
package org.jboss.as.remoting;

import static org.jboss.as.controller.capability.RuntimeCapability.buildDynamicCapabilityName;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALLOW_RESOURCE_SERVICE_RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.remoting.Capabilities.IO_WORKER_CAPABILITY_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.AbsolutePathService;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.ControllerInitializer;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceNotFoundException;
import org.jboss.msc.service.ServiceTarget;
import org.junit.Ignore;
import org.junit.Test;
import org.wildfly.extension.io.IOServices;
import org.wildfly.extension.io.WorkerService;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="opalka.richard@gmail.com">Richard Opalka</a>
 */
public class RemotingLegacySubsystemTestCase extends AbstractRemotingSubsystemBaseTest {

    public RemotingLegacySubsystemTestCase() {
        super(RemotingSubsystemSchema.CURRENT);
    }

    @Override
    protected void compare(ModelNode node1, ModelNode node2) {
        // First, clean up io stuff parser adds when the old remoting version is used
        cleanIO(node1);
        //cleanIO(node2);
        super.compare(node1, node2);
    }

    private void cleanIO(ModelNode node) {
        if (node.has(EXTENSION, "org.wildfly.extension.io")) {
            node.get(EXTENSION).remove("org.wildfly.extension.io");
            if (node.get(EXTENSION).asInt() == 0) {
                node.get(EXTENSION).set(new ModelNode());
            }
        }
        if (node.hasDefined(SUBSYSTEM, "io")) {
            node.get(SUBSYSTEM).remove("io");
        }
    }

    @Test
    public void testSubsystem12WithConnector() throws Exception {
        KernelServices services = createKernelServicesBuilder(createRuntimeAdditionalInitialization(true))
                .setSubsystemXmlResource("remoting12-with-connector.xml").build();

        ServiceName connectorServiceName = RemotingServices.serverServiceName("test-connector");
        DependenciesRetrievalService dependencies = DependenciesRetrievalService.create(services, connectorServiceName);
        Object connectorService = dependencies.getService(connectorServiceName);
        assertNotNull(connectorService);

        ModelNode model = services.readWholeModel();
        ModelNode subsystem = model.require(SUBSYSTEM).require(RemotingExtension.SUBSYSTEM_NAME);

        ModelNode connector = subsystem.require(CommonAttributes.CONNECTOR).require("test-connector");
        assertEquals(1, connector.require(CommonAttributes.PROPERTY).require("org.xnio.Options.WORKER_ACCEPT_THREADS").require(CommonAttributes.VALUE).asInt());
        // the following 2 attributes are new in remoting 1.2 NS
        assertEquals("myProto", connector.require(CommonAttributes.SASL_PROTOCOL).asString());
        assertEquals("myServer", connector.require(CommonAttributes.SERVER_NAME).asString());

        // Validate the io subsystem was added
        assertTrue(model.require(SUBSYSTEM).hasDefined("io"));
    }

    @Test
    public void testSubsystemWithConnectorProperties() throws Exception {
        KernelServices services = createKernelServicesBuilder(createRuntimeAdditionalInitialization(false))
                .setSubsystemXmlResource("remoting-with-connector.xml")
                .build();

        ServiceName remotingEndpointSN = RemotingServices.SUBSYSTEM_ENDPOINT;
        ServiceName connectorSN = RemotingServices.serverServiceName("test-connector");
        DependenciesRetrievalService dependencies = DependenciesRetrievalService.create(services, remotingEndpointSN, connectorSN);

        Object remoingEndpointService = dependencies.getService(remotingEndpointSN);
        assertNotNull(remoingEndpointService);

        Object connectorService = dependencies.getService(connectorSN);
        assertNotNull(connectorService);

        ModelNode model = services.readWholeModel();
        ModelNode subsystem = model.require(SUBSYSTEM).require(RemotingExtension.SUBSYSTEM_NAME);

        ModelNode connector = subsystem.require(CommonAttributes.CONNECTOR).require("test-connector");
        assertEquals(1, connector.require(CommonAttributes.PROPERTY).require("org.xnio.Options.WORKER_ACCEPT_THREADS").require(CommonAttributes.VALUE).asInt());
    }

    @Test
    @Ignore("https://issues.redhat.com/browse/WFCORE-5386")
    public void testSubsystemWithConnectorPropertyChange() throws Exception {
        KernelServices services = createKernelServicesBuilder(createRuntimeAdditionalInitialization(false))
                .setSubsystemXmlResource("remoting-with-connector.xml")
                .build();

        CurrentConnectorAndController current = CurrentConnectorAndController.create(services, RemotingServices.SUBSYSTEM_ENDPOINT, RemotingServices.serverServiceName("test-connector"));

        //Test that write property reloads the connector
        ModelNode write = new ModelNode();
        write.get(OPERATION_HEADERS, ALLOW_RESOURCE_SERVICE_RESTART).set(true);
        write.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        write.get(OP_ADDR).add(SUBSYSTEM, RemotingExtension.SUBSYSTEM_NAME).add(CommonAttributes.CONNECTOR, "test-connector").add(CommonAttributes.PROPERTY, "org.xnio.Options.WORKER_ACCEPT_THREADS");
        write.get(NAME).set(VALUE);
        write.get(VALUE).set(2);
        ModelNode result = services.executeOperation(write);
        assertFalse(result.get(FAILURE_DESCRIPTION).toString(), result.hasDefined(FAILURE_DESCRIPTION));
        assertEquals(2, services.readWholeModel().get(SUBSYSTEM, RemotingExtension.SUBSYSTEM_NAME, CommonAttributes.CONNECTOR, "test-connector", CommonAttributes.PROPERTY, "org.xnio.Options.WORKER_ACCEPT_THREADS").require(VALUE).asInt());
        current.updateCurrentEndpoint(true);
        current.updateCurrentConnector(false);

        //remove property
        ModelNode remove = write.clone();
        remove.get(OP).set(REMOVE);
        remove.remove(NAME);
        remove.remove(VALUE);
        result = services.executeOperation(remove);
        assertFalse(result.get(FAILURE_DESCRIPTION).toString(), result.hasDefined(FAILURE_DESCRIPTION));
        assertFalse(services.readWholeModel().get(SUBSYSTEM, RemotingExtension.SUBSYSTEM_NAME, CommonAttributes.CONNECTOR, "test-connector", CommonAttributes.PROPERTY, "org.xnio.Options.WORKER_ACCEPT_THREADS").isDefined());
        current.updateCurrentEndpoint(true);
        current.updateCurrentConnector(false);

        //TODO property
        ModelNode add = remove.clone();
        add.get(OP).set(ADD);
        add.get(VALUE).set(1);
        result = services.executeOperation(add);
        assertFalse(result.get(FAILURE_DESCRIPTION).toString(), result.hasDefined(FAILURE_DESCRIPTION));
        assertEquals(1, services.readWholeModel().get(SUBSYSTEM, RemotingExtension.SUBSYSTEM_NAME, CommonAttributes.CONNECTOR, "test-connector", CommonAttributes.PROPERTY, "org.xnio.Options.WORKER_ACCEPT_THREADS").require(VALUE).asInt());
        current.updateCurrentEndpoint(true);
        current.updateCurrentConnector(false);
    }

    @Test
    public void testSubsystemWithBadConnectorProperty() throws Exception {
        KernelServices services = createKernelServicesBuilder(createRuntimeAdditionalInitialization(true))
                .setSubsystemXmlResource("remoting-with-bad-connector-property.xml")
                .build();

        try {
            services.getContainer().getRequiredService(RemotingServices.SUBSYSTEM_ENDPOINT);
            fail("Expected no " + RemotingServices.SUBSYSTEM_ENDPOINT);
        } catch (ServiceNotFoundException expected) {
            // ok
        }

        try {
            services.getContainer().getRequiredService(RemotingServices.serverServiceName("test-connector"));
            fail("Expected no " + RemotingServices.serverServiceName("test-connector"));
        } catch (ServiceNotFoundException expected) {
            // ok
        }
    }

    /**
     * Tests that the outbound connections configured in the remoting subsytem are processed and services
     * are created for them
     *
     * @throws Exception
     */
    @Test
    public void testOutboundConnections() throws Exception {
        KernelServices services = createKernelServicesBuilder(createRuntimeAdditionalInitialization(false))
                .setSubsystemXmlResource("remoting-with-outbound-connections.xml")
                .build();

        ServiceName remotingEndpointSN = RemotingServices.SUBSYSTEM_ENDPOINT;
        ServiceName remoteOutboundConnectionSN = AbstractOutboundConnectionResourceDefinition.OUTBOUND_CONNECTION_CAPABILITY.getCapabilityServiceName("remote-conn1");
        ServiceName localOutboundConnectionSN = AbstractOutboundConnectionResourceDefinition.OUTBOUND_CONNECTION_CAPABILITY.getCapabilityServiceName("local-conn1");
        DependenciesRetrievalService dependencies = DependenciesRetrievalService.create(services, remotingEndpointSN, remoteOutboundConnectionSN, localOutboundConnectionSN);

        Object remoingEndpointService = dependencies.getService(remotingEndpointSN);
        assertNotNull(remoingEndpointService);

        Object remoteOutboundConnectionService = dependencies.getService(remoteOutboundConnectionSN);
        assertNotNull(remoteOutboundConnectionService);

        Object localOutboundConnectionService = dependencies.getService(localOutboundConnectionSN);
        assertNotNull(localOutboundConnectionService);
    }

    private AdditionalInitialization createRuntimeAdditionalInitialization(final boolean legacyParser) {
        return new AdditionalInitialization() {
            @Override
            protected void setupController(ControllerInitializer controllerInitializer) {
                controllerInitializer.addSocketBinding("test", 27258);
                controllerInitializer.addRemoteOutboundSocketBinding("dummy-outbound-socket", "localhost", 6799);
                controllerInitializer.addRemoteOutboundSocketBinding("other-outbound-socket", "localhost", 1234);
            }

            @Override
            protected void addExtraServices(ServiceTarget target) {
                //Needed for initialization of the RealmAuthenticationProviderService
                AbsolutePathService.addService(ServerEnvironment.CONTROLLER_TEMP_DIR, new File("target/temp" + System.currentTimeMillis()).getAbsolutePath(), target);
                if (!legacyParser) {
                    ServiceBuilder<?> builder = target.addService(IOServices.WORKER.append("default"));
                    Consumer<XnioWorker> workerConsumer = builder.provides(IOServices.WORKER.append("default"));
                    builder.setInstance(new WorkerService(workerConsumer, () -> Executors.newFixedThreadPool(1), Xnio.getInstance().createWorkerBuilder().setWorkerIoThreads(2)));
                    builder.install();                }
            }

            @Override
            protected void initializeExtraSubystemsAndModel(ExtensionRegistry extensionRegistry, Resource rootResource, ManagementResourceRegistration rootRegistration, RuntimeCapabilityRegistry capabilityRegistry) {
                super.initializeExtraSubystemsAndModel(extensionRegistry, rootResource, rootRegistration, capabilityRegistry);

                Map<String, Class> capabilities = new HashMap<>();
                capabilities.put(buildDynamicCapabilityName(IO_WORKER_CAPABILITY_NAME,
                        "default-remoting"), XnioWorker.class);

                if (legacyParser) {
                    // Deal with the fact that legacy parsers will add the io extension/subsystem
                    RemotingSubsystemTestUtil.registerIOExtension(extensionRegistry, rootRegistration);
                } else {
                    capabilities.put(buildDynamicCapabilityName(IO_WORKER_CAPABILITY_NAME, "default"), XnioWorker.class);
                }

                AdditionalInitialization.registerServiceCapabilities(capabilityRegistry, capabilities);
            }
        };
    }

    private static class CurrentConnectorAndController {
        final ServiceName endpointName;
        final ServiceName connectorName;
        final DependenciesRetrievalService dependencies;
        Object currentEndpoint;
        Object currentConnector;

        CurrentConnectorAndController(DependenciesRetrievalService dependencies, ServiceName endpointName, ServiceName connectorName) {
            this.dependencies = dependencies;
            this.endpointName = endpointName;
            this.connectorName = connectorName;
            this.currentEndpoint = loadCurrentEndpoint();
            this.currentConnector = loadCurrentConnector();
        }

        static CurrentConnectorAndController create(KernelServices services, ServiceName endpointName, ServiceName connectorName) {
            DependenciesRetrievalService deps = DependenciesRetrievalService.create(services, endpointName, connectorName);
            return new CurrentConnectorAndController(deps, endpointName, connectorName);
        }

        final Object loadCurrentEndpoint() {
            Object endpoint = dependencies.getService(endpointName);
            assertNotNull(endpoint);
            return endpoint;
        }

        final Object loadCurrentConnector() {
            Object connector = dependencies.getService(connectorName);
            assertNotNull(connector);
            return connector;
        }

        void updateCurrentEndpoint(final boolean equals) {
            this.currentEndpoint = checkStatus(this.currentEndpoint, loadCurrentEndpoint(), equals);
        }

        void updateCurrentConnector(final boolean equals) {
            this.currentConnector = checkStatus(this.currentConnector, loadCurrentConnector(), equals);
        }

        Object checkStatus(Object oldObject, Object newObject, boolean equals) {
            if (!equals) {
                assertNotSame(oldObject, newObject);
            } else {
                assertSame(oldObject, newObject);
            }
            return newObject;
        }
    }

}
