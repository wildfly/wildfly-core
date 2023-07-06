/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.access;

import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ParameterCorrector;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.WritableAuthorizerConfiguration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Base {@link org.jboss.as.controller.ResourceDefinition} for scoped roles
 */
public abstract class ScopedRoleResourceDefinition extends SimpleResourceDefinition {

    public static final SimpleAttributeDefinition BASE_ROLE =
            new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.BASE_ROLE, ModelType.STRING)
            .setRestartAllServices()
            .build();

    private final AttributeDefinition roleAttribute;

    protected ScopedRoleResourceDefinition(PathElement path, ResourceDescriptionResolver resolver, WritableAuthorizerConfiguration authorizerConfiguration) {
        super(path, resolver);
        this.roleAttribute = new SimpleAttributeDefinitionBuilder(BASE_ROLE)
                .setValidator(new ParameterValidator() {
                    @Override
                    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
                        Set<String> standardRoles = authorizerConfiguration.getStandardRoles();
                        String specifiedRole = value.asString();
                        for (String current : standardRoles) {
                            if (specifiedRole.equalsIgnoreCase(current)) {
                                return;
                            }
                        }

                        throw DomainManagementLogger.ROOT_LOGGER.badBaseRole(specifiedRole);
                    }
                }).setCorrector(new ParameterCorrector() {
                    @Override
                    public ModelNode correct(ModelNode newValue, ModelNode currentValue) {
                        Set<String> standardRoles = authorizerConfiguration.getStandardRoles();
                        String specifiedRole = newValue.asString();

                        for (String current : standardRoles) {
                            if (specifiedRole.equalsIgnoreCase(current) && specifiedRole.equals(current) == false) {
                                return new ModelNode(current);
                            }
                        }

                        return newValue;
                    }
                })
                .build();
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        registration.registerReadWriteAttribute(this.roleAttribute, null, ReloadRequiredWriteAttributeHandler.INSTANCE);
    }
}
