/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.controller.resources;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;

import java.util.Locale;
import java.util.ResourceBundle;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.constraint.SensitivityClassification;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.ProcessEnvironmentSystemPropertyUpdater;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.ServerEnvironmentSystemPropertyUpdater;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.as.server.operations.SystemPropertyAddHandler;
import org.jboss.as.server.operations.SystemPropertyRemoveHandler;
import org.jboss.as.server.operations.SystemPropertyValueWriteAttributeHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for system property configuration resources.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class SystemPropertyResourceDefinition extends SimpleResourceDefinition {

    public static final PathElement PATH = PathElement.pathElement(SYSTEM_PROPERTY);

    public static final SimpleAttributeDefinition VALUE = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.VALUE, ModelType.STRING, true)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(0, true, true))
            .build();

    public static final SimpleAttributeDefinition BOOT_TIME = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.BOOT_TIME, ModelType.BOOLEAN, true)
            .setValidator(new ModelTypeValidator(ModelType.BOOLEAN, true))
            .setDefaultValue(ModelNode.TRUE)
            .setAllowExpression(true)
            .build();

    static final AttributeDefinition[] ALL_ATTRIBUTES = new AttributeDefinition[] {VALUE, BOOT_TIME};
    static final AttributeDefinition[] SERVER_ATTRIBUTES = new AttributeDefinition[] {VALUE};

    final ProcessEnvironmentSystemPropertyUpdater systemPropertyUpdater;
    final boolean useBoottime;

    private SystemPropertyResourceDefinition(Location location, ProcessEnvironmentSystemPropertyUpdater systemPropertyUpdater, boolean useBoottime) {
        super(new Parameters(PATH, new ReplaceResourceNameResourceDescriptionResolver(location, SYSTEM_PROPERTY))
                .setAddHandler(new SystemPropertyAddHandler(systemPropertyUpdater, useBoottime ? ALL_ATTRIBUTES : SERVER_ATTRIBUTES))
                .setRemoveHandler(new SystemPropertyRemoveHandler(systemPropertyUpdater))
                .setAccessConstraints(new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.SYSTEM_PROPERTY)));
        this.systemPropertyUpdater = systemPropertyUpdater;
        this.useBoottime = useBoottime;
    }

    public static SystemPropertyResourceDefinition createForStandaloneServer(ServerEnvironment processEnvironment) {
        return new SystemPropertyResourceDefinition(Location.STANDALONE, new ServerEnvironmentSystemPropertyUpdater(processEnvironment), false);
    }

    public static SystemPropertyResourceDefinition createForDomainOrHost(Location location) {
        return new SystemPropertyResourceDefinition(location, null, true);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(VALUE, null, new SystemPropertyValueWriteAttributeHandler(systemPropertyUpdater));
        if (useBoottime) {
            resourceRegistration.registerReadWriteAttribute(BOOT_TIME, null, ModelOnlyWriteAttributeHandler.INSTANCE);
        }
    }

    private static class ReplaceResourceNameResourceDescriptionResolver extends StandardResourceDescriptionResolver {
        Location location;
        public ReplaceResourceNameResourceDescriptionResolver(Location location, String keyPrefix) {
            super(keyPrefix, ServerDescriptions.RESOURCE_NAME, SecurityActions.getClassLoader(ServerDescriptions.class), true, false);
            this.location = location;
        }

        public String getResourceDescription(Locale locale, ResourceBundle bundle) {
            //TODO - there should be a better way
            return bundle.getString(SYSTEM_PROPERTY + "." + location.getSuffix());
        }
    }

    public enum Location {
        STANDALONE("server"),
        DOMAIN("domain"),
        HOST("host"),
        SERVER_CONFIG("server-config"),
        SERVER_GROUP("server-group");

        private String suffix;

        Location(String suffix){
            this.suffix = suffix;
        }

        String getSuffix() {
            return suffix;
        }
    }
}
