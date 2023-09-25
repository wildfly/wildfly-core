/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.hamcrest.MatcherAssert.assertThat;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Test;

/**
 * Test to verify if https://issues.jboss.org/browse/AS7-1960 is an issue
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class BadReadHandlerAttributeTestCase extends AbstractControllerTestBase {

    private static ModelNode createdResource = null;

    @Test
    public void testCannotAccessAttributeWhenResourceDoesNotExist() throws Exception {
        assertThat(createdResource, is(nullValue()));
        //Just make sure it works as expected for an existent resource
        ModelNode op = Util.createAddOperation(PathAddress.pathAddress("test", "exists"));
        op.get(BASIC_ATTRIBUTE.getName()).set("cool");
        op.get(BAD_ATTRIBUTE.getName()).set("bad");
        executeCheckNoFailure(op);
        assertThat(createdResource, is(notNullValue()));
        assertThat(createdResource.has(BASIC_ATTRIBUTE.getName()), is(true));
        assertThat(createdResource.get(BASIC_ATTRIBUTE.getName()).isDefined(), is(true));
        assertThat(createdResource.get(BASIC_ATTRIBUTE.getName()).asString(), is("cool"));
        assertThat(createdResource.has(BAD_ATTRIBUTE.getName()), is(true));
        assertThat(createdResource.get(BAD_ATTRIBUTE.getName()).asString(), is("bad"));
        op = Util.createOperation(READ_RESOURCE_OPERATION, PathAddress.pathAddress("test", "exists"));
        op.get(RECURSIVE).set(true);
        ModelNode result = executeForResult(op);
        assertThat(result, is(notNullValue()));
        assertThat(result.has(BASIC_ATTRIBUTE.getName()), is(true));
        assertThat(result.get(BASIC_ATTRIBUTE.getName()).isDefined(), is(true));
        assertThat(result.get(BASIC_ATTRIBUTE.getName()).asString(), is("cool"));
        assertThat(result.has(BAD_ATTRIBUTE.getName()), is(true));
        assertThat(result.get(BAD_ATTRIBUTE.getName()).isDefined(), is(true));
        assertThat(result.get(BAD_ATTRIBUTE.getName()).asString(), is("bad"));


         createdResource = null;
        op = Util.createAddOperation(PathAddress.pathAddress("test", "wrong"));
        op.get(BASIC_ATTRIBUTE.getName()).set("cool");
        op.get(BAD_ATTRIBUTE.getName()).set("wrong");
        executeCheckNoFailure(op);

        op = Util.createOperation(READ_RESOURCE_OPERATION, PathAddress.pathAddress("test", "wrong"));
        op.get(RECURSIVE).set(true);
        executeCheckNoFailure(op);
        assertThat(createdResource, is(notNullValue()));
        assertThat(createdResource.has(BASIC_ATTRIBUTE.getName()), is(true));
        assertThat(createdResource.get(BASIC_ATTRIBUTE.getName()).isDefined(), is(true));
        assertThat(createdResource.get(BASIC_ATTRIBUTE.getName()).asString(), is("cool"));
        assertThat(createdResource.has(BAD_ATTRIBUTE.getName()), is(false));

        createdResource = null;
        op = Util.createAddOperation(PathAddress.pathAddress("test", "notthere"));
        op.get(BASIC_ATTRIBUTE.getName()).set("cool");
        executeCheckNoFailure(op);

        op = Util.createOperation(READ_RESOURCE_OPERATION, PathAddress.pathAddress("test", "notthere"));
        op.get(RECURSIVE).set(true);
        executeCheckNoFailure(op);
        assertThat(createdResource, is(notNullValue()));
        assertThat(createdResource.has(BASIC_ATTRIBUTE.getName()), is(true));
        assertThat(createdResource.get(BASIC_ATTRIBUTE.getName()).isDefined(), is(true));
        assertThat(createdResource.get(BASIC_ATTRIBUTE.getName()).asString(), is("cool"));
        assertThat(createdResource.has(BAD_ATTRIBUTE.getName()), is(false));

    }

    @Override
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration registration = managementModel.getRootResourceRegistration();
        GlobalOperationHandlers.registerGlobalOperations(registration, processType);
        GlobalNotifications.registerGlobalNotifications(registration, processType);
        registration.registerSubModel(new TestResource());
    }

    private static final AttributeDefinition BASIC_ATTRIBUTE = new SimpleAttributeDefinitionBuilder("attr", ModelType.STRING, true)
            .setDefaultValue(new ModelNode("default"))
            .build();

    private static final AttributeDefinition BAD_ATTRIBUTE = new SimpleAttributeDefinitionBuilder("bad_attr", ModelType.STRING, true)
            .build();

    private static class TestResource extends SimpleResourceDefinition {

        public TestResource() {
            super(PathElement.pathElement("test"), NonResolvingResourceDescriptionResolver.INSTANCE,
                    new TestResourceAddHandler(), new AbstractRemoveStepHandler() {
                    });
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerReadOnlyAttribute(BASIC_ATTRIBUTE, null);
            resourceRegistration.registerReadOnlyAttribute(BAD_ATTRIBUTE, null);
        }
    }

    private static class TestResourceAddHandler extends AbstractAddStepHandler {

        @Override
        protected Resource createResource(OperationContext context) {
            Resource resource = super.createResource(context);
            BadReadHandlerAttributeTestCase.createdResource = resource.getModel();
            return resource;
        }

        @Override
        protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
            BASIC_ATTRIBUTE.validateAndSet(operation, model);
            if (operation.hasDefined(BAD_ATTRIBUTE.getName()) && "bad".equals(operation.get(BAD_ATTRIBUTE.getName()).asString())) {
                BAD_ATTRIBUTE.validateAndSet(operation, model);
            }
        }
    }
}
