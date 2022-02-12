/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.controller.test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_ALIASES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_GROUP_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_GROUP_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESOLVE_EXPRESSIONS;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.dmr.ValueExpression;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class ReadAttributeGroupTestCase extends AbstractControllerTestBase {
    @BeforeClass
    public static void setSystemPropertyForResolution() {
        System.setProperty("my.value", "resolved");
    }

    @Test
    public void testAttributeGroupNames() throws Exception {
        ModelNode operation = createOperation(READ_ATTRIBUTE_GROUP_NAMES_OPERATION, "subsystem", "basicSubsystem");

        ModelNode result = executeForResult(operation);
        assertNotNull(result);
        List<ModelNode> groupNames = result.asList();
        assertThat(groupNames, is(notNullValue()));
        assertThat(groupNames.size(), is(2));
        Set<String> groups = new HashSet<>(2);
        for(ModelNode groupName : groupNames) {
            groups.add(groupName.asString());
        }
        assertThat(groups, hasItems("group1", "group2"));
    }

    @Test
    public void testSimpleAttributeGroup() throws Exception {
        ModelNode operation = createOperation(READ_ATTRIBUTE_GROUP_OPERATION, "subsystem", "basicSubsystem");
        operation.get(NAME).set("group1");

        ModelNode result = executeForResult(operation);
        assertNotNull(result);
        List<Property> attributesPerGroup = result.asPropertyList();
        assertThat(attributesPerGroup, is(notNullValue()));
        assertThat(attributesPerGroup.size(), is(3));
        LinkedHashMap<String, String> attributes = mapAttributes(attributesPerGroup);
        assertThat(attributes.keySet(), hasItems("eigth", "first", "second"));
        assertThat(attributes.values(), hasItems("configuration1", "${my.value}", "undefined"));
    }

    @Test
    public void testRuntimeAttributeGroup() throws Exception {
        ModelNode operation = createOperation(READ_ATTRIBUTE_GROUP_OPERATION, "subsystem", "basicSubsystem");
        operation.get(NAME).set("group1");
        operation.get(INCLUDE_RUNTIME).set(true);

        ModelNode result = executeForResult(operation);
        assertNotNull(result);
        List<Property> attributesPerGroup = result.asPropertyList();
        assertThat(attributesPerGroup, is(notNullValue()));
        assertThat(attributesPerGroup.size(), is(4));
        LinkedHashMap<String, String> attributes = mapAttributes(attributesPerGroup);
        assertThat(attributes.keySet(), hasItems("eigth", "fifth", "first", "second"));
        assertThat(attributes.values(), hasItems("configuration1", "runtime5", "${my.value}", "undefined"));
    }

    @Test
    public void testResolveAttributeGroup() throws Exception {
        ModelNode operation = createOperation(READ_ATTRIBUTE_GROUP_OPERATION, "subsystem", "basicSubsystem");
        operation.get(NAME).set("group1");
        operation.get(RESOLVE_EXPRESSIONS).set(true);

        ModelNode result = executeForResult(operation);
        assertNotNull(result);
        List<Property> attributesPerGroup = result.asPropertyList();
        assertThat(attributesPerGroup, is(notNullValue()));
        assertThat(attributesPerGroup.size(), is(3));
        LinkedHashMap<String, String> attributes = mapAttributes(attributesPerGroup);
        assertThat(attributes.keySet(), hasItems("eigth", "first", "second"));
        assertThat(attributes.values(), hasItems("configuration1", "resolved", "undefined"));
    }

     @Test
    public void testAliasAttributeGroup() throws Exception {
        ModelNode operation = createOperation(READ_ATTRIBUTE_GROUP_OPERATION, "subsystem", "basicSubsystem");
        operation.get(NAME).set("group2");
        operation.get(INCLUDE_ALIASES).set(true);

        ModelNode result = executeForResult(operation);
        assertNotNull(result);
        List<Property> attributesPerGroup = result.asPropertyList();
        assertThat(attributesPerGroup, is(notNullValue()));
        assertThat(attributesPerGroup.size(), is(3));
        LinkedHashMap<String, String> attributes = mapAttributes(attributesPerGroup);
        assertThat(attributes.keySet(), hasItems("seventh", "sixth", "third"));
        assertThat(attributes.values(), hasItems("configuration6", "configuration6", "configuration3"));
    }

    private LinkedHashMap<String, String> mapAttributes(List<Property> attributes) {
        LinkedHashMap<String, String> result = new LinkedHashMap<String, String>(attributes.size());
        for (Property attribute : attributes) {
            result.put(attribute.getName(), attribute.getValue().asString());
        }
        return result;
    }

    @Override
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration registration = managementModel.getRootResourceRegistration();
        GlobalOperationHandlers.registerGlobalOperations(registration, processType);
        GlobalNotifications.registerGlobalNotifications(registration, processType);

        ManagementResourceRegistration basicResourceRegistration = registration.registerSubModel(
                new SimpleResourceDefinition(PathElement.pathElement("subsystem", "basicSubsystem"), NonResolvingResourceDescriptionResolver.INSTANCE));
        basicResourceRegistration.registerReadOnlyAttribute(TestUtils.createAttribute("first", ModelType.STRING, "group1", false), null);
        basicResourceRegistration.registerReadOnlyAttribute(TestUtils.createAttribute("second", ModelType.STRING, "group1", false), null);
        basicResourceRegistration.registerReadOnlyAttribute(TestUtils.createAttribute("third", ModelType.STRING, "group2", false), null);
        basicResourceRegistration.registerReadOnlyAttribute(TestUtils.createAttribute("fourth", ModelType.STRING, "group2", true), null);
        basicResourceRegistration.registerReadOnlyAttribute(TestUtils.createAttribute("fifth", ModelType.STRING, "group1", true), null);
        basicResourceRegistration.registerReadOnlyAttribute(TestUtils.createAttribute("sixth", ModelType.STRING, "group2", false, false), null);
        basicResourceRegistration.registerReadOnlyAttribute(TestUtils.createAttribute("seventh", ModelType.STRING, "group2", false, true), ShowModelAliasReadHandler.INSTANCE);
        basicResourceRegistration.registerReadOnlyAttribute(TestUtils.createAttribute("eigth", ModelType.STRING, "group1", false, false, true), null);

        registration.registerOperationHandler(TestUtils.SETUP_OPERATION_DEF, new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                final ModelNode model = new ModelNode();
                //Atttributes
                model.get("subsystem", "basicSubsystem", "first").set("configuration1");
                model.get("subsystem", "basicSubsystem", "second").set(new ValueExpression("${my.value}"));
                model.get("subsystem", "basicSubsystem", "third").set("configuration3");
                model.get("subsystem", "basicSubsystem", "fourth").set("runtime4");
                model.get("subsystem", "basicSubsystem", "fifth").set("runtime5");
                model.get("subsystem", "basicSubsystem", "sixth").set("configuration6");
                model.get("subsystem", "basicSubsystem", "seventh").set("alias7");
                createModel(context, model);
            }
        }
        );
    }

    private static class ShowModelAliasReadHandler implements OperationStepHandler {

        static final ShowModelAliasReadHandler INSTANCE = new ShowModelAliasReadHandler();

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
            if (resource.getModel().hasDefined("sixth")) {
                context.getResult().set(resource.getModel().get("sixth").asString());
            }
        }
    }
}
