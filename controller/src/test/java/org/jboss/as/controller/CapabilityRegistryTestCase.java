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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.as.controller.capability.Capability;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.operations.validation.AbstractParameterValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.test.AbstractControllerTestBase;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xnio.Pool;
import org.xnio.XnioWorker;

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
    private static final RuntimeCapability<Void> PROFILE_CAPABILITY = RuntimeCapability.Builder.of("org.wildfly.test.profile-capability", true, Void.class).build();
    private static final RuntimeCapability<Void> HOST_CAPABILITY = RuntimeCapability.Builder.of("org.wildfly.test.host-capability", true, Void.class).build();
    private static final RuntimeCapability<Void> RELOADED_CAPABILITY = RuntimeCapability.Builder.of("org.wildfly.test.reloaded-capability", true, Void.class).build();
    private static final RuntimeCapability<Void> INDEPENDENT_CAPABILITY = RuntimeCapability.Builder.of("org.wildfly.test.independent-capability", true, Void.class).build();
    private static final RuntimeCapability<Void> ROOT_CAPABILITY = RuntimeCapability.Builder.of("org.wildfly.test.root-capability", false, Void.class).build();
    private static final RuntimeCapability<Void> DEPENDENT_CAPABILITY = RuntimeCapability.Builder.of("org.wildfly.test.dep-capability", false, Void.class)
            .addRequirements(ROOT_CAPABILITY.getName()).build();
    private static final RuntimeCapability<Void> TRANS_DEP_CAPABILITY = RuntimeCapability.Builder.of("org.wildfly.test.trans-dep-capability", false, Void.class)
            .addRequirements(DEPENDENT_CAPABILITY.getName()).build();

    private static PathAddress TEST_ADDRESS1 = PathAddress.pathAddress("subsystem", "test1");
    private static PathAddress TEST_ADDRESS2 = PathAddress.pathAddress("subsystem", "test2");
    private static PathAddress TEST_ADDRESS3 = PathAddress.pathAddress("sub", "resource");
    private static PathAddress TEST_ADDRESS4 = PathAddress.pathAddress("subsystem", "test4");
    private static PathAddress TEST_ADDRESS5 = PathAddress.pathAddress("subsystem", "broken");

    private static PathElement RELOAD_ELEMENT = PathElement.pathElement("subsystem", "reload");
    private static PathElement CHILD_ELEMENT = PathElement.pathElement("child", "test");
    private static PathElement GRANDCHILD_ELEMENT = PathElement.pathElement("grandchild", "test");
    private static PathElement INFANT_ELEMENT = PathElement.pathElement("infant", "test");
    private static PathElement INDEPENDENT_ELEMENT = PathElement.pathElement("independent", "test");
    private static PathElement UNINCORPORATED_ELEMENT = PathElement.pathElement("unincorporated", "test");
    private static PathElement HOST_ELEMENT = PathElement.pathElement("host", "test");


    private static PathElement PROFILE_ELEMENT = PathElement.pathElement("profile", "test");
    private static PathElement NO_CAP_ELEMENT = PathElement.pathElement("subsystem", "no-cap");
    private static PathElement DEP_CAP_ELEMENT = PathElement.pathElement("subsystem", "dep-cap");

    private static ResourceDefinition TEST_RESOURCE1 = ResourceBuilder.Factory.create(TEST_ADDRESS1.getElement(0),
            NonResolvingResourceDescriptionResolver.INSTANCE)
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
            NonResolvingResourceDescriptionResolver.INSTANCE)
                .setAddOperation(new AbstractAddStepHandler(Collections.singleton(TEST_CAPABILITY3)))
                .setRemoveOperation(new ReloadRequiredRemoveStepHandler(TEST_CAPABILITY3))
                .addReadWriteAttribute(ad, null, new ReloadRequiredWriteAttributeHandler(ad))
                .addCapability(TEST_CAPABILITY3)
                .build();


    private static ResourceDefinition TEST_RESOURCE2 = ResourceBuilder.Factory.create(TEST_ADDRESS2.getElement(0),
            NonResolvingResourceDescriptionResolver.INSTANCE)
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
            NonResolvingResourceDescriptionResolver.INSTANCE)
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

    private static final OperationStepHandler RELOAD_HANDLER = new OperationStepHandler() {
        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.reloadRequired();
            context.completeStep(OperationContext.RollbackHandler.REVERT_RELOAD_REQUIRED_ROLLBACK_HANDLER);
        }
    };
    private static final OperationDefinition RELOAD_DEFINITION = new SimpleOperationDefinition("reload", NonResolvingResourceDescriptionResolver.INSTANCE);

    private static final OperationStepHandler RUNTIME_MOD_HANDLER = new OperationStepHandler() {
        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    // If we execute, the result is now defined
                    context.getResult().set(true);
                }
            }, OperationContext.Stage.RUNTIME);
        }
    };

    private static final OperationDefinition RUNTIME_MOD_DEFINITION = new SimpleOperationDefinition("runtime-mod", NonResolvingResourceDescriptionResolver.INSTANCE);
    private static final OperationDefinition RUNTIME_ONLY_DEFINITION = new SimpleOperationDefinitionBuilder("runtime-only", NonResolvingResourceDescriptionResolver.INSTANCE)
            .setRuntimeOnly()
            .build();

    private static ResourceBuilder createReloadTestResourceBuilder(PathElement address, RuntimeCapability... caps) {
        return addReloadTestOps(ResourceBuilder.Factory.create(address, NonResolvingResourceDescriptionResolver.INSTANCE), caps);
    }

    private static ResourceBuilder addReloadTestOps(ResourceBuilder builder, RuntimeCapability... caps) {
        ResourceBuilder result = builder
                .addOperation(RELOAD_DEFINITION, RELOAD_HANDLER)
                .addOperation(RUNTIME_MOD_DEFINITION, RUNTIME_MOD_HANDLER)
                .addOperation(RUNTIME_ONLY_DEFINITION, RUNTIME_MOD_HANDLER);
        if (caps != null && caps.length > 0) {
            result = result.addCapabilities((Capability[]) caps)
                    .setAddOperation(new AbstractAddStepHandler(new HashSet<RuntimeCapability>(Arrays.asList(caps))))
                    .setRemoveOperation(new ModelOnlyRemoveStepHandler());
        }
        return result;
    }

    private static ResourceDefinition RELOAD_PARENT = createReloadTestResourceBuilder(RELOAD_ELEMENT, RELOADED_CAPABILITY)
            .pushChild(createReloadTestResourceBuilder(INDEPENDENT_ELEMENT, INDEPENDENT_CAPABILITY))
                .pushChild(createReloadTestResourceBuilder(CHILD_ELEMENT)).pop()
            .pop()
            .pushChild(createReloadTestResourceBuilder(UNINCORPORATED_ELEMENT))
                .setIncorporatingCapabilities(Collections.emptySet())
                .pushChild(createReloadTestResourceBuilder(CHILD_ELEMENT)).pop()
            .pop()
            .pushChild(createReloadTestResourceBuilder(CHILD_ELEMENT))
                .pushChild(createReloadTestResourceBuilder(GRANDCHILD_ELEMENT))
                    .pushChild(createReloadTestResourceBuilder(INFANT_ELEMENT))
                    .pop()
                .pop()
                .pushChild(createReloadTestResourceBuilder(UNINCORPORATED_ELEMENT))
                    .setIncorporatingCapabilities(Collections.emptySet())
                    .pushChild(createReloadTestResourceBuilder(INFANT_ELEMENT)).pop()
                .pop()
            .pop()
            .build();

    private static ResourceDefinition HOST_RESOURCE = createReloadTestResourceBuilder(HOST_ELEMENT, HOST_CAPABILITY)
            .pushChild(createReloadTestResourceBuilder(CHILD_ELEMENT)).pop()
            .build();

    private static ResourceDefinition PROFILE_RESOURCE = createReloadTestResourceBuilder(PROFILE_ELEMENT, PROFILE_CAPABILITY)
            .pushChild(createReloadTestResourceBuilder(NO_CAP_ELEMENT))
                .pushChild(createReloadTestResourceBuilder(CHILD_ELEMENT)).pop()
            .pop()
            .build();

    private static ResourceDefinition NO_CAP_RESOURCE = createReloadTestResourceBuilder(CHILD_ELEMENT)
            .build();

    private static ResourceDefinition DEP_CAP_RESOURCE = createReloadTestResourceBuilder(DEP_CAP_ELEMENT, DEPENDENT_CAPABILITY)
            .pushChild(createReloadTestResourceBuilder(CHILD_ELEMENT, TRANS_DEP_CAPABILITY)).pop()
            .build();

    private static final AtomicReference<CountDownLatch> readLatchHolder = new AtomicReference<>();
    private static final AtomicReference<CountDownLatch> failLatchHolder = new AtomicReference<>();
    private static final ResourceDefinition BAD_RESOURCE = ResourceBuilder.Factory.create(TEST_ADDRESS5.getElement(0),
            NonResolvingResourceDescriptionResolver.INSTANCE)
            .setAddOperation(new AbstractAddStepHandler(Collections.singleton(TEST_CAPABILITY1)) {
                @Override
                protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {

                    try {
                        CountDownLatch latch = new CountDownLatch(1);
                        failLatchHolder.set(latch);

                        // Notify the test we are done with model stage
                        readLatchHolder.get().countDown();

                        // Wait for test to unblock us
                        latch.await();

                    } catch (InterruptedException e) {
                        // ignore
                    }
                    context.setRollbackOnly();
                    context.getFailureDescription().set("failed per design");
                }
            })
            .setRemoveOperation(new ReloadRequiredRemoveStepHandler(TEST_CAPABILITY3))
            .addOperation(new SimpleOperationDefinition("read", NonResolvingResourceDescriptionResolver.INSTANCE),
                    ((context, operation) -> context.getResult().set(true)))
            .build();

    private static final int RELOAD_CAP_COUNT = 6;

    private ManagementModel managementModel;

    @Override
    protected void initModel(ManagementModel managementModel) {
        this.managementModel = managementModel;
        ManagementResourceRegistration rootRegistration = managementModel.getRootResourceRegistration();
        // register the global operations to be able to call :read-attribute and :write-attribute
        GlobalOperationHandlers.registerGlobalOperations(rootRegistration, processType);
        // register the global notifications so there is no warning that emitted notifications are not described by the resource.
        GlobalNotifications.registerGlobalNotifications(rootRegistration, processType);
        rootRegistration.registerOperationHandler(CompositeOperationHandler.DEFINITION, CompositeOperationHandler.INSTANCE);
        rootRegistration.registerOperationHandler(new SimpleOperationDefinition("clean", NonResolvingResourceDescriptionResolver.INSTANCE), (context, operation) -> {
            ManagementResourceRegistration mrr = context.getResourceRegistrationForUpdate();
            mrr.unregisterSubModel(TEST_RESOURCE1.getPathElement());
            mrr.unregisterSubModel(TEST_RESOURCE2.getPathElement());
            mrr.unregisterSubModel(TEST_RESOURCE4.getPathElement());
            mrr.unregisterSubModel(BAD_RESOURCE.getPathElement());
            mrr.unregisterSubModel(RELOAD_PARENT.getPathElement());
            mrr.unregisterSubModel(HOST_RESOURCE.getPathElement());
            mrr.unregisterSubModel(PROFILE_RESOURCE.getPathElement());
            mrr.unregisterSubModel(NO_CAP_RESOURCE.getPathElement());
            mrr.unregisterSubModel(DEP_CAP_RESOURCE.getPathElement());
        });

        rootRegistration.registerOperationHandler(new SimpleOperationDefinition("create", NonResolvingResourceDescriptionResolver.INSTANCE), (context, operation) -> {
            ManagementResourceRegistration mrr = context.getResourceRegistrationForUpdate();
            mrr.registerSubModel(TEST_RESOURCE1);
            mrr.registerSubModel(TEST_RESOURCE2);
            mrr.registerSubModel(TEST_RESOURCE4);
            mrr.registerSubModel(BAD_RESOURCE);
            mrr.registerSubModel(RELOAD_PARENT);
            mrr.registerSubModel(HOST_RESOURCE);
            mrr.registerSubModel(PROFILE_RESOURCE);
            mrr.registerSubModel(NO_CAP_RESOURCE);
            mrr.registerSubModel(DEP_CAP_RESOURCE);
        });
        rootRegistration.registerOperationHandler(new SimpleOperationDefinition("root-cap", NonResolvingResourceDescriptionResolver.INSTANCE),
                new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        context.registerCapability(ROOT_CAPABILITY);
                    }
                });
        rootRegistration.registerOperationHandler(new SimpleOperationDefinition("no-root-cap", NonResolvingResourceDescriptionResolver.INSTANCE),
                new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        context.deregisterCapability(ROOT_CAPABILITY.getName());
                    }
                });
        rootRegistration.registerOperationHandler(RELOAD_DEFINITION, RELOAD_HANDLER);
        rootRegistration.registerOperationHandler(RUNTIME_MOD_DEFINITION, RUNTIME_MOD_HANDLER);
        rootRegistration.registerOperationHandler(RUNTIME_ONLY_DEFINITION, RUNTIME_MOD_HANDLER);
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
        ManagementResourceRegistration registration = managementModel.getRootResourceRegistration().getSubModel(TEST_ADDRESS1);
        Assert.assertEquals(2, registration.getCapabilities().size());
        Assert.assertEquals(RELOAD_CAP_COUNT + 3, capabilityRegistry.getPossibleCapabilities().size());  //resource1 has 2 + 1 from resource 2
        Assert.assertEquals(0, capabilityRegistry.getCapabilities().size());

        ModelNode addOp = createOperation(ADD, TEST_ADDRESS1);
        addOp.get("test").set("some test value");
        addOp.get("other").set("other value");
        executeCheckNoFailure(addOp);
        Assert.assertEquals(2, capabilityRegistry.getCapabilities().size());

        //post boot registration
        executeCheckNoFailure(createOperation("add-cap", TEST_ADDRESS1));
        Assert.assertEquals(3, registration.getCapabilities().size());
        Assert.assertEquals(RELOAD_CAP_COUNT + 4, capabilityRegistry.getPossibleCapabilities().size());


        executeCheckNoFailure(createOperation(REMOVE, TEST_ADDRESS1));
        Assert.assertEquals(0, capabilityRegistry.getCapabilities().size());

        ModelNode add2Op = createOperation(ADD, TEST_ADDRESS2);
        add2Op.get("test").set("some test value");
        executeCheckNoFailure(add2Op);

        Assert.assertEquals(1, capabilityRegistry.getCapabilities().size());
        Assert.assertEquals(RELOAD_CAP_COUNT + 4, capabilityRegistry.getPossibleCapabilities().size());

        executeCheckNoFailure(createOperation("add-sub-resource", TEST_ADDRESS2));

        Assert.assertEquals(1, capabilityRegistry.getCapabilities().size());
        Assert.assertEquals(RELOAD_CAP_COUNT + 5, capabilityRegistry.getPossibleCapabilities().size());

        executeCheckNoFailure(createOperation("remove-sub-resource", TEST_ADDRESS2));

        Assert.assertEquals(1, capabilityRegistry.getCapabilities().size());
        //this is now 3 as remove resource also unregisteres sub resource from mgmt tree
        Assert.assertEquals(RELOAD_CAP_COUNT + 4, capabilityRegistry.getPossibleCapabilities().size());

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
        executeCheckForFailure(addOp4); // Should rollback due to conflict with TEST_RESOURCE1
        Assert.assertEquals(2, capabilityRegistry.getCapabilities().size());

        // Remove the conflict
        executeCheckNoFailure(createOperation(REMOVE, TEST_ADDRESS1));
        Assert.assertEquals(0, capabilityRegistry.getCapabilities().size());

        addOp4 = createOperation(ADD, TEST_ADDRESS4);
        addOp4.get("test").set("some test value");
        addOp4.get("other").set("other value");
        addOp4.get("fail").set("true");
        executeCheckForFailure(addOp4); // Op designed to roll back
        Assert.assertEquals(0, capabilityRegistry.getCapabilities().size()); //Should remove the new RegistrationPoints

        addOp4 = createOperation(ADD, TEST_ADDRESS4);
        addOp4.get("test").set("some test value");
        addOp4.get("other").set("other value");
        executeCheckNoFailure(addOp4); //Will fail if rollback didn't work as epxected
        Assert.assertEquals(2, capabilityRegistry.getCapabilities().size());

        executeCheckNoFailure(createOperation(REMOVE, TEST_ADDRESS4));
        Assert.assertEquals(0, capabilityRegistry.getCapabilities().size());
    }

    // Check that subsystem=reload requiring reload prevents runtime execution of
    // subsystem=reload/child=test, since it is incorporated by the parent resource cap
    @Test
    public void testChildReload() throws OperationFailedException {
        add(RELOAD_ELEMENT);
        try {
            requireReload(RELOAD_ELEMENT);
            runtimeCheck(false, RELOAD_ELEMENT);
            runtimeOnlyCheck(true, RELOAD_ELEMENT);
            runtimeCheck(false, RELOAD_ELEMENT, CHILD_ELEMENT);
            runtimeOnlyCheck(true, RELOAD_ELEMENT, CHILD_ELEMENT);
        } finally {
            //noinspection ThrowFromFinallyBlock
            remove(RELOAD_ELEMENT);
        }
    }

    // Check that subsystem=reload requiring reload prevents runtime execution of
    // subsystem=reload/child=test/grandchild=test, since it is incorporated by the parent resource cap
    @Test
    public void testGrandChildReload() throws OperationFailedException {
        add(RELOAD_ELEMENT);
        try {
            requireReload(RELOAD_ELEMENT);
            runtimeCheck(false, RELOAD_ELEMENT);
            runtimeOnlyCheck(true, RELOAD_ELEMENT);
            runtimeCheck(false, RELOAD_ELEMENT, CHILD_ELEMENT, GRANDCHILD_ELEMENT);
            runtimeOnlyCheck(true, RELOAD_ELEMENT, CHILD_ELEMENT, GRANDCHILD_ELEMENT);
        } finally {
            //noinspection ThrowFromFinallyBlock
            remove(RELOAD_ELEMENT);
        }
    }

    // Check that subsystem=reload requiring reload prevents runtime execution of
    // subsystem=reload/child=test/grandchild=test/infant=test, since it is incorporated by the parent resource cap
    // This one is getting a bit extreme. :)
    @Test
    public void testInfantReload() throws OperationFailedException {
        add(RELOAD_ELEMENT);
        try {
            requireReload(RELOAD_ELEMENT);
            runtimeCheck(false, RELOAD_ELEMENT);
            runtimeOnlyCheck(true, RELOAD_ELEMENT);
            runtimeCheck(false, RELOAD_ELEMENT, CHILD_ELEMENT, GRANDCHILD_ELEMENT, INFANT_ELEMENT);
            runtimeOnlyCheck(true, RELOAD_ELEMENT, CHILD_ELEMENT, GRANDCHILD_ELEMENT, INFANT_ELEMENT);
        } finally {
            //noinspection ThrowFromFinallyBlock
            remove(RELOAD_ELEMENT);
        }
    }

    // Check that subsystem=reload requiring reload doesn't prevent runtime execution of
    // subsystem=reload/independent=test, since it has its own capability
    @Test
    public void testIndependentCapabilityReload() throws OperationFailedException {
        add(RELOAD_ELEMENT);
        try {
            requireReload(RELOAD_ELEMENT);
            runtimeCheck(false, RELOAD_ELEMENT);
            runtimeOnlyCheck(true, RELOAD_ELEMENT);
            // First, check what happens when the independent-capability isn't even registered yet
            // This is kind of an odd case, but good to verify
            runtimeCheck(true, RELOAD_ELEMENT, INDEPENDENT_ELEMENT);
            runtimeOnlyCheck(true, RELOAD_ELEMENT, INDEPENDENT_ELEMENT);
            runtimeCheck(true, RELOAD_ELEMENT, INDEPENDENT_ELEMENT, CHILD_ELEMENT);
            runtimeOnlyCheck(true, RELOAD_ELEMENT, INDEPENDENT_ELEMENT, CHILD_ELEMENT);
            // Add the independent element child so its independent-capability is registered
            add(RELOAD_ELEMENT, INDEPENDENT_ELEMENT);
            try {
                runtimeCheck(true, RELOAD_ELEMENT, INDEPENDENT_ELEMENT);
                runtimeOnlyCheck(true, RELOAD_ELEMENT, INDEPENDENT_ELEMENT);
                runtimeCheck(true, RELOAD_ELEMENT, INDEPENDENT_ELEMENT, CHILD_ELEMENT);
                runtimeOnlyCheck(true, RELOAD_ELEMENT, INDEPENDENT_ELEMENT, CHILD_ELEMENT);
                // Make sure triggering reload of independent element child affects things
                requireReload(RELOAD_ELEMENT, INDEPENDENT_ELEMENT);
                runtimeCheck(false, RELOAD_ELEMENT, INDEPENDENT_ELEMENT);
                runtimeOnlyCheck(true, RELOAD_ELEMENT, INDEPENDENT_ELEMENT);
                runtimeCheck(false, RELOAD_ELEMENT, INDEPENDENT_ELEMENT, CHILD_ELEMENT);
                runtimeOnlyCheck(true, RELOAD_ELEMENT, INDEPENDENT_ELEMENT, CHILD_ELEMENT);
            }  finally {
                //noinspection ThrowFromFinallyBlock
                remove(RELOAD_ELEMENT, INDEPENDENT_ELEMENT);
            }
        } finally {
            //noinspection ThrowFromFinallyBlock
            remove(RELOAD_ELEMENT);
        }
    }

    // Check that subsystem=reload requiring reload doesn't prevent runtime execution of
    // subsystem=reload/unincorporated=test, since it is not incorporated by the parent resource cap
    @Test
    public void testUnincorporatedChildResourceReload() throws OperationFailedException {
        add(RELOAD_ELEMENT);
        try {
            requireReload(RELOAD_ELEMENT);
            runtimeCheck(false, RELOAD_ELEMENT);
            runtimeOnlyCheck(true, RELOAD_ELEMENT);
            runtimeCheck(true, RELOAD_ELEMENT, UNINCORPORATED_ELEMENT);
            runtimeOnlyCheck(true, RELOAD_ELEMENT, UNINCORPORATED_ELEMENT);
            runtimeCheck(true, RELOAD_ELEMENT, UNINCORPORATED_ELEMENT, CHILD_ELEMENT);
            runtimeOnlyCheck(true, RELOAD_ELEMENT, UNINCORPORATED_ELEMENT, CHILD_ELEMENT);
        } finally {
            //noinspection ThrowFromFinallyBlock
            remove(RELOAD_ELEMENT);
        }
    }

    // Check that subsystem=reload requiring reload doesn't prevent runtime execution of
    // subsystem=reload/child=test/unincorporated=test, since it is not incorporated by
    // the grandparent resource cap. Also test subsystem=reload/child=test/unincorporated=test/infant=test
    // to verify that its unincorporated parent stops checks further up the chain
    @Test
    public void testUnincorporatedGranchildResourceReload() throws OperationFailedException {
        add(RELOAD_ELEMENT);
        try {
            requireReload(RELOAD_ELEMENT);
            runtimeCheck(false, RELOAD_ELEMENT);
            runtimeOnlyCheck(true, RELOAD_ELEMENT);
            runtimeCheck(true, RELOAD_ELEMENT, CHILD_ELEMENT, UNINCORPORATED_ELEMENT);
            runtimeOnlyCheck(true, RELOAD_ELEMENT, CHILD_ELEMENT, UNINCORPORATED_ELEMENT);
            runtimeCheck(true, RELOAD_ELEMENT, CHILD_ELEMENT, UNINCORPORATED_ELEMENT, INFANT_ELEMENT);
            runtimeOnlyCheck(true, RELOAD_ELEMENT, CHILD_ELEMENT, UNINCORPORATED_ELEMENT, INFANT_ELEMENT);
        } finally {
            //noinspection ThrowFromFinallyBlock
            remove(RELOAD_ELEMENT);
        }

    }

    // Check that root resource requiring reload doesn't prevent runtime execution of
    // /child=test, since root resource capabilities don't block children
    @Test
    public void testRootExclusion() throws OperationFailedException {
        executeCheckNoFailure(Util.createEmptyOperation("root-cap", PathAddress.EMPTY_ADDRESS));
        try {
            requireReload();
            runtimeCheck(false);
            runtimeOnlyCheck(true);
            runtimeCheck(true, CHILD_ELEMENT);
            runtimeOnlyCheck(true, CHILD_ELEMENT);
            runtimeCheck(true, RELOAD_ELEMENT);
            runtimeOnlyCheck(true, RELOAD_ELEMENT);
            // Now add the reload subsystem so its capability is registered
            add(RELOAD_ELEMENT);
            try {
                runtimeCheck(true, RELOAD_ELEMENT);
                runtimeOnlyCheck(true, RELOAD_ELEMENT);
                // Now trigger reload-required from the reload subsystem
                requireReload(RELOAD_ELEMENT);
                runtimeCheck(false, RELOAD_ELEMENT);
                runtimeOnlyCheck(true, RELOAD_ELEMENT);
            } finally {
                //noinspection ThrowFromFinallyBlock
                remove(RELOAD_ELEMENT);
            }
        } finally {
            //noinspection ThrowFromFinallyBlock
            executeCheckNoFailure(Util.createEmptyOperation("no-root-cap", PathAddress.EMPTY_ADDRESS));
        }
    }

    // Check that /host=test resource requiring reload doesn't prevent runtime execution of
    // /host=test/child=test, since host root resource capabilities don't block children
    @Test
    public void testHostRootExclusion() throws OperationFailedException {
        add(HOST_ELEMENT);
        try {
            requireReload(HOST_ELEMENT);
            runtimeCheck(false, HOST_ELEMENT);
            runtimeOnlyCheck(true, HOST_ELEMENT);
            runtimeCheck(true, HOST_ELEMENT, CHILD_ELEMENT);
            runtimeOnlyCheck(true, HOST_ELEMENT, CHILD_ELEMENT);
        } finally {
            //noinspection ThrowFromFinallyBlock
            remove(HOST_ELEMENT);
        }
    }

    // Check that /profile=test resource requiring reload doesn't prevent runtime execution of
    // /profile=test/subsystem=no-cap, since kernel resource capabilities don't block subsystems
    @Test
    public void testProfileExclusion() throws OperationFailedException {
        add(PROFILE_ELEMENT);
        try {
            requireReload(PROFILE_ELEMENT);
            runtimeCheck(false, PROFILE_ELEMENT);
            runtimeOnlyCheck(true, PROFILE_ELEMENT);
            runtimeCheck(true, PROFILE_ELEMENT, NO_CAP_ELEMENT);
            runtimeOnlyCheck(true, PROFILE_ELEMENT, NO_CAP_ELEMENT);
            runtimeCheck(true, PROFILE_ELEMENT, NO_CAP_ELEMENT, CHILD_ELEMENT);
            runtimeOnlyCheck(true, PROFILE_ELEMENT, NO_CAP_ELEMENT, CHILD_ELEMENT);
        } finally {
            //noinspection ThrowFromFinallyBlock
            remove(PROFILE_ELEMENT);
        }
    }

    // Transitive dependency check
    // Check that subsystem=dep-cap requires reload once the root resource "root-cap"
    // does, because the subsystem=dep-cap's capability requires root-cap.
    @Test
    public void testDependentCapabilities() throws OperationFailedException {

        executeCheckNoFailure(Util.createEmptyOperation("root-cap", PathAddress.EMPTY_ADDRESS));
        try {
            add(DEP_CAP_ELEMENT);
            try {
                add(DEP_CAP_ELEMENT, CHILD_ELEMENT);
                try {
                    runtimeCheck(true, DEP_CAP_ELEMENT);
                    runtimeOnlyCheck(true, DEP_CAP_ELEMENT);
                    runtimeCheck(true, DEP_CAP_ELEMENT, CHILD_ELEMENT);
                    runtimeOnlyCheck(true, DEP_CAP_ELEMENT, CHILD_ELEMENT);
                    requireReload();
                    runtimeCheck(false, DEP_CAP_ELEMENT);
                    runtimeOnlyCheck(true, DEP_CAP_ELEMENT);
                    runtimeCheck(false, DEP_CAP_ELEMENT, CHILD_ELEMENT);
                    runtimeOnlyCheck(true, DEP_CAP_ELEMENT, CHILD_ELEMENT);
                } finally {
                    //noinspection ThrowFromFinallyBlock
                    remove(DEP_CAP_ELEMENT, CHILD_ELEMENT);
                }
            } finally {
                //noinspection ThrowFromFinallyBlock
                remove(DEP_CAP_ELEMENT);
            }
        } finally {
            //noinspection ThrowFromFinallyBlock
            executeCheckNoFailure(Util.createEmptyOperation("no-root-cap", PathAddress.EMPTY_ADDRESS));
        }
    }

    @Test
    public void testClear() throws OperationFailedException, InterruptedException {
        add(RELOAD_ELEMENT);
        try {
            requireReload(RELOAD_ELEMENT);
            runtimeCheck(false, RELOAD_ELEMENT);
            runtimeOnlyCheck(true, RELOAD_ELEMENT);

            // Do a mock reload
            CountDownLatch latch = new CountDownLatch(1);
            ServiceController<?> svc = container.getRequiredService(ServiceName.of("ModelController"));
            svc.addListener(new AbstractServiceListener<Object>(){

                @Override
                public void listenerAdded(ServiceController<?> controller) {
                    controller.setMode(ServiceController.Mode.NEVER);
                }

                @Override
                public void transition(ServiceController<?> controller, ServiceController.Transition transition) {
                    if (transition == ServiceController.Transition.STOPPING_to_DOWN) {
                        controller.setMode(ServiceController.Mode.ACTIVE);
                    } else if (transition == ServiceController.Transition.STARTING_to_UP) {
                        controller.removeListener(this);
                        latch.countDown();
                    }
                }
            });
            Assert.assertTrue("Failed to reload", latch.await(30, TimeUnit.SECONDS));

            Assert.assertEquals(0, capabilityRegistry.getCapabilities().size());
            Assert.assertEquals(0, capabilityRegistry.getPossibleCapabilities().size());
            runtimeCheck(true, RELOAD_ELEMENT);
            runtimeOnlyCheck(true, RELOAD_ELEMENT);
        } finally {
            //noinspection ThrowFromFinallyBlock
            remove(RELOAD_ELEMENT);
        }
    }

    @Test
    public void testConcurrentRead() throws OperationFailedException, InterruptedException {
        readLatchHolder.set(new CountDownLatch(1));

        ModelNode addOp = Util.createAddOperation(TEST_ADDRESS5);
        Thread t = new Thread(() -> executeForFailure(addOp));
        t.setDaemon(true);
        t.start();
        try {
            // Wait for the add op to reach Stage.RUNTIME, so the cap is registered
            readLatchHolder.get().await();

            CountDownLatch failLatch = failLatchHolder.get();
            Assert.assertNotNull(failLatch);

            // add op's registry change should not be visible
            Assert.assertEquals(0, capabilityRegistry.getCapabilities().size());

            // Execute a concurrent read
            executeCheckNoFailure(Util.createEmptyOperation("read", TEST_ADDRESS5));

            // Confirm add op is still blocking
            Assert.assertTrue(t.isAlive());

            // add op's registry change should not be visible
            Assert.assertEquals(0, capabilityRegistry.getCapabilities().size());

            // Let the add op release and fail
            failLatch.countDown();

            // Wait for add op to finish and then check the registry
            t.join(30000);
            Assert.assertFalse(t.isAlive());
            Assert.assertEquals(0, capabilityRegistry.getCapabilities().size());

        } finally {
            if (t.isAlive()) {
                t.interrupt();
            }
        }
    }

    private void add(PathElement... address) throws OperationFailedException {
        executeCheckNoFailure(Util.createEmptyOperation("add", PathAddress.pathAddress(address)));
    }
    private void remove(PathElement... address) throws OperationFailedException {
        executeCheckNoFailure(Util.createEmptyOperation("remove", PathAddress.pathAddress(address)));
    }

    private void requireReload(PathElement... elements) throws OperationFailedException {
        PathAddress address = PathAddress.pathAddress(elements);
        executeCheckNoFailure(Util.createEmptyOperation(RELOAD_DEFINITION.getName(), address));
    }

    private void runtimeCheck(boolean expectResult, PathElement... elements) throws OperationFailedException {
        runtimeCheck(false, expectResult, elements);
    }

    private void runtimeOnlyCheck(boolean expectResult, PathElement... elements) throws OperationFailedException {
        runtimeCheck(true, expectResult, elements);
    }

    private void runtimeCheck(boolean runtimeOnly, boolean expectResult, PathElement... elements) throws OperationFailedException {
        OperationDefinition opDef = runtimeOnly ? RUNTIME_ONLY_DEFINITION : RUNTIME_MOD_DEFINITION;
        PathAddress address = PathAddress.pathAddress(elements);
        ModelNode op = Util.createEmptyOperation(opDef.getName(), address);
        ModelNode result = executeForResult(op);
        if (expectResult) {
            Assert.assertTrue(result.toString(), result.isDefined());
            Assert.assertTrue(result.toString(), result.asBoolean());
        } else {
            Assert.assertFalse(result.toString(), result.isDefined());
        }
    }
}
