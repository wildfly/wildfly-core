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

package org.jboss.as.server.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ANY_ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.AbstractControllerService;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.DelegatingResourceDefinition;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.ModelControllerClientFactory;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.ManagementSecurityIdentitySupplier;
import org.jboss.as.controller.audit.AuditLogger;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.persistence.AbstractConfigurationPersister;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.resource.InterfaceDefinition;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.DeploymentFileRepository;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.Services;
import org.jboss.as.server.controller.resources.ServerRootResourceDefinition;
import org.jboss.as.server.parsing.StandaloneXml;
import org.jboss.as.server.services.net.NetworkInterfaceService;
import org.jboss.as.version.ProductConfig;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.modules.Module;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.vfs.VirtualFile;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Basic server controller unit test.
 *
 * @author Emanuel Muckenhuber
 */
public class InterfaceManagementUnitTestCase {

    private final ServiceContainer container = ServiceContainer.Factory.create();
    private ModelController controller;
    private ModelControllerClientFactory clientFactory;
    private volatile boolean dependentStarted;

    @Before
    public void before() throws Exception {
        dependentStarted = false;
        final ServiceTarget target = container.subTarget();
        final ExtensionRegistry extensionRegistry = ExtensionRegistry.builder(ProcessType.STANDALONE_SERVER).build();
        final StringConfigurationPersister persister = new StringConfigurationPersister(Collections.<ModelNode>emptyList(), new StandaloneXml(null, null, extensionRegistry));
        extensionRegistry.setWriterRegistry(persister);
        final ControlledProcessState processState = new ControlledProcessState(true);
        final ModelControllerService svc = new ModelControllerService(processState, persister, new ServerDelegatingResourceDefinition());
        final ServiceBuilder<ModelController> builder = target.addService(Services.JBOSS_SERVER_CONTROLLER, svc);
        builder.install();

        // Create demand for the ON_DEMAND interface service we'll be adding so we can validate what happens in start()
        Service<Void> dependentService = new Service<Void>() {
            @Override
            public void start(StartContext context) throws StartException {
                dependentStarted = true;
            }

            @Override
            public void stop(StopContext context) {
                dependentStarted = false;
            }

            @Override
            public Void getValue() throws IllegalStateException, IllegalArgumentException {
                return null;
            }
        };
        final ServiceBuilder sb = target.addService(ServiceName.JBOSS.append("interface", "management", "test", "case", "dependent"), dependentService);
        sb.requires(NetworkInterfaceService.JBOSS_NETWORK_INTERFACE.append("test"));
        ServiceController<Void> dependentController = sb.install();

        svc.latch.await(20, TimeUnit.SECONDS);
        this.controller = svc.getValue();
        this.clientFactory = svc.getModelControllerClientFactory();

        container.awaitStability(20, TimeUnit.SECONDS);
        Assert.assertTrue(dependentController.getState() == ServiceController.State.DOWN
                && dependentController.getUnavailableDependencies().size() > 0);
    }

    @After
    public void after() {
        container.shutdown();
    }

    @Test
    public void testInterfacesAlternatives() throws IOException {
        final ModelControllerClient client = clientFactory.createClient(Executors.newCachedThreadPool());
        final ModelNode base = new ModelNode();
        base.get(ModelDescriptionConstants.OP).set("add");
        base.get(ModelDescriptionConstants.OP_ADDR).add("interface", "test");
        {
            // any-address is not valid with the normal criteria
            final ModelNode operation = base.clone();
            operation.get(ANY_ADDRESS).set(true);
            populateCritieria(operation, Nesting.TOP);
            executeForNonServiceFailure(client, operation);
        }
        // Disabled. See https://github.com/wildfly/wildfly-core/commit/ae0ca95c42b481ef519246b9a6eab2b50c48472e
//        {
//            // AS7-2685 had a notion of disallowing LOOPBACK and LINK_LOCAL_ADDRESS
//            final ModelNode operation = base.clone();
//            populateCritieria(operation, Nesting.TOP, InterfaceDefinition.LOOPBACK, InterfaceDefinition.LINK_LOCAL_ADDRESS);
//            executeForNonServiceFailure(client, operation);
//        }
        {
            // The full set of normal criteria is ok, although it won't resolve properly
            final ModelNode operation = base.clone();
            populateCritieria(operation, Nesting.TOP);
            executeForServiceFailure(client, operation);
        }
    }

    @Test
    public void testUpdateInterface() throws IOException {
        final ModelControllerClient client = clientFactory.createClient(Executors.newCachedThreadPool());
        final ModelNode address = new ModelNode();
        address.add("interface", "test");
        {
            final ModelNode operation = new ModelNode();
            operation.get(ModelDescriptionConstants.OP).set("add");
            operation.get(ModelDescriptionConstants.OP_ADDR).set(address);
            operation.get(ANY_ADDRESS).set(true);

            executeForResult(client, operation);
            final ModelNode resource = readResource(client, operation.get(ModelDescriptionConstants.OP_ADDR));
            Assert.assertTrue(resource.get(ANY_ADDRESS).asBoolean());
        }
        {
            final ModelNode composite = new ModelNode();
            composite.get(ModelDescriptionConstants.OP).set("composite");
            composite.get(ModelDescriptionConstants.OP_ADDR).setEmptyList();
            final ModelNode one = composite.get(ModelDescriptionConstants.STEPS).add();
            one.get(ModelDescriptionConstants.OP).set("write-attribute");
            one.get(ModelDescriptionConstants.OP_ADDR).set(address);
            one.get(ModelDescriptionConstants.NAME).set(ANY_ADDRESS);
            one.get(ModelDescriptionConstants.VALUE);

            final ModelNode two = composite.get(ModelDescriptionConstants.STEPS).add();
            two.get(ModelDescriptionConstants.OP).set("write-attribute");
            two.get(ModelDescriptionConstants.OP_ADDR).set(address);
            two.get(ModelDescriptionConstants.NAME).set("inet-address");
            two.get(ModelDescriptionConstants.VALUE).set("127.0.0.1");

            executeForResult(client, composite);
            final ModelNode resource = readResource(client, address);
            Assert.assertFalse(resource.hasDefined(ANY_ADDRESS));
            Assert.assertEquals("127.0.0.1", resource.get("inet-address").asString());
        }
    }

    @Test
    public void testComplexInterface() throws IOException {

        final ModelNode operation = new ModelNode();
        operation.get(ModelDescriptionConstants.OP).set("add");
        operation.get(ModelDescriptionConstants.OP_ADDR).add("interface", "test");
        // This won't be resolvable with the runtime layer enabled
        populateCritieria(operation, Nesting.TOP,
                InterfaceDefinition.LOOPBACK);
        populateCritieria(operation.get("not"), Nesting.NOT,
                InterfaceDefinition.PUBLIC_ADDRESS,
                InterfaceDefinition.LINK_LOCAL_ADDRESS,
                InterfaceDefinition.SITE_LOCAL_ADDRESS,
                InterfaceDefinition.VIRTUAL,
                InterfaceDefinition.UP,
                InterfaceDefinition.MULTICAST,
                InterfaceDefinition.LOOPBACK_ADDRESS,
                InterfaceDefinition.POINT_TO_POINT);
        populateCritieria(operation.get("any"), Nesting.ANY);

        final ModelControllerClient client = clientFactory.createClient(Executors.newCachedThreadPool());

        executeForServiceFailure(client, operation);
    }

    protected void populateCritieria(final ModelNode model, final Nesting nesting, final AttributeDefinition...excluded) {
        Set<AttributeDefinition> excludedCriteria = new HashSet<AttributeDefinition>(Arrays.asList(excluded));
        for(final AttributeDefinition def : InterfaceDefinition.NESTED_ATTRIBUTES) {

            if (excludedCriteria.contains(def)) {
                continue;
            }

            final ModelNode node = model.get(def.getName());
            if(def.getType() == ModelType.BOOLEAN) {
                node.set(true);
            } else if (def == InterfaceDefinition.INET_ADDRESS || def == InterfaceDefinition.LOOPBACK_ADDRESS) {
                if (nesting == Nesting.ANY && def == InterfaceDefinition.INET_ADDRESS) {
                    node.add("127.0.0.1");
                } else if (nesting == Nesting.NOT && def == InterfaceDefinition.INET_ADDRESS) {
                    node.add("10.0.0.1");
                } else {
                    node.set("127.0.0.1");
                }
            } else if (def == InterfaceDefinition.NIC || def == InterfaceDefinition.NIC_MATCH) {
                if (nesting == Nesting.ANY) {
                    node.add("lo");
                } else if (nesting == Nesting.NOT) {
                    node.add("en3");
                } else {
                    node.set("lo");
                }
            } else if (def == InterfaceDefinition.SUBNET_MATCH) {
                if (nesting == Nesting.ANY) {
                    node.add("127.0.0.1/24");
                } else if (nesting == Nesting.NOT) {
                    node.add("10.0.0.1/24");
                } else {
                    node.set("127.0.0.0/24");
                }
            }
        }
    }

    private static class ModelControllerService extends AbstractControllerService {

        final CountDownLatch latch = new CountDownLatch(1);
        final StringConfigurationPersister persister;
        final ControlledProcessState processState;
        final ServerDelegatingResourceDefinition rootResourceDefinition;
        final ServerEnvironment environment;
        final ExtensionRegistry extensionRegistry;
        final CapabilityRegistry capabilityRegistry;
        volatile ManagementResourceRegistration rootRegistration;
        volatile Exception error;


        ModelControllerService(final ControlledProcessState processState, final StringConfigurationPersister persister, final ServerDelegatingResourceDefinition rootResourceDefinition) {
            super(ProcessType.EMBEDDED_SERVER, new RunningModeControl(RunningMode.ADMIN_ONLY), persister, processState, rootResourceDefinition, null, ExpressionResolver.TEST_RESOLVER,
                    AuditLogger.NO_OP_LOGGER, new DelegatingConfigurableAuthorizer(), new ManagementSecurityIdentitySupplier(), new CapabilityRegistry(true));
            this.persister = persister;
            this.processState = processState;
            this.rootResourceDefinition = rootResourceDefinition;

            Properties properties = new Properties();
            properties.put("jboss.home.dir", System.getProperty("basedir", ".") + File.separatorChar + "target");

            final String hostControllerName = "hostControllerName"; // Host Controller name may not be null when in a managed domain
            environment = new ServerEnvironment(hostControllerName, properties, new HashMap<String, String>(), null, null,
                    ServerEnvironment.LaunchType.DOMAIN, null, ProductConfig.fromFilesystemSlot(Module.getBootModuleLoader(), ".", properties), false);
            extensionRegistry = ExtensionRegistry.builder(ProcessType.STANDALONE_SERVER).build();

            capabilityRegistry = new CapabilityRegistry(processType.isServer());
        }

        @Override
        protected void initModel(ManagementModel managementModel, Resource modelControllerResource) {
            this.rootRegistration = managementModel.getRootResourceRegistration();
        }

        @Override
        public void start(StartContext context) throws StartException {
            rootResourceDefinition.setDelegate(new ServerRootResourceDefinition(MockRepository.INSTANCE,
                    persister, environment, processState, null, extensionRegistry, false, MOCK_PATH_MANAGER, null,
                    authorizer, securityIdentitySupplier, AuditLogger.NO_OP_LOGGER, getMutableRootResourceRegistrationProvider(), getBootErrorCollector(), capabilityRegistry));
            super.start(context);
        }

        @Override
        protected ModelControllerClientFactory getModelControllerClientFactory() {
            return super.getModelControllerClientFactory();
        }

        @Override
        protected void bootThreadDone() {
            super.bootThreadDone();
            latch.countDown();
        }
    }

    static final class ServerDelegatingResourceDefinition extends DelegatingResourceDefinition {
        @Override
        public void setDelegate(ResourceDefinition delegate) {
            super.setDelegate(delegate);
        }
    }

    static class StringConfigurationPersister extends AbstractConfigurationPersister {

        private final List<ModelNode> bootOperations;
        volatile String marshalled;

        public StringConfigurationPersister(List<ModelNode> bootOperations, XMLElementWriter<ModelMarshallingContext> rootDeparser) {
            super(rootDeparser);
            this.bootOperations = bootOperations;
        }

        @Override
        public PersistenceResource store(ModelNode model, Set<PathAddress> affectedAddresses)
                throws ConfigurationPersistenceException {
            return new StringPersistenceResource(model, this);
        }

        @Override
        public List<ModelNode> load() throws ConfigurationPersistenceException {
            return bootOperations;
        }

        private class StringPersistenceResource implements PersistenceResource {

            private byte[] bytes;
            private final AbstractConfigurationPersister persister;

            StringPersistenceResource(final ModelNode model, final AbstractConfigurationPersister persister) throws ConfigurationPersistenceException {
                this.persister = persister;
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024 * 8);
                try {
                    try {
                        persister.marshallAsXml(model, output);
                    } finally {
                        try {
                            output.close();
                        } catch (Exception ignore) {
                        }
                        bytes = output.toByteArray();
                    }
                } catch (Exception e) {
                    throw new ConfigurationPersistenceException("Failed to marshal configuration", e);
                }
            }

            @Override
            public void commit() {
                StringConfigurationPersister.this.marshalled = new String(bytes, StandardCharsets.UTF_8);
            }

            @Override
            public void rollback() {
                marshalled = null;
            }
        }
    }

    static ModelNode readResource(final ModelControllerClient client, final ModelNode address) {
        final ModelNode operation = new ModelNode();
        operation.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_RESOURCE_OPERATION);
        operation.get(ModelDescriptionConstants.OP_ADDR).set(address);
        return executeForResult(client, operation);
    }

    /**
     * Assert that the operation failed, but not with the failure message that indicates a service start problem.
     * Use this to check that problems that should be detected in the OSH and not in the service are properly
     * detected.
     *
     * @param client the client to use to execute the operation
     * @param operation the operation to execute
     */
    private static void executeForNonServiceFailure(final ModelControllerClient client, final ModelNode operation) {
        try {
            final ModelNode result = client.execute(operation);
            if (! result.hasDefined("outcome") && ! ModelDescriptionConstants.FAILED.equals(result.get("outcome").asString())) {
                Assert.fail("Operation outcome is " + result.get("outcome").asString());
            }
            System.out.println("Failure for " + operation + "\n is:\n" + result);
            Assert.assertFalse(result.toString(), result.get(ModelDescriptionConstants.FAILURE_DESCRIPTION).toString().contains(ControllerLogger.MGMT_OP_LOGGER.failedServices()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Assert that the operation failed, but only with the failure message that indicates a service start problem.
     * Use this for instead of executeFoResult in tests that use criteria that may not be resolvable on a real machine,
     * but which are not explicitly disallowed in the model. The inability to resolve a matching interface will lead to the service start problem.
     *
     * @param client the client to use to execute the operation
     * @param operation the operation to execute
     */
    private void executeForServiceFailure(final ModelControllerClient client, final ModelNode operation) {
        try {
            final ModelNode result = client.execute(operation);
            if (result.hasDefined(OUTCOME) && ! ModelDescriptionConstants.FAILED.equals(result.get(OUTCOME).asString())) {
                // The dependent service we add in before() should have demanded the ON_DEMAND interface service.
                // And that should have failed start. So if the op succeeded but didn't start the dependent, that's
                // a clue as to why the op succeeded; i.e. the demand didn't get picked up. This is basically
                // a diagnostic for WFCORE-2630
                Assert.assertTrue("Adding interface service did not trigger start of dependent service", dependentStarted);
                // If we get here the interface service must have started when it shouldn't have
                Assert.fail("Operation outcome is " + result.get(OUTCOME).asString());
            }
            System.out.println("Failure for " + operation + "\n is:\n" + result);
            Assert.assertTrue(result.toString(), result.get(ModelDescriptionConstants.FAILURE_DESCRIPTION).toString().contains(ControllerLogger.MGMT_OP_LOGGER.failedServices()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static ModelNode executeForResult(final ModelControllerClient client, final ModelNode operation) {
        try {
            final ModelNode result = client.execute(operation);
            if (result.hasDefined("outcome") && "success".equals(result.get("outcome").asString())) {
                return result.get("result");
            } else {
                Assert.fail("Operation outcome is " + result.get("outcome").asString() + " " + result.get("failure-description"));
                throw new RuntimeException(); // not reached
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static final class MockRepository implements ContentRepository, DeploymentFileRepository {

        static MockRepository INSTANCE = new MockRepository();

        @Override
        public File[] getDeploymentFiles(ContentReference reference) {
            return null;
        }

        @Override
        public File getDeploymentRoot(ContentReference reference) {
            return null;
        }

        @Override
        public void deleteDeployment(ContentReference reference) {
        }

        @Override
        public byte[] addContent(InputStream stream) throws IOException {
            return null;
        }

        @Override
        public boolean syncContent(ContentReference reference) {
            return hasContent(reference.getHash());
        }

        @Override
        public VirtualFile getContent(byte[] hash) {
            return null;
        }

        @Override
        public boolean hasContent(byte[] hash) {
            return false;
        }

        @Override
        public void removeContent(ContentReference reference) {
        }

        @Override
        public void addContentReference(ContentReference reference) {
        }

        @Override
        public Map<String, Set<String>> cleanObsoleteContent() {
            return null;
        }
    }

    private static PathManagerService MOCK_PATH_MANAGER = new PathManagerService() {

    };

    private enum Nesting {
        TOP,
        ANY,
        NOT
    }
}
