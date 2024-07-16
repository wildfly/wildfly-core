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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReloadRequiredAddStepHandler;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.dmr.ModelNode;
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

    // this was supposed to be the attribute definition's default value, but using it as a default results in an error when
    // trying to obtain the MBeanInfo for the security manager subsystem because default lists are not supported. The error
    // message 'Default value not supported for ArrayType and TabularType' is displayed in an exception thrown by the
    // javax.management.openmbean.OpenMBeanInfoSupport class.
    static final ModelNode DEFAULT_MAXIMUM_SET;
    static {
        final ModelNode defaultPermission = new ModelNode();
        defaultPermission.get(PERMISSION_CLASS).set("java.security.AllPermission");
        DEFAULT_MAXIMUM_SET = new ModelNode().add(defaultPermission);
    }

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
                ReloadRequiredAddStepHandler.INSTANCE, ReloadRequiredRemoveStepHandler.INSTANCE);
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Collections.unmodifiableCollection(Arrays.asList(ATTRIBUTES));
    }
}
