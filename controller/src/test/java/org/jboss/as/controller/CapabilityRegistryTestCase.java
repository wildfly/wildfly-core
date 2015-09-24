/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.operations.validation.AbstractParameterValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.test.AbstractControllerTestBase;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xnio.Pool;
import org.xnio.XnioWorker;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

/**
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public class CapabilityRegistryTestCase extends AbstractControllerTestBase {

    private static final SimpleAttributeDefinition ad = new SimpleAttributeDefinitionBuilder("test", ModelType.STRING, true)
            .build();

    private static final SimpleAttributeDefinition other = new SimpleAttributeDefinitionBuilder("other", ModelType.STRING, true)
            .setValidator(new AbstractParameterValidator() {
                @Override
                public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
                    if (value.asString().equals("break")){
                        throw new OperationFailedException("we need to break");
                    }
                }
            })
            .build();

    private static final RuntimeCapability<Void> IO_WORKER_RUNTIME_CAPABILITY =
            RuntimeCapability.Builder.of("org.wildfly.io.worker", false, XnioWorker.class).build();
    private static final RuntimeCapability<Void> IO_POOL_RUNTIME_CAPABILITY = RuntimeCapability.Builder.of("org.wildfly.io.buffer-pool", false, Pool.class).build();
    private static final RuntimeCapability<Void> TEST_CAPABILITY1 = RuntimeCapability.Builder.of("org.wildfly.test.capability1", true, Void.class).build();
    private static final RuntimeCapability<Void> TEST_CAPABILITY2 = RuntimeCapability.Builder.of("org.wildfly.test.capability2", true, Void.class).build();
    private static final RuntimeCapability<Void> TEST_CAPABILITY3 = RuntimeCapability.Builder.of("org.wildfly.test.capability3", true, Void.class).build();

    private static PathAddress TEST_ADDRESS1 = PathAddress.pathAddress("subsystem", "test1");
    private static PathAddress TEST_ADDRESS2 = PathAddress.pathAddress("subsystem", "test2");
    private static PathAddress TEST_ADDRESS3 = PathAddress.pathAddress("sub", "resource");
    private static PathAddress TEST_ADDRESS4 = PathAddress.pathAddress("subsystem", "test4");

    private static ResourceDefinition TEST_RESOURCE1 = ResourceBuilder.Factory.create(TEST_ADDRESS1.getElement(0),
            new NonResolvingResourceDescriptionResolver())
            .setAddOperation(new AbstractAddStepHandler(new HashSet<>(Arrays.asList(IO_POOL_RUNTIME_CAPABILITY, IO_WORKER_RUNTIME_CAPABILITY)), ad, other))
            .setRemoveOperation(new ReloadRequiredRemoveStepHandler(IO_POOL_RUNTIME_CAPABILITY, IO_WORKER_RUNTIME_CAPABILITY))
            .addReadWriteAttribute(ad, null, new ReloadRequiredWriteAttributeHandler(ad))
            .addReadWriteAttribute(other, null, new ReloadRequiredWriteAttributeHandler(other))
            .addOperation(new SimpleOperationDefinition("add-cap",
                            new NonResolvingResourceDescriptionResolver()),
                    (context, operation) -> {
                        ManagementResourceRegistration mrr = context.getResourceRegistrationForUpdate();
                        mrr.registerCapability(TEST_CAPABILITY1);
                        Assert.assertEquals(3, mrr.getCapabilities().size());
                    })
            .addCapability(IO_POOL_RUNTIME_CAPABILITY)
            .addCapability(IO_WORKER_RUNTIME_CAPABILITY)
            .build();

    private static final ResourceDefinition SUB_RESOURCE = ResourceBuilder.Factory.create(TEST_ADDRESS3.getElement(0),
                new NonResolvingResourceDescriptionResolver())
                .setAddOperation(new AbstractAddStepHandler(Collections.singleton(TEST_CAPABILITY3)))
                .setRemoveOperation(new ReloadRequiredRemoveStepHandler(TEST_CAPABILITY3))
                .addReadWriteAttribute(ad, null, new ReloadRequiredWriteAttributeHandler(ad))
                .addCapability(TEST_CAPABILITY3)
                .build();


    private static ResourceDefinition TEST_RESOURCE2 = ResourceBuilder.Factory.create(TEST_ADDRESS2.getElement(0),
            new NonResolvingResourceDescriptionResolver())
            .setAddOperation(new AbstractAddStepHandler(Collections.singleton(TEST_CAPABILITY2), ad, other))
            .setRemoveOperation(new ReloadRequiredRemoveStepHandler(TEST_CAPABILITY2))
            .addReadWriteAttribute(ad, null, new ReloadRequiredWriteAttributeHandler(ad))
            .addReadWriteAttribute(other, null, new ReloadRequiredWriteAttributeHandler(other))
            .addCapability(TEST_CAPABILITY2)
            .addOperation(new SimpleOperationDefinition("add-sub-resource",
                            new NonResolvingResourceDescriptionResolver()),
                    (context, operation) -> {
                        ManagementResourceRegistration mrr = context.getResourceRegistrationForUpdate();
                        mrr.registerSubModel(SUB_RESOURCE);
                    })

            .addOperation(new SimpleOperationDefinition("remove-sub-resource",
                            new NonResolvingResourceDescriptionResolver()),
                    (context, operation) -> {
                        ManagementResourceRegistration mrr = context.getResourceRegistrationForUpdate();
                        mrr.unregisterSubModel(SUB_RESOURCE.getPathElement());
                    })
            .build();

    private static ResourceDefinition TEST_RESOURCE4 = ResourceBuilder.Factory.create(TEST_ADDRESS4.getElement(0),
            new NonResolvingResourceDescriptionResolver())
            .setAddOperation(new AbstractAddStepHandler(new HashSet<>(Arrays.asList(IO_POOL_RUNTIME_CAPABILITY, IO_WORKER_RUNTIME_CAPABILITY)), ad, other) {
        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            if(operation.hasDefined("fail")) {
                throw new OperationFailedException("Let's rollback");
            }
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return true;
        }
            })
            .setRemoveOperation(new ReloadRequiredRemoveStepHandler(IO_POOL_RUNTIME_CAPABILITY, IO_WORKER_RUNTIME_CAPABILITY))
            .addReadWriteAttribute(ad, null, new ReloadRequiredWriteAttributeHandler(ad))
            .addReadWriteAttribute(other, null, new ReloadRequiredWriteAttributeHandler(other))
            .addCapability(IO_POOL_RUNTIME_CAPABILITY)
            .addCapability(IO_WORKER_RUNTIME_CAPABILITY)
            .build();

    private ManagementResourceRegistration rootRegistration;

    @Override
    protected void initModel(ManagementModel managementModel) {
        rootRegistration = managementModel.getRootResourceRegistration();
        // register the global operations to be able to call :read-attribute and :write-attribute
        GlobalOperationHandlers.registerGlobalOperations(rootRegistration, processType);
        // register the global notifications so there is no warning that emitted notifications are not described by the resource.
        GlobalNotifications.registerGlobalNotifications(rootRegistration, processType);
        rootRegistration.registerOperationHandler(CompositeOperationHandler.DEFINITION, CompositeOperationHandler.INSTANCE);
        rootRegistration.registerOperationHandler(new SimpleOperationDefinition("clean", new NonResolvingResourceDescriptionResolver()), (context, operation) -> {
            ManagementResourceRegistration mrr = context.getResourceRegistrationForUpdate();
            mrr.unregisterSubModel(TEST_ADDRESS1.getElement(0));
            mrr.unregisterSubModel(TEST_ADDRESS2.getElement(0));
            mrr.unregisterSubModel(TEST_ADDRESS4.getElement(0));
        });

        rootRegistration.registerOperationHandler(new SimpleOperationDefinition("create", new NonResolvingResourceDescriptionResolver()), (context, operation) -> {
            ManagementResourceRegistration mrr = context.getResourceRegistrationForUpdate();
            mrr.registerSubModel(TEST_RESOURCE1);
            mrr.registerSubModel(TEST_RESOURCE2);
            mrr.registerSubModel(TEST_RESOURCE4);
        });

    }
    @Before
    public void createModel() throws OperationFailedException {
        Assert.assertEquals(0, capabilityRegistry.getCapabilities().size());
        executeCheckNoFailure(createOperation("create", PathAddress.EMPTY_ADDRESS));
    }


    @After
    public void checkEmptyRegistry() throws Exception {
        //remove model registration
        executeCheckNoFailure(createOperation("clean", PathAddress.EMPTY_ADDRESS));
        //we check that each test cleaned up after itself
        Assert.assertEquals(0, capabilityRegistry.getCapabilities().size());
        Assert.assertEquals(0, capabilityRegistry.getPossibleCapabilities().size());
    }


    @Test
    public void testCapabilityRegistration() throws OperationFailedException {
        ManagementResourceRegistration registration = rootRegistration.getSubModel(TEST_ADDRESS1);
        Assert.assertEquals(2, registration.getCapabilities().size());
        Assert.assertEquals(3, capabilityRegistry.getPossibleCapabilities().size());  //resource1 has 2 + 1 from resource 2
        Assert.assertEquals(0, capabilityRegistry.getCapabilities().size());

        ModelNode addOp = createOperation(ADD, TEST_ADDRESS1);
        addOp.get("test").set("some test value");
        addOp.get("other").set("other value");
        executeCheckNoFailure(addOp);
        Assert.assertEquals(2, capabilityRegistry.getCapabilities().size());

        //post boot registration
        executeCheckNoFailure(createOperation("add-cap", TEST_ADDRESS1));
        Assert.assertEquals(3, registration.getCapabilities().size());
        Assert.assertEquals(4, capabilityRegistry.getPossibleCapabilities().size());


        executeCheckNoFailure(createOperation(REMOVE, TEST_ADDRESS1));
        Assert.assertEquals(0, capabilityRegistry.getCapabilities().size());

        ModelNode add2Op = createOperation(ADD, TEST_ADDRESS2);
        add2Op.get("test").set("some test value");
        executeCheckNoFailure(add2Op);

        Assert.assertEquals(1, capabilityRegistry.getCapabilities().size());
        Assert.assertEquals(4, capabilityRegistry.getPossibleCapabilities().size());

        executeCheckNoFailure(createOperation("add-sub-resource", TEST_ADDRESS2));

        Assert.assertEquals(1, capabilityRegistry.getCapabilities().size());
        Assert.assertEquals(5, capabilityRegistry.getPossibleCapabilities().size());

        executeCheckNoFailure(createOperation("remove-sub-resource", TEST_ADDRESS2));

        Assert.assertEquals(1, capabilityRegistry.getCapabilities().size());
        Assert.assertEquals(4, capabilityRegistry.getPossibleCapabilities().size());

        //remove test2 resource so capabilites are moved
        executeCheckNoFailure(createOperation(REMOVE, TEST_ADDRESS2));
        Assert.assertEquals(0, capabilityRegistry.getCapabilities().size());
    }

    @Test
    public void testRollBack() throws OperationFailedException {
        ModelNode addOp1 = createOperation(ADD, TEST_ADDRESS1);
        addOp1.get("test").set("some test value");
        addOp1.get("other").set("break"); //this value will throw exception and rollback should happen
        executeCheckForFailure(addOp1);
        Assert.assertEquals(0, capabilityRegistry.getCapabilities().size());

        ModelNode addOp2 = createOperation(ADD, TEST_ADDRESS2);
        addOp1.get("test").set("some test value");


        ModelNode composite = createOperation(ModelDescriptionConstants.COMPOSITE);
        composite.get(STEPS).add(addOp2);//adds one capability,
        composite.get(STEPS).add(addOp1); //breaks which causes operation to be rollbacked, so previously added capability shouldn't be represent.
        executeCheckForFailure(composite);
        Assert.assertEquals(0, capabilityRegistry.getCapabilities().size());
    }

    @Test
    public void testRollBackAfterPublish() throws OperationFailedException {
        ModelNode addOp1 = createOperation(ADD, TEST_ADDRESS1);
        addOp1.get("test").set("some test value");
        addOp1.get("other").set("other value");
        executeCheckNoFailure(addOp1);
        Assert.assertEquals(2, capabilityRegistry.getCapabilities().size());

        ModelNode addOp4 = createOperation(ADD, TEST_ADDRESS4);
        addOp4.get("test").set("some test value");
        addOp4.get("other").set("other value");
        addOp4.get("fail").set("true");
        executeCheckForFailure(addOp4); //Rollbacking
        Assert.assertEquals(2, capabilityRegistry.getCapabilities().size()); //Should remove the new RegistrationPoints

        addOp4 = createOperation(ADD, TEST_ADDRESS4);
        addOp4.get("test").set("some test value");
        addOp4.get("other").set("other value");
        executeCheckNoFailure(addOp4); //Will fail if rollback didn't work as epxected
        Assert.assertEquals(2, capabilityRegistry.getCapabilities().size());

        executeCheckNoFailure(createOperation(REMOVE, TEST_ADDRESS4));
        Assert.assertEquals(2, capabilityRegistry.getCapabilities().size());
        executeCheckNoFailure(createOperation(REMOVE, TEST_ADDRESS1));
        Assert.assertEquals(0, capabilityRegistry.getCapabilities().size());
    }
}
