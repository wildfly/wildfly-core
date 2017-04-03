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
import static org.jboss.as.remoting.RemotingSubsystemTestUtil.DEFAULT_ADDITIONAL_INITIALIZATION;
import static org.jboss.as.remoting.RemotingSubsystemTestUtil.HC_ADDITIONAL_INITIALIZATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.AbsolutePathService;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.ControllerInitializer;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceNotFoundException;
import org.jboss.msc.service.ServiceTarget;
import org.junit.Test;
import org.wildfly.extension.io.IOServices;
import org.wildfly.extension.io.WorkerService;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="opalka.richard@gmail.com">Richard Opalka</a>
 */
public class RemotingLegacySubsystemTestCase extends AbstractSubsystemBaseTest {

    public RemotingLegacySubsystemTestCase() {
        super(RemotingExtension.SUBSYSTEM_NAME, new RemotingExtension());
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return DEFAULT_ADDITIONAL_INITIALIZATION;
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
    public void testSubsystemWithThreadParameters() throws Exception {
        standardSubsystemTest("remoting-with-threads.xml", null, true, HC_ADDITIONAL_INITIALIZATION);
    }

    @Test
    public void testSubsystemWithThreadAttributeChange() throws Exception {
        KernelServices services = createKernelServicesBuilder(HC_ADDITIONAL_INITIALIZATION)
                .setSubsystemXmlResource("remoting-with-threads.xml")
                .build();

        updateAndCheckThreadAttribute(services, CommonAttributes.WORKER_READ_THREADS, 5, 6);
        updateAndCheckThreadAttribute(services, CommonAttributes.WORKER_TASK_CORE_THREADS, 6, 2);
        updateAndCheckThreadAttribute(services, CommonAttributes.WORKER_TASK_KEEPALIVE, 7, 3);
        updateAndCheckThreadAttribute(services, CommonAttributes.WORKER_TASK_LIMIT, 8, 4);
        updateAndCheckThreadAttribute(services, CommonAttributes.WORKER_TASK_MAX_THREADS, 9, 5);
        updateAndCheckThreadAttribute(services, CommonAttributes.WORKER_WRITE_THREADS, 10, 6);
    }

    private void updateAndCheckThreadAttribute(KernelServices services, String attrName, int before, int after) throws Exception {
        assertEquals(before, services.readWholeModel().get(SUBSYSTEM, RemotingExtension.SUBSYSTEM_NAME, attrName).asInt());
        ModelNode write = new ModelNode();
        write.get(OPERATION_HEADERS, ALLOW_RESOURCE_SERVICE_RESTART).set(true);
        write.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        write.get(OP_ADDR).add(SUBSYSTEM, RemotingExtension.SUBSYSTEM_NAME);
        write.get(NAME).set(attrName);
        write.get(VALUE).set(after);
        ModelNode result = services.executeOperation(write);
        assertFalse(result.get(FAILURE_DESCRIPTION).toString(), result.hasDefined(FAILURE_DESCRIPTION));

        assertEquals(after, services.readWholeModel().get(SUBSYSTEM, RemotingExtension.SUBSYSTEM_NAME, attrName).asInt());
    }

    @Test
    public void testSubsystem12WithConnector() throws Exception {

        KernelServices services = createKernelServicesBuilder(createRuntimeAdditionalInitialization(true))
                .setSubsystemXmlResource("remoting12-with-connector.xml").build();

        ServiceName connectorServiceName = RemotingServices.serverServiceName("test-connector");
        ServiceController<?> connectorService = services.getContainer().getRequiredService(connectorServiceName);
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


        ServiceController<?> endPointService = services.getContainer().getRequiredService(RemotingServices.SUBSYSTEM_ENDPOINT);
        assertNotNull(endPointService);

        ServiceName connectorServiceName = RemotingServices.serverServiceName("test-connector");
        ServiceController<?> connectorService = services.getContainer().getRequiredService(connectorServiceName);
        assertNotNull(connectorService);

        ModelNode model = services.readWholeModel();
        ModelNode subsystem = model.require(SUBSYSTEM).require(RemotingExtension.SUBSYSTEM_NAME);
        for (AttributeDefinition ad : RemotingSubsystemRootResource.ATTRIBUTES) {
            ModelNode dflt = ad.getDefaultValue();
            assertEquals(ad.getName(), dflt == null ? new ModelNode() : dflt, subsystem.require(ad.getName()));
        }
        ModelNode endpoint = subsystem.get(RemotingEndpointResource.ENDPOINT_PATH.getKey(), RemotingEndpointResource.ENDPOINT_PATH.getValue());
        for (AttributeDefinition ad : RemotingEndpointResource.ATTRIBUTES) {
            ModelNode dflt = ad.getDefaultValue();
            assertEquals(ad.getName(), dflt == null ? new ModelNode() : dflt, endpoint.require(ad.getName()));
        }


        ModelNode connector = subsystem.require(CommonAttributes.CONNECTOR).require("test-connector");
        assertEquals(1, connector.require(CommonAttributes.PROPERTY).require("org.xnio.Options.WORKER_ACCEPT_THREADS").require(CommonAttributes.VALUE).asInt());
    }

    @Test
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

        ServiceController<?> endPointService = services.getContainer().getRequiredService(RemotingServices.SUBSYSTEM_ENDPOINT);
        assertNotNull("Endpoint service was null", endPointService);

        final String remoteOutboundConnectionName = "remote-conn1";
        ServiceName remoteOutboundConnectionServiceName = RemoteOutboundConnectionService.REMOTE_OUTBOUND_CONNECTION_BASE_SERVICE_NAME.append(remoteOutboundConnectionName);
        ServiceController<?> remoteOutboundConnectionService = services.getContainer().getRequiredService(remoteOutboundConnectionServiceName);
        assertNotNull("Remote outbound connection service for outbound connection:" + remoteOutboundConnectionName + " was null", remoteOutboundConnectionService);

        final String localOutboundConnectionName = "local-conn1";
        ServiceName localOutboundConnectionServiceName = LocalOutboundConnectionService.LOCAL_OUTBOUND_CONNECTION_BASE_SERVICE_NAME.append(localOutboundConnectionName);
        ServiceController<?> localOutboundConnectionService = services.getContainer().getRequiredService(localOutboundConnectionServiceName);
        assertNotNull("Local outbound connection service for outbound connection:" + localOutboundConnectionName + " was null", localOutboundConnectionService);
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("remoting.xml");
    }

    @Override
    protected String getSubsystemXml(String resource) throws IOException {
        return readResource(resource);
    }

    @Override
    protected void compareXml(String configId, String original, String marshalled) throws Exception {
        super.compareXml(configId, original, marshalled, true);
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
                    target.addService(IOServices.WORKER.append("default"), new WorkerService(Xnio.getInstance().createWorkerBuilder().setWorkerIoThreads(2)))
                            .setInitialMode(ServiceController.Mode.ACTIVE)
                            .install();
                }
            }

            @Override
            protected void initializeExtraSubystemsAndModel(ExtensionRegistry extensionRegistry, Resource rootResource, ManagementResourceRegistration rootRegistration, RuntimeCapabilityRegistry capabilityRegistry) {
                super.initializeExtraSubystemsAndModel(extensionRegistry, rootResource, rootRegistration, capabilityRegistry);

                Map<String, Class> capabilities = new HashMap<>();
                capabilities.put(buildDynamicCapabilityName(RemotingSubsystemRootResource.IO_WORKER_CAPABILITY,
                        "default-remoting"), XnioWorker.class);

                if (legacyParser) {
                    // Deal with the fact that legacy parsers will add the io extension/subsystem
                    RemotingSubsystemTestUtil.registerIOExtension(extensionRegistry, rootRegistration);
                } else {
                    capabilities.put(buildDynamicCapabilityName(RemotingSubsystemRootResource.IO_WORKER_CAPABILITY,
                            RemotingEndpointResource.WORKER.getDefaultValue().asString()), XnioWorker.class);
                }

                AdditionalInitialization.registerServiceCapabilities(capabilityRegistry, capabilities);
            }
        };
    }

    private static class CurrentConnectorAndController {
        final KernelServices services;
        final ServiceName endpointName;
        final ServiceName connectorName;
        Object currentEndpoint;
        Object currentConnector;

        CurrentConnectorAndController(KernelServices services, ServiceName endpointName, ServiceName connectorName) {
            this.services = services;
            this.endpointName = endpointName;
            this.connectorName = connectorName;
            this.currentEndpoint = loadCurrentEndpoint(services);
            this.currentConnector = loadCurrentConnector(services);
        }

        static CurrentConnectorAndController create(KernelServices services, ServiceName endpointName, ServiceName connectorName) {
            return new CurrentConnectorAndController(services, endpointName, connectorName);
        }

        final Object loadCurrentEndpoint(KernelServices services) {
            ServiceController<?> endPointService = services.getContainer().getRequiredService(endpointName);
            assertNotNull(endPointService);
            Object endpoint = endPointService.getValue();
            assertNotNull(endpoint);
            return endpoint;
        }

        final Object loadCurrentConnector(KernelServices services) {
            ServiceController<?> connectorService = services.getContainer().getRequiredService(connectorName);
            assertNotNull(connectorService);
            Object connector = connectorService.getValue();
            assertNotNull(connector);
            return connector;
        }

        void updateCurrentEndpoint(final boolean equals) throws Exception {
            this.currentEndpoint = checkStatus(this.currentEndpoint, loadCurrentEndpoint(services), equals);
        }

        void updateCurrentConnector(final boolean equals) throws Exception {
            this.currentConnector = checkStatus(this.currentConnector, loadCurrentConnector(services), equals);
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
