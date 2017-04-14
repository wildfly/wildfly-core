/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
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

import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.test.AbstractControllerTestBase;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Test;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class ValidateModelTestCase extends AbstractControllerTestBase {
    private static final SimpleAttributeDefinition ad = new SimpleAttributeDefinitionBuilder("test", ModelType.STRING, true)
            .setRequires("other", "alter2")
            .build();

    private static final SimpleAttributeDefinition other = new SimpleAttributeDefinitionBuilder("other", ModelType.STRING, true)
            .setAlternatives("alter2")
            .build();
    private static final SimpleAttributeDefinition alter1 = new SimpleAttributeDefinitionBuilder("alter1", ModelType.STRING, true)
            .build();
    private static final SimpleAttributeDefinition alter2 = new SimpleAttributeDefinitionBuilder("alter2", ModelType.STRING, true)
            .setAlternatives("other")
            .build();
    private static final SimpleAttributeDefinition alter3 = new SimpleAttributeDefinitionBuilder("alter3", ModelType.STRING, true)
            .setAlternatives("alter2")
            .build();
    private static final AttributeDefinition object = ObjectTypeAttributeDefinition.Builder.of("object", ad, other, alter1, alter2, alter3)
            .setRequired(false)
            .build();


    private static PathAddress TEST_ADDRESS = PathAddress.pathAddress("subsystem", "test");

    @Override
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration rootRegistration = managementModel.getRootResourceRegistration();
        // register the global operations to be able to call :read-attribute and :write-attribute
        GlobalOperationHandlers.registerGlobalOperations(rootRegistration, processType);
        // register the global notifications so there is no warning that emitted notifications are not described by the resource.
        GlobalNotifications.registerGlobalNotifications(rootRegistration, processType);

        ResourceDefinition profileDefinition = createDummyProfileResourceDefinition();
        rootRegistration.registerSubModel(profileDefinition);
    }

    private static ResourceDefinition createDummyProfileResourceDefinition() {
        return ResourceBuilder.Factory.create(TEST_ADDRESS.getElement(0),
                new NonResolvingResourceDescriptionResolver())
                .setAddOperation(new AbstractAddStepHandler() {

                    @Override
                    protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
                        ad.validateAndSet(operation, model);
                        other.validateAndSet(operation, model);
                        alter1.validateAndSet(operation, model);
                        alter2.validateAndSet(operation, model);
                        alter3.validateAndSet(operation, model);
                        object.validateAndSet(operation, model);
                    }

                })
                .setRemoveOperation(ReloadRequiredRemoveStepHandler.INSTANCE)
                .addReadWriteAttribute(ad, null, new ReloadRequiredWriteAttributeHandler(ad))
                .addReadWriteAttribute(other, null, new ReloadRequiredWriteAttributeHandler(other))
                .addReadWriteAttribute(alter1, null, new ReloadRequiredWriteAttributeHandler(alter1))
                .addReadWriteAttribute(alter2, null, new ReloadRequiredWriteAttributeHandler(alter2))
                .addReadWriteAttribute(alter3, null, new ReloadRequiredWriteAttributeHandler(alter3))
                .addReadWriteAttribute(object, null, new ReloadRequiredWriteAttributeHandler(object))
                .build();
    }


    @Test
    public void testRequires() throws OperationFailedException {
        ModelNode addOp = createOperation("add", TEST_ADDRESS);
        addOp.get("test").set("some test value");
        executeForFailure(addOp);

        addOp.get("other").set("other value");
        executeCheckNoFailure(addOp);

        executeCheckNoFailure(createOperation("remove", TEST_ADDRESS));
    }

    @Test
    public void testAlternatives() throws OperationFailedException {
        ModelNode addOp = createOperation("add", TEST_ADDRESS);
        addOp.get("other").set("some test value");
        addOp.get("alter2").set("some test value");
        executeCheckForFailure(addOp);


        addOp.remove("other");
        executeCheckNoFailure(addOp);

        executeCheckNoFailure(createOperation("remove", TEST_ADDRESS));
    }

    @Test
    public void testRequiresAndAlternatives() throws OperationFailedException {
        ModelNode addOp = createOperation("add", TEST_ADDRESS);
        addOp.get("test").set("some test value");
        addOp.get("other").set("some test value");
        addOp.get("alter2").set("some test value");
        executeCheckForFailure(addOp);

        addOp.remove("other");
        executeCheckNoFailure(addOp);

        executeCheckNoFailure(createOperation("remove", TEST_ADDRESS));

        addOp.get("alter3").set("some test value");
        executeCheckForFailure(addOp);

        addOp.remove("alter2");
        executeCheckForFailure(addOp);

        addOp.get("other").set("some test value");
        executeCheckNoFailure(addOp);

        executeCheckNoFailure(createOperation("remove", TEST_ADDRESS));
    }

    @Test
    public void testRequiresAndAlternativesNested() throws OperationFailedException {
        ModelNode addOp = createOperation("add", TEST_ADDRESS);
        ModelNode object = addOp.get("object");
        object.get("test").set("some test value");
        object.get("other").set("some test value");
        object.get("alter2").set("some test value");
        executeCheckForFailure(addOp);

        object.remove("other");
        executeCheckNoFailure(addOp);

        executeCheckNoFailure(createOperation("remove", TEST_ADDRESS));

        object.get("alter3").set("some test value");
        executeCheckForFailure(addOp);

        object.remove("alter2");
        executeCheckForFailure(addOp);

        object.get("other").set("some test value");
        executeCheckNoFailure(addOp);

        executeCheckNoFailure(createOperation("remove", TEST_ADDRESS));
    }
}
