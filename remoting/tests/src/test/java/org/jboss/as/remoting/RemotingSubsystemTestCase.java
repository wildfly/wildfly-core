/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.remoting;

import static org.jboss.as.controller.capability.RuntimeCapability.buildDynamicCapabilityName;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.remoting.Capabilities.IO_WORKER_CAPABILITY_NAME;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.wildfly.extension.io.IOServices;
import org.wildfly.extension.io.WorkerService;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * @author Tomaz Cerar
 * @author <a href="opalka.richard@gmail.com">Richard Opalka</a>
 * @author Paul Ferraro
 */
@RunWith(Parameterized.class)
public class RemotingSubsystemTestCase extends AbstractRemotingSubsystemBaseTest {

    @Parameters
    public static Iterable<RemotingSubsystemSchema> parameters() {
        return EnumSet.complementOf(EnumSet.of(RemotingSubsystemSchema.VERSION_1_0, RemotingSubsystemSchema.VERSION_1_1, RemotingSubsystemSchema.VERSION_1_2, RemotingSubsystemSchema.VERSION_2_0, RemotingSubsystemSchema.VERSION_3_0));
    }

    private static final PathAddress ROOT_ADDRESS = PathAddress.pathAddress(SUBSYSTEM, RemotingExtension.SUBSYSTEM_NAME);
    private static final PathAddress CONNECTOR_ADDRESS = ROOT_ADDRESS.append("connector", "remoting-connector");

    /**
     * @param subsystemName
     * @param extension
     * @param testSchema
     * @param currentSchema
     */
    public RemotingSubsystemTestCase(RemotingSubsystemSchema schema) {
        super(schema);
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
