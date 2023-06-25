/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.EnvVarAttributeOverrider.replaceNonAlphanumericByUnderscoreAndMakeUpperCase;
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
import static org.junit.Assert.assertFalse;

import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.test.AbstractControllerTestBase;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author jmesnil
 */
public abstract class EnvVarAttributeOverrideModel extends AbstractControllerTestBase {
    private static final SimpleAttributeDefinition MY_ATTR = new SimpleAttributeDefinitionBuilder("my-attr", ModelType.INT, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleListAttributeDefinition MY_LIST_ATTR = new SimpleListAttributeDefinition.Builder("my-list-attr", MY_ATTR)
            .setRequired(false)
            .build();

    private static final PathAddress CUSTOM_RESOURCE_ADDR = PathAddress.pathAddress("subsystem", "custom");
    private static final int VALUE_FROM_MODEL = 1234;
    private static final int VALUE_FROM_ENV_VAR = 5678;

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
                NonResolvingResourceDescriptionResolver.INSTANCE)
                .setAddOperation(new ModelOnlyAddStepHandler())
                .setRemoveOperation(ReloadRequiredRemoveStepHandler.INSTANCE)
                .addReadWriteAttribute(MY_ATTR, null, ReloadRequiredWriteAttributeHandler.INSTANCE)
                .addReadWriteAttribute(MY_LIST_ATTR, null, ReloadRequiredWriteAttributeHandler.INSTANCE)
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
    protected void testOverriddenSpecifiedAttributeValue(boolean featureEnabled) throws OperationFailedException {
        ModelNode addOp = createOperation("add", CUSTOM_RESOURCE_ADDR);
        addOp.get(MY_ATTR.getName()).set("1234");
        executeCheckNoFailure(addOp);

        ModelNode readResource = createOperation(READ_RESOURCE_OPERATION, CUSTOM_RESOURCE_ADDR);
        executeCheckNoFailure(readResource);

        ModelNode readAttribute = createOperation(READ_ATTRIBUTE_OPERATION, CUSTOM_RESOURCE_ADDR);
        readAttribute.get(NAME).set(MY_ATTR.getName());
        ModelNode response = executeCheckNoFailure(readAttribute);
        if (featureEnabled) {
            assertEquals(VALUE_FROM_ENV_VAR, response.get(RESULT).asInt());
        } else {
            assertEquals(VALUE_FROM_MODEL, response.get(RESULT).asInt());
        }
    }

    /**
     * This test creates a resource with an undefined attribute.
     * The test runs with an env var corresponding to the resource attribute set to "value2"
     *
     * When we read the attribute value we verify that the value comes from the env var (i.e. "value2").
     *
     * @throws OperationFailedException
     */
    protected void testOverriddenOptionalAttributeValue(boolean featureEnabled) throws OperationFailedException {
        ModelNode addOp = createOperation("add", CUSTOM_RESOURCE_ADDR);
        executeCheckNoFailure(addOp);

        ModelNode readResource = createOperation(READ_RESOURCE_OPERATION, CUSTOM_RESOURCE_ADDR);
        executeCheckNoFailure(readResource);

        ModelNode readAttribute = createOperation(READ_ATTRIBUTE_OPERATION, CUSTOM_RESOURCE_ADDR);
        readAttribute.get(NAME).set(MY_ATTR.getName());
        ModelNode response = executeCheckNoFailure(readAttribute);
        if (featureEnabled) {
            assertEquals(VALUE_FROM_ENV_VAR, response.get(RESULT).asInt());
        } else {
            assertFalse(response.get(RESULT).isDefined());
        }
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

    public static final class EnabledOverridingEnvVarTestCase extends EnvVarAttributeOverrideModel {

        @BeforeClass
        public static void setup() {
            Assume.assumeTrue(EnvVarAttributeOverrider.isEnabled());
        }

        @Test
        public void testOverriddenSpecifiedAttributeValue() throws OperationFailedException {
            testOverriddenSpecifiedAttributeValue(true);
        }

        @Test
        public void testOverriddenOptionalAttributeValue() throws OperationFailedException {
            testOverriddenOptionalAttributeValue(true);
        }

        @Test
        public void textComplexAttributeIsIgnored() throws OperationFailedException {
            // verify that there is an env var matching the my-list-addr attribute
            String envVarName = replaceNonAlphanumericByUnderscoreAndMakeUpperCase(CUSTOM_RESOURCE_ADDR, MY_LIST_ATTR.getName());
            assertFalse(System.getenv(envVarName).isEmpty());

            ModelNode addOp = createOperation("add", CUSTOM_RESOURCE_ADDR);
            executeCheckNoFailure(addOp);

            ModelNode readResource = createOperation(READ_RESOURCE_OPERATION, CUSTOM_RESOURCE_ADDR);
            executeCheckNoFailure(readResource);

            ModelNode readAttribute = createOperation(READ_ATTRIBUTE_OPERATION, CUSTOM_RESOURCE_ADDR);
            readAttribute.get(NAME).set(MY_LIST_ATTR.getName());
            ModelNode response = executeCheckNoFailure(readAttribute);
            assertFalse(response.get(RESULT).isDefined());
        }
    }

    public static final class DisabledOverridingEnvVarTestCase extends EnvVarAttributeOverrideModel {

        @BeforeClass
        public static void setup() {
            Assume.assumeFalse(EnvVarAttributeOverrider.isEnabled());
        }

        @Test
        public void testOverriddenSpecifiedAttributeValue() throws OperationFailedException {
            testOverriddenSpecifiedAttributeValue(false);
        }

        @Test
        public void testOverriddenOptionalAttributeValue() throws OperationFailedException {
            testOverriddenOptionalAttributeValue(false);
        }
    }
}
