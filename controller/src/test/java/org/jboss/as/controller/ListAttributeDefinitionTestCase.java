/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.SimpleAttributeDefinitionBuilder.create;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXPRESSIONS_ALLOWED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NILLABLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE_TYPE;
import static org.jboss.dmr.ModelType.LIST;
import static org.jboss.dmr.ModelType.OBJECT;
import static org.jboss.dmr.ModelType.STRING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.descriptions.DefaultResourceDescriptionProvider;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;
import org.junit.Test;

//todo consider using ResourceBuilder instead of ucstom impl of ResourceDefinition
public class ListAttributeDefinitionTestCase {

    private static final String MY_RESOURCE = "my-resource";
    private static final String MY_LIST_OF_STRINGS = "my-list-of-strings";
    private static final String MY_LIST_OF_OBJECTS = "my-list-of-objects";
    private static final String TYPE1 = "type1";
    private static final String TYPE2 = "type2";

    @Test
    public void testPrimitiveListAttributeDescription() {
        ResourceDefinition resource = new ResourceDefinition() {
            @Override
            public void registerOperations(ManagementResourceRegistration resourceRegistration) {
            }

            @Override
            public void registerChildren(ManagementResourceRegistration resourceRegistration) {
            }

            @Override
            public void registerNotifications(ManagementResourceRegistration resourceRegistration) {
            }

            @Override
            public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
                final PrimitiveListAttributeDefinition attr = PrimitiveListAttributeDefinition.Builder.of(MY_LIST_OF_STRINGS, STRING).build();
                resourceRegistration.registerReadOnlyAttribute(attr, null);
            }

            @Override
            public void registerCapabilities(ManagementResourceRegistration resourceRegistration) {
                //no op
            }

            @Override
            public PathElement getPathElement() {
                return PathElement.pathElement(MY_RESOURCE);
            }

            @Override
            public DescriptionProvider getDescriptionProvider(ImmutableManagementResourceRegistration registration) {
                StandardResourceDescriptionResolver descriptionResolver = new StandardResourceDescriptionResolver(MY_RESOURCE, ListAttributeDefinitionTestCase.class.getName(), Thread.currentThread().getContextClassLoader());
                return new DefaultResourceDescriptionProvider(registration, descriptionResolver);
            }

            @Override
            public List<AccessConstraintDefinition> getAccessConstraints() {
                return Collections.emptyList();
            }

            @Override
            public boolean isRuntime() {
                return false;
            }

            @Override
            public boolean isOrderedChild() {
                return false;
            }
        };

        ImmutableManagementResourceRegistration registration = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER).createRegistration(resource);
        ModelNode modelDescription = resource.getDescriptionProvider(registration).getModelDescription(Locale.ENGLISH);
        assertEquals("incorrect type for description " + modelDescription, LIST, modelDescription.get(ATTRIBUTES, MY_LIST_OF_STRINGS, TYPE).asType());
        assertEquals("incorrect value-type for description " + modelDescription, STRING, modelDescription.get(ATTRIBUTES, MY_LIST_OF_STRINGS, VALUE_TYPE).asType());
    }

    @Test
    public void testObjectListAttributeDescription() {
        ResourceDefinition resource = new ResourceDefinition() {
            @Override
            public void registerOperations(ManagementResourceRegistration resourceRegistration) {
            }

            @Override
            public void registerChildren(ManagementResourceRegistration resourceRegistration) {
            }

            @Override
            public void registerNotifications(ManagementResourceRegistration resourceRegistration) {
            }

            @Override
            public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
                AttributeDefinition type1 = create(TYPE1, ModelType.STRING)
                        .setRequired(false)
                        .build();
                AttributeDefinition type2 = create(TYPE2, ModelType.BOOLEAN)
                        .setRequired(true)
                        .build();
                ObjectTypeAttributeDefinition objectDefinition = ObjectTypeAttributeDefinition.Builder.of("objects", type1, type2).build();
                ObjectListAttributeDefinition attr = ObjectListAttributeDefinition.Builder.of(MY_LIST_OF_OBJECTS, objectDefinition).build();

                resourceRegistration.registerReadOnlyAttribute(attr, null);
            }

            @Override
            public PathElement getPathElement() {
                return PathElement.pathElement(MY_RESOURCE);
            }

            @Override
            public DescriptionProvider getDescriptionProvider(ImmutableManagementResourceRegistration registration) {
                StandardResourceDescriptionResolver descriptionResolver = new StandardResourceDescriptionResolver(MY_RESOURCE, ListAttributeDefinitionTestCase.class.getName(), Thread.currentThread().getContextClassLoader());
                return new DefaultResourceDescriptionProvider(registration, descriptionResolver);
            }

            @Override
            public List<AccessConstraintDefinition> getAccessConstraints() {
                return Collections.emptyList();
            }

            @Override
            public boolean isRuntime() {
                return false;
            }

            @Override
            public boolean isOrderedChild() {
                return false;
            }

            @Override
            public void registerCapabilities(ManagementResourceRegistration resourceRegistration) {
            }
        };

        ImmutableManagementResourceRegistration registration = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER).createRegistration(resource);
        ModelNode modelDescription = resource.getDescriptionProvider(registration).getModelDescription(Locale.ENGLISH);
        assertEquals("incorrect type for description " + modelDescription, LIST, modelDescription.get(ATTRIBUTES, MY_LIST_OF_OBJECTS, TYPE).asType());
        assertEquals("incorrect value-type for description " + modelDescription, OBJECT, modelDescription.get(ATTRIBUTES, MY_LIST_OF_OBJECTS, VALUE_TYPE).getType());

        assertTrue(modelDescription.get(ATTRIBUTES, MY_LIST_OF_OBJECTS, VALUE_TYPE).hasDefined(TYPE1));
        assertEquals(true, modelDescription.get(ATTRIBUTES, MY_LIST_OF_OBJECTS, VALUE_TYPE, TYPE1, NILLABLE).asBoolean());

        assertTrue(modelDescription.get(ATTRIBUTES, MY_LIST_OF_OBJECTS, VALUE_TYPE).hasDefined(TYPE2));
        assertEquals(false, modelDescription.get(ATTRIBUTES, MY_LIST_OF_OBJECTS, VALUE_TYPE, TYPE2, NILLABLE).asBoolean());
    }

    @Test
    public void testStringList() {
        StringListAttributeDefinition list = new StringListAttributeDefinition.Builder("string-list")
                .setAllowExpression(true)
                .setRequired(true)
                .build();
        assertEquals(ModelType.STRING, list.getValueAttributeDefinition().getType());
        ModelNode desc = list.getNoTextDescription(false);
        ModelNode expressionNode = desc.get(EXPRESSIONS_ALLOWED);
        assertNotNull("Expression element should be present!", expressionNode);
        Assert.assertTrue("expressions should be supported", expressionNode.asBoolean());
    }
}
