/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import static org.jboss.as.controller.capability.RuntimeCapability.buildDynamicCapabilityName;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_DEFAULTS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.remoting.Capabilities.IO_WORKER_CAPABILITY_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.AbsolutePathService;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.ControllerInitializer;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.remoting3.Endpoint;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.wildfly.extension.io.IOServices;
import org.wildfly.extension.io.WorkerService;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * @author Tomaz Cerar
 * @author <a href="opalka.richard@gmail.com">Richard Opalka</a>
 */
public class RemotingSubsystemTestCase extends AbstractRemotingSubsystemBaseTest {

    private static final PathAddress ROOT_ADDRESS = PathAddress.pathAddress(SUBSYSTEM, RemotingExtension.SUBSYSTEM_NAME);
    private static final PathAddress ENDPOINT_CONFIG_ADDRESS = ROOT_ADDRESS.append("configuration", "endpoint");
    private static final PathAddress CONNECTOR_ADDRESS = ROOT_ADDRESS.append("connector", "remoting-connector");
    private static final Map<String, ModelNode> ENDPOINT_CONFIG_TEST_DATA;
    static {
        Map<String, ModelNode> data = new LinkedHashMap<>();
        data.put("worker", new ModelNode("default-remoting"));
        for (AttributeDefinition ad : RemotingSubsystemRootResource.OPTIONS) {
            switch (ad.getType()) {
                case INT:
                    data.put(ad.getName(), new ModelNode(1));
                    break;
                case LONG:
                    data.put(ad.getName(), new ModelNode(1L));
                    break;
                case STRING:
                    data.put(ad.getName(), new ModelNode("abc"));
                    break;
                default:
                    throw new IllegalStateException();
            }
        }
        ENDPOINT_CONFIG_TEST_DATA = data;
    }

    /**
     * Tests that the outbound connections configured in the remoting subsytem are processed and services
     * are created for them
     *
     * @throws Exception
     */
    @Test
    public void testRuntime() throws Exception {
        KernelServices services = createKernelServicesBuilder(createRuntimeAdditionalInitialization())
                .setSubsystemXml(getSubsystemXml())
                .build();

        ServiceName remotingEndpointSN = RemotingServices.SUBSYSTEM_ENDPOINT;
        ServiceName remotingConnectorSN = RemotingServices.serverServiceName("remoting-connector");
        DependenciesRetrievalService dependencies = DependenciesRetrievalService.create(services, remotingEndpointSN, remotingConnectorSN);
        Endpoint endpointService = dependencies.getService(remotingEndpointSN);
        assertNotNull("Endpoint service was null", endpointService);
        assertNotNull(endpointService.getName());

        Object remotingConnector = dependencies.getService(remotingConnectorSN);
        assertNotNull("Remoting connector was null", remotingConnector);
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("remoting.xml");
    }

    /**
     * WFCORE-3327. Use the management API to add the subsystem, with the endpoint configuration done via
     * the deprecated /subsystem=remoting/configuration=endpoint resource.
     */
    @Ignore("WFCORE-4518")
    @Test
    public void testEndpointConfigurationViaDeprecatedChild() throws Exception {
        KernelServices services = createKernelServicesBuilder(createAdditionalInitialization())
                .build();

        // First, just the root resource with the endpoint unconfigured
        ModelNode rootAdd = Util.createAddOperation(ROOT_ADDRESS);
        services.executeForResult(rootAdd);
        checkEndpointSettings(services, Collections.emptyMap(), true);

        // Now, add the child resource with no config.
        ModelNode childAdd = Util.createAddOperation(ENDPOINT_CONFIG_ADDRESS);
        services.executeForResult(childAdd);
        checkEndpointSettings(services, Collections.emptyMap(), true);

        // Now add the child resource (which will work as it's forgiving) with new config
        childAdd = Util.createAddOperation(ENDPOINT_CONFIG_ADDRESS);
        for (Map.Entry<String, ModelNode> entry : ENDPOINT_CONFIG_TEST_DATA.entrySet()) {
            childAdd.get(entry.getKey()).set(entry.getValue());
        }
        services.executeForResult(childAdd);
        checkEndpointSettings(services, ENDPOINT_CONFIG_TEST_DATA, true);

        // Remove all so we can start over
        services.executeForResult(Util.createRemoveOperation(ROOT_ADDRESS));

        // Do the adds via a composite
        ModelNode composite = Util.createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        composite.get(STEPS).add(rootAdd);
        composite.get(STEPS).add(childAdd);
        services.executeForResult(composite);
        checkEndpointSettings(services, ENDPOINT_CONFIG_TEST_DATA, true);

        // Do an undefine-attribute for all children
        for (String attr : ENDPOINT_CONFIG_TEST_DATA.keySet()) {
            ModelNode op = Util.createEmptyOperation(UNDEFINE_ATTRIBUTE_OPERATION, ENDPOINT_CONFIG_ADDRESS);
            op.get(NAME).set(attr);
            services.executeForResult(op);
        }
        checkEndpointSettings(services, Collections.emptyMap(), false);

        // Do a write-attribute for all children
        for (Map.Entry<String, ModelNode> attr : ENDPOINT_CONFIG_TEST_DATA.entrySet()) {
            ModelNode op = Util.getWriteAttributeOperation(ENDPOINT_CONFIG_ADDRESS, attr.getKey(), attr.getValue());
            services.executeForResult(op);
        }
        checkEndpointSettings(services, ENDPOINT_CONFIG_TEST_DATA, false);

        // Remove the child config
        services.executeForResult(Util.createRemoveOperation(ENDPOINT_CONFIG_ADDRESS));
        checkEndpointSettings(services, Collections.emptyMap(), false);
    }

    /**
     * WFCORE-3327. Use the management API to add the subsystem, with the endpoint configuration done via
     * the root /subsystem=remoting resource.
     */
    @Ignore("WFCORE-4604")
    @Test
    public void testEndpointConfigurationViaSubsystemRoot() throws Exception {
        KernelServices services = createKernelServicesBuilder(createAdditionalInitialization())
                .build();

        // Add the root resource with the endpoint configured
        ModelNode rootAdd = Util.createAddOperation(ROOT_ADDRESS);
        for (Map.Entry<String, ModelNode> entry : ENDPOINT_CONFIG_TEST_DATA.entrySet()) {
            rootAdd.get(entry.getKey()).set(entry.getValue());
        }
        services.executeForResult(rootAdd);
        checkEndpointSettings(services, ENDPOINT_CONFIG_TEST_DATA, true);

        // Do an undefine-attribute for all children
        for (String attr : ENDPOINT_CONFIG_TEST_DATA.keySet()) {
            ModelNode op = Util.createEmptyOperation(UNDEFINE_ATTRIBUTE_OPERATION, ROOT_ADDRESS);
            op.get(NAME).set(attr);
            services.executeForResult(op);
        }
        checkEndpointSettings(services, Collections.emptyMap(), false);

        // Do a write-attribute for all children
        for (Map.Entry<String, ModelNode> attr : ENDPOINT_CONFIG_TEST_DATA.entrySet()) {
            ModelNode op = Util.getWriteAttributeOperation(ROOT_ADDRESS, attr.getKey(), attr.getValue());
            services.executeForResult(op);
        }
        checkEndpointSettings(services, ENDPOINT_CONFIG_TEST_DATA, false);
    }

    private static void checkEndpointSettings(KernelServices services, Map<String, ModelNode> nonDefaults, boolean expectDefaults) throws OperationFailedException {
        // Check the root
        ModelNode readResource = Util.createEmptyOperation(READ_RESOURCE_OPERATION, ROOT_ADDRESS);
        readResource.get(RECURSIVE).set(true);
        readResource.get(INCLUDE_DEFAULTS).set(expectDefaults);
        ModelNode result = services.executeForResult(readResource);
        checkEndpointSettings(result, nonDefaults, expectDefaults);

        // Check the child returned recursively
        assertTrue(result.toString(), result.hasDefined("configuration", "endpoint"));
        checkEndpointSettings(result.get("configuration", "endpoint"), nonDefaults, expectDefaults);

        // Check the child independently read
        readResource = Util.createEmptyOperation(READ_RESOURCE_OPERATION, ENDPOINT_CONFIG_ADDRESS);
        readResource.get(INCLUDE_DEFAULTS).set(expectDefaults);
        result = services.executeForResult(readResource);
        checkEndpointSettings(result, nonDefaults, expectDefaults);
    }

    private static void checkEndpointSettings(ModelNode node, Map<String, ModelNode> nonDefaults, boolean expectDefaults) {
        for (AttributeDefinition ad : RemotingEndpointResource.ATTRIBUTES.values()) {
            ModelNode correct = nonDefaults.get(ad.getName());
            if (expectDefaults) {
                correct = correct == null ? ad.getDefaultValue() : correct;
            }
            correct = correct == null ? new ModelNode() : correct;
            assertEquals(node.toString() + " wrong for " + ad.getName(), correct, node.get(ad.getName()));
        }
    }

    @Override
    protected String getSubsystemXml(String resource) throws IOException {
        return readResource(resource);
    }

    @Test
    public void testHttpConnectorValidationStepFail() throws Exception {
        KernelServices services = createKernelServicesBuilder(createAdditionalInitialization())
                .setSubsystemXml(getSubsystemXml("remoting-with-duplicate-http-connector.xml"))
                .build();

        Assert.assertFalse(services.isSuccessfulBoot());
    }

    @Test
    public void testHttpConnectorValidationStepSuccess() throws Exception {
        KernelServices services = createKernelServicesBuilder(createAdditionalInitialization())
                .setSubsystemXml(getSubsystemXml("remoting-without-duplicate-http-connector.xml"))
                .build();

        Assert.assertTrue(services.isSuccessfulBoot());
    }


    @Override
    protected void compareXml(String configId, String original, String marshalled) throws Exception {
        super.compareXml(configId, original, marshalled, true);
    }

    private AdditionalInitialization createRuntimeAdditionalInitialization() {
        return new AdditionalInitialization() {
            @Override
            protected void setupController(ControllerInitializer controllerInitializer) {
                controllerInitializer.addSocketBinding("remoting", 27258);
                controllerInitializer.addRemoteOutboundSocketBinding("dummy-outbound-socket", "localhost", 6799);
                controllerInitializer.addRemoteOutboundSocketBinding("other-outbound-socket", "localhost", 1234);
            }

            @Override
            protected void addExtraServices(ServiceTarget target) {
                //Needed for initialization of the RealmAuthenticationProviderService
                AbsolutePathService.addService(ServerEnvironment.CONTROLLER_TEMP_DIR, new File("target/temp" + System.currentTimeMillis()).getAbsolutePath(), target);
                ServiceBuilder<?> builder = target.addService(IOServices.WORKER.append("default"));
                Consumer<XnioWorker> workerConsumer = builder.provides(IOServices.WORKER.append("default"));
                builder.setInstance(new WorkerService(workerConsumer, () -> Executors.newFixedThreadPool(1), Xnio.getInstance().createWorkerBuilder().setWorkerIoThreads(2)));
                builder.setInitialMode(ServiceController.Mode.ON_DEMAND);
                builder.install();

                builder = target.addService(IOServices.WORKER.append("default-remoting"));
                workerConsumer = builder.provides(IOServices.WORKER.append("default-remoting"));
                builder.setInstance(new WorkerService(workerConsumer, () -> Executors.newFixedThreadPool(1), Xnio.getInstance().createWorkerBuilder().setWorkerIoThreads(2)));
                builder.setInitialMode(ServiceController.Mode.ON_DEMAND);
                builder.install();            }

            @Override
            protected void initializeExtraSubystemsAndModel(ExtensionRegistry extensionRegistry, Resource rootResource, ManagementResourceRegistration rootRegistration, RuntimeCapabilityRegistry capabilityRegistry) {
                super.initializeExtraSubystemsAndModel(extensionRegistry, rootResource, rootRegistration, capabilityRegistry);
                Map<String, Class> capabilities = new HashMap<>();
                capabilities.put(buildDynamicCapabilityName(IO_WORKER_CAPABILITY_NAME,
                        RemotingSubsystemRootResource.WORKER.getDefaultValue().asString()), XnioWorker.class);
                capabilities.put(buildDynamicCapabilityName(IO_WORKER_CAPABILITY_NAME,
                        "default-remoting"), XnioWorker.class);
                AdditionalInitialization.registerServiceCapabilities(capabilityRegistry, capabilities);
            }
        };
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCliAddProperty() throws Exception {
        KernelServices services = createKernelServicesBuilder(createRuntimeAdditionalInitialization())
                .setSubsystemXml(readResource("remoting-cli.xml")).build();

        ServiceName connectorSN = RemotingServices.serverServiceName("remoting-connector");
        DependenciesRetrievalService dependencies = DependenciesRetrievalService.create(services, connectorSN);
        Object remotingConnector = dependencies.getService(connectorSN);
        assertNotNull("Connector was null", remotingConnector);

        ModelNode validAdd = Util.createAddOperation(CONNECTOR_ADDRESS.append("property", "BROADCAST"));
        validAdd.get("value").set("aaa");
        try {
            services.executeForResult(validAdd);
            assertTrue(true);
        } catch (OperationFailedException ofe) {
            Assert.fail("This operation is supposed to pass");
        }

        ModelNode invalidAdd = Util.createAddOperation(CONNECTOR_ADDRESS.append("property", "aaa"));
        try {
            services.executeForResult(invalidAdd);
        } catch (OperationFailedException ofe) {
            assertTrue(true);
            assertTrue(ofe.getMessage().contains("WFLYRMT0028"));
        }
    }
}
