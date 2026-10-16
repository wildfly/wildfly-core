/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.security.manager;

import static org.wildfly.extension.security.manager.Constants.MAXIMUM_SET;
import static org.wildfly.extension.security.manager.Constants.MINIMUM_SET;
import static org.wildfly.extension.security.manager.Constants.PERMISSION;
import static org.wildfly.extension.security.manager.Constants.PERMISSION_ACTIONS;
import static org.wildfly.extension.security.manager.Constants.PERMISSION_CLASS;
import static org.wildfly.extension.security.manager.Constants.PERMISSION_MODULE;
import static org.wildfly.extension.security.manager.Constants.PERMISSION_NAME;

import java.util.Collection;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.dmr.ModelType;

/**
 * Defines a resource that represents the security permissions that can be assigned to deployments.
 *
 * @author <a href="sguilhen@jboss.com">Stefan Guilhen</a>
 */
class DeploymentPermissionsResourceDefinition extends PersistentResourceDefinition {


    static final SimpleAttributeDefinition CLASS = new SimpleAttributeDefinitionBuilder(PERMISSION_CLASS, ModelType.STRING)
            .setRequired(true)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .build();

    static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(PERMISSION_NAME, ModelType.STRING)
            .setRequired(false)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .build();

    static final SimpleAttributeDefinition ACTIONS = new SimpleAttributeDefinitionBuilder(PERMISSION_ACTIONS, ModelType.STRING)
            .setRequired(false)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .build();

    static final SimpleAttributeDefinition MODULE = new SimpleAttributeDefinitionBuilder(PERMISSION_MODULE, ModelType.STRING)
            .setRequired(false)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .build();

    private static final ObjectTypeAttributeDefinition PERMISSIONS_VALUE_TYPE =
            ObjectTypeAttributeDefinition.Builder.of(PERMISSION, CLASS, NAME, ACTIONS, MODULE).build();

    static final AttributeDefinition MAXIMUM_PERMISSIONS =
            ObjectListAttributeDefinition.Builder.of(Constants.MAXIMUM_PERMISSIONS, PERMISSIONS_VALUE_TYPE)
                    .setRequired(false)
                    .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                    .setXmlName(MAXIMUM_SET)
                    .build();

    static final AttributeDefinition MINIMUM_PERMISSIONS =
            ObjectListAttributeDefinition.Builder.of(Constants.MINIMUM_PERMISSIONS, PERMISSIONS_VALUE_TYPE)
                    .setRequired(false)
                    .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                    .setXmlName(MINIMUM_SET)
                    .build();

    static final PathElement DEPLOYMENT_PERMISSIONS_PATH = PathElement.pathElement(
            Constants.DEPLOYMENT_PERMISSIONS, Constants.DEFAULT_VALUE);

    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{MINIMUM_PERMISSIONS, MAXIMUM_PERMISSIONS};

    static final DeploymentPermissionsResourceDefinition INSTANCE = new DeploymentPermissionsResourceDefinition();

    private DeploymentPermissionsResourceDefinition() {
        super(DEPLOYMENT_PERMISSIONS_PATH, SecurityManagerExtension.getResolver(Constants.DEPLOYMENT_PERMISSIONS),
                ModelOnlyAddStepHandler.INSTANCE, ModelOnlyRemoveStepHandler.INSTANCE);
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return List.of(ATTRIBUTES);
    }
}
