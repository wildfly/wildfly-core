/*
 *
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2014, Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags. See the copyright.txt file in the
 *  distribution for a full listing of individual contributors.
 *
 *  This is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this software; if not, write to the Free
 *  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  02110-1301 USA, or see the FSF site: http://www.fsf.org.
 * /
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

    public static final OperationDefinition SETUP_OPERATION_DEF = new SimpleOperationDefinitionBuilder("setup", new NonResolvingResourceDescriptionResolver())
            .setPrivateEntry()
            .build();

    public static AttributeDefinition createAttribute(String name, ModelType type) {
        return createAttribute(name, type, null, false);
    }
    public static AttributeDefinition createNillableAttribute(String name, ModelType type) {
        return createAttribute(name, type, null, true);
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
            attribute.setAllowNull(true);
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
            attribute.setAllowNull(true);
        }
        return attribute.build();
    }

    public static OperationDefinition createOperationDefinition(String name, AttributeDefinition... parameters) {
        return new SimpleOperationDefinitionBuilder(name, new NonResolvingResourceDescriptionResolver())
                .setParameters(parameters)
                .build();
    }

    public static OperationDefinition createOperationDefinition(String name, boolean runtimeOnly, AttributeDefinition... parameters) {
        SimpleOperationDefinitionBuilder builder = new SimpleOperationDefinitionBuilder(name, new NonResolvingResourceDescriptionResolver())
                .setParameters(parameters);
        if (runtimeOnly) {
            builder.setRuntimeOnly();
        }
        return builder.build();
    }
}
