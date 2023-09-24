/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.test;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class TestUtils {

    static final OperationDefinition SETUP_OPERATION_DEF = new SimpleOperationDefinitionBuilder("setup", NonResolvingResourceDescriptionResolver.INSTANCE)
            .setPrivateEntry()
            .build();

    public static AttributeDefinition createAttribute(String name, ModelType type) {
        return createAttribute(name, type, null, false);
    }

    public static AttributeDefinition createNillableAttribute(String name, ModelType type) {
        return createNillableAttribute(name, type, false);
    }

    public static AttributeDefinition createNillableAttribute(String name, ModelType type, boolean runtimeOnly) {
        return createAttribute(name, type, null, runtimeOnly, false, true);
    }

    public static AttributeDefinition createAttribute(String name, ModelType type, String groupName) {
        return createAttribute(name, type, groupName, false);
    }

    public static AttributeDefinition createAttribute(String name, ModelType type, boolean runtimeOnly) {
        return createAttribute(name, type, null, runtimeOnly);
    }

    public static AttributeDefinition createAttribute(String name, ModelType type, String groupName, boolean runtimeOnly) {
        return createAttribute(name, type, groupName, runtimeOnly, false);
    }

    public static AttributeDefinition createAttribute(String name, ModelType type, String groupName, boolean runtimeOnly, boolean alias) {
        return createAttribute(name, type, groupName, runtimeOnly, alias, false);
    }


    public static AttributeDefinition createAttribute(String name, ModelType type, String groupName, boolean runtimeOnly, boolean alias, boolean allowNull) {
        SimpleAttributeDefinitionBuilder attribute = SimpleAttributeDefinitionBuilder.create(name, type);
        if (runtimeOnly) {
            attribute.setStorageRuntime();
        }
        if(groupName != null && ! groupName.isEmpty()) {
            attribute.setAttributeGroup(groupName);
        }
        if(alias) {
            attribute.addFlag(AttributeAccess.Flag.ALIAS);
        }
        attribute.setAllowExpression(true);
        if (allowNull) {
            attribute.setRequired(false);
        }
        return attribute.build();
    }

    public static AttributeDefinition createMetric(String name, ModelType type) {
        return createMetric(name, type, null);
    }

    public static AttributeDefinition createMetric(String name, ModelType type, String groupName) {
        SimpleAttributeDefinitionBuilder attribute = SimpleAttributeDefinitionBuilder.create(name, type).setStorageRuntime();
        if(groupName != null && ! groupName.isEmpty()) {
            attribute.setAttributeGroup(groupName);
        }
        if (type == ModelType.INT) {
            attribute.setUndefinedMetricValue(new ModelNode(-1));
        }
        if (type == ModelType.STRING) {
            attribute.setRequired(false);
        }
        return attribute.build();
    }

    public static OperationDefinition createOperationDefinition(String name, AttributeDefinition... parameters) {
        return new SimpleOperationDefinitionBuilder(name, NonResolvingResourceDescriptionResolver.INSTANCE)
                .setParameters(parameters)
                .build();
    }

    public static OperationDefinition createOperationDefinition(String name, boolean runtimeOnly, AttributeDefinition... parameters) {
        SimpleOperationDefinitionBuilder builder = new SimpleOperationDefinitionBuilder(name, NonResolvingResourceDescriptionResolver.INSTANCE)
                .setParameters(parameters);
        if (runtimeOnly) {
            builder.setRuntimeOnly();
        }
        return builder.build();
    }
}
