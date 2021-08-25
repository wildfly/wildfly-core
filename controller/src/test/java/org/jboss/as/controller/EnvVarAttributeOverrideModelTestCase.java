/*
 * JBoss, Home of Professional Open Source
 * Copyright 2021, JBoss Inc., and individual contributors as indicated
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

import static org.jboss.as.controller.AttributeDefinition.replaceNonAlphanumericByUnderscoreAndMakeUpperCase;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEFAULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.test.AbstractControllerTestBase;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Test;

/**
 * @author jmesnil
 */
public class EnvVarAttributeOverrideModelTestCase extends AbstractControllerTestBase {
    private static final SimpleAttributeDefinition MY_ATTR = new SimpleAttributeDefinitionBuilder("my-attr", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();

    private static PathAddress CUSTOM_RESOURCE_ADDR = PathAddress.pathAddress("subsystem", "custom");

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
        return ResourceBuilder.Factory.create(CUSTOM_RESOURCE_ADDR.getElement(0),
                new NonResolvingResourceDescriptionResolver())
                .setAddOperation(new AbstractAddStepHandler(Arrays.asList(MY_ATTR)))
                .setRemoveOperation(ReloadRequiredRemoveStepHandler.INSTANCE)
                .addReadWriteAttribute(MY_ATTR, null, new ReloadRequiredWriteAttributeHandler(MY_ATTR))
                .build();
    }

    /**
     * This test creates a resource with an attribute set to "value1".
     * The test runs with an env var corresponding to the resource attribute set to "value2"
     *
     * When we read the attribute value we verify that the value comes from the env var (i.e. "value2").
     *
     * @throws OperationFailedException
     */
    @Test
    public void testOverridenSpecifiedAttributeValue() throws OperationFailedException {
        ModelNode addOp = createOperation("add", CUSTOM_RESOURCE_ADDR);
        addOp.get(MY_ATTR.getName()).set("value1");
        executeCheckNoFailure(addOp);

        ModelNode readResource = createOperation(READ_RESOURCE_OPERATION, CUSTOM_RESOURCE_ADDR);
        executeCheckNoFailure(readResource);

        ModelNode readAttribute = createOperation(READ_ATTRIBUTE_OPERATION, CUSTOM_RESOURCE_ADDR);
        readAttribute.get(NAME).set(MY_ATTR.getName());
        ModelNode response = executeCheckNoFailure(readAttribute);
        assertEquals("value2", response.get(RESULT).asString());
    }

    /**
     * This test creates a resource with an undefined attribute.
     * The test runs with an env var corresponding to the resource attribute set to "value2"
     *
     * When we read the attribute value we verify that the value comes from the env var (i.e. "value2").
     *
     * @throws OperationFailedException
     */
    @Test
    public void testOverridenOptionalAttributeValue() throws OperationFailedException {
        ModelNode addOp = createOperation("add", CUSTOM_RESOURCE_ADDR);
        executeCheckNoFailure(addOp);

        ModelNode readResource = createOperation(READ_RESOURCE_OPERATION, CUSTOM_RESOURCE_ADDR);
        executeCheckNoFailure(readResource);

        ModelNode readAttribute = createOperation(READ_ATTRIBUTE_OPERATION, CUSTOM_RESOURCE_ADDR);
        readAttribute.get(NAME).set(MY_ATTR.getName());
        ModelNode response = executeCheckNoFailure(readAttribute);
        assertEquals("value2", response.get(RESULT).asString());
    }

    @Test
    public void testAttributeMapping() {
        // /interface=management:read-attribute(name=inet-address)
        assertEquals("INTERFACE_MANAGEMENT__INET_ADDRESS",
                replaceNonAlphanumericByUnderscoreAndMakeUpperCase(PathAddress.pathAddress(INTERFACE, MANAGEMENT), "inet-address"));

        // /subsystem=undertow/server=default-server/http-listener=default:read-attribute(name=proxy-address-forwarding)
        assertEquals("SUBSYSTEM_UNDERTOW_SERVER_DEFAULT_SERVER_HTTP_LISTENER_DEFAULT__PROXY_ADDRESS_FORWARDING",
                replaceNonAlphanumericByUnderscoreAndMakeUpperCase(PathAddress.pathAddress(SUBSYSTEM, "undertow").append(SERVER, "default-server").append("http-listener", DEFAULT),
                        "proxy-address-forwarding"));

        // /resource=a:read-attribute(name=b-c)
        assertEquals("RESOURCE_A__B_C",
                replaceNonAlphanumericByUnderscoreAndMakeUpperCase(PathAddress.pathAddress("resource", "a"), "b-c"));
        // /resource=a-b:read-attribute(name=c)
        assertEquals("RESOURCE_A_B__C",
                replaceNonAlphanumericByUnderscoreAndMakeUpperCase(PathAddress.pathAddress("resource", "a-b"), "c"));
    }
}
