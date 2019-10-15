/*
Copyright 2016 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.controller;

import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.extension.ExtensionAddHandler;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.extension.ExtensionRegistryType;
import org.jboss.as.controller.extension.MutableRootResourceRegistrationProvider;
import org.jboss.as.controller.extension.RuntimeHostControllerInfoAccessor;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.persistence.AbstractConfigurationPersister;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

/**
 * Tests that removing non existing child resource fails when using {@link RestartParentResourceRemoveHandler}.
 */
public class RemoveNotEsistingResourceTestCase {


    private ServiceContainer container;

    @Before
    public void setupController() throws InterruptedException {
    }

    @After
    public void shutdownServiceContainer() {
        if (container != null) {
            container.shutdown();
            try {
                container.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            finally {
                container = null;
            }
        }
    }

    @Test
    public void testRemoveNonExistingResource() throws Exception {
        container = ServiceContainer.Factory.create("test");
        ServiceTarget target = container.subTarget();
        TestModelControllerService svc = new InterleavedSubsystemModelControllerService();
        target.addService(ServiceName.of("ModelController")).setInstance(svc).install();
        svc.awaitStartup(30, TimeUnit.SECONDS);
        ModelController controller = svc.getValue();

        final PathAddress attributePath = PathAddress.pathAddress(PathElement.pathElement("subsystem", "a"))
                .append(PathElement.pathElement(SUBMODEL_NAME, "nonExisting"));
        final ModelNode op = Util.createEmptyOperation(REMOVE, attributePath);
        ModelNode result = controller.execute(op, null, null, null);

        Assert.assertEquals("failed", result.get("outcome").asString());
    }

    @Test
    public void testRemoveExistingResource() throws Exception {
        container = ServiceContainer.Factory.create("test");
        ServiceTarget target = container.subTarget();
        TestModelControllerService svc = new InterleavedSubsystemModelControllerService();
        target.addService(ServiceName.of("ModelController")).setInstance(svc).install();
        svc.awaitStartup(30, TimeUnit.SECONDS);
        ModelController controller = svc.getValue();

        // create child node
        final PathAddress attributePath = PathAddress.pathAddress(PathElement.pathElement("subsystem", "a"))
                .append(PathElement.pathElement(SUBMODEL_NAME, "existing"));
        final ModelNode addChild = Util.createEmptyOperation(ADD, attributePath);
        controller.execute(addChild, null, null, null);

        // should be able to remove it
        final ModelNode op = Util.createEmptyOperation(REMOVE, attributePath);
        ModelNode result = controller.execute(op, null, null, null);

        Assert.assertEquals("success", result.get("outcome").asString());
    }


    public static final String SUBMODEL_NAME = "child";
    static final AttributeDefinition ATTRIBUTE_DEFINITION = new SimpleAttributeDefinition("attribute", ModelType.BOOLEAN, true);
    static final AttributeDefinition MODULE = new SimpleAttributeDefinition("module", ModelType.STRING, true);

    public static class InterleavedSubsystemModelControllerService extends TestModelControllerService {

        InterleavedSubsystemModelControllerService() {
            super(EmptySubsystemPerister.INSTANCE, new ControlledProcessState(true));
        }

        @Override
        protected void initModel(ManagementModel managementModel, Resource modelControllerResource) {
            ManagementResourceRegistration rootRegistration = managementModel.getRootResourceRegistration();
            GlobalOperationHandlers.registerGlobalOperations(rootRegistration, processType);

            GlobalNotifications.registerGlobalNotifications(rootRegistration, processType);

            SimpleResourceDefinition subsystemResource = new SimpleResourceDefinition(
                    PathElement.pathElement(EXTENSION),
                    new NonResolvingResourceDescriptionResolver(),
                    new FakeExtensionAddHandler(rootRegistration, getMutableRootResourceRegistrationProvider()),
                    ReloadRequiredRemoveStepHandler.INSTANCE
            ){
                @Override
                public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
                    super.registerAttributes(resourceRegistration);
                    resourceRegistration.registerReadOnlyAttribute(MODULE, null);
                }
            };
            rootRegistration.registerSubModel(subsystemResource);

        }

    }

    private static class FakeExtensionAddHandler extends ExtensionAddHandler {

        private final ManagementResourceRegistration rootRegistration;

        private FakeExtensionAddHandler(ManagementResourceRegistration rootRegistration, MutableRootResourceRegistrationProvider rootResourceRegistrationProvider) {
            super(new ExtensionRegistry(ProcessType.EMBEDDED_SERVER, new RunningModeControl(RunningMode.NORMAL), null, null, null, RuntimeHostControllerInfoAccessor.SERVER), false, ExtensionRegistryType.SERVER, rootResourceRegistrationProvider);
            this.rootRegistration = rootRegistration;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            Resource resource = context.createResource(PathAddress.EMPTY_ADDRESS);

            String module = context.getCurrentAddressValue();
            resource.getModel().get(MODULE.getName()).set(module);

            SimpleResourceDefinition subsystemResource = new SimpleResourceDefinition(
                    PathElement.pathElement(SUBSYSTEM, module),
                    new NonResolvingResourceDescriptionResolver(),
                    new AbstractAddStepHandler(),
                    ReloadRequiredRemoveStepHandler.INSTANCE
            ){

                @Override
                public void registerChildren(ManagementResourceRegistration resourceRegistration) {
                    super.registerChildren(resourceRegistration);
                    resourceRegistration.registerSubModel(new FakeSubmodelChild());
                }
            };
            rootRegistration.registerSubModel(subsystemResource);
        }
    }

    private static class FakeSubmodelChild extends SimpleResourceDefinition {

        public FakeSubmodelChild() {
            super(PathElement.pathElement(SUBMODEL_NAME),
                    new NonResolvingResourceDescriptionResolver(),
                    new AbstractAddStepHandler(),
                    new RestartParentResourceRemoveHandler("attr") {

                        @Override
                        protected ServiceName getParentServiceName(PathAddress parentAddress) {
                            return null;
                        }
                    });
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            super.registerAttributes(resourceRegistration);

            resourceRegistration.registerReadOnlyAttribute(ATTRIBUTE_DEFINITION, null);
        }
    }

    private static class EmptySubsystemPerister extends AbstractConfigurationPersister {

        private static final EmptySubsystemPerister INSTANCE = new EmptySubsystemPerister();

        private EmptySubsystemPerister() {
            super(null);
        }

        /** {@inheritDoc} */
        @Override
        public PersistenceResource store(final ModelNode model, Set<PathAddress> affectedAddresses) {
            return NullPersistenceResource.INSTANCE;
        }

        /** {@inheritDoc} */
        @Override
        public List<ModelNode> load() {
            final List<ModelNode> bootOps = new ArrayList<ModelNode>();
            final ModelNode addrAE = new ModelNode().setEmptyList().add(EXTENSION, "a");
            final ModelNode addrAS = new ModelNode().setEmptyList().add(SUBSYSTEM, "a");
            bootOps.add(Util.getEmptyOperation(ADD, addrAE));
            bootOps.add(Util.getEmptyOperation(ADD, addrAS));
            return bootOps;
        }

        private static class NullPersistenceResource implements PersistenceResource {

            private static final NullPersistenceResource INSTANCE = new NullPersistenceResource();

            @Override
            public void commit() {
            }

            @Override
            public void rollback() {
            }
        }
    }
}
