/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.access;

import org.jboss.as.controller.ParameterCorrector;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_SCOPED_ROLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP_SCOPED_ROLE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.access.management.WritableAuthorizerConfiguration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.management.CoreManagementResourceDefinition;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;

/**
 * An extension so {@link AbstractAddStepHandler} to add verification that a scoped role is not a duplicate entry.
 *
 * Within the model scoped roles are added using case sensitive addresses, in addition to this the roles can be added as host
 * scoped roles OR server group scoped roles so the additional verification checks for duplicates.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
abstract class ScopedRoleAddHandler extends AbstractAddStepHandler {

    private static final PathAddress AUTHZ_ADDRESS = PathAddress.pathAddress(CoreManagementResourceDefinition.PATH_ELEMENT,
            AccessAuthorizationResourceDefinition.PATH_ELEMENT);
    private final WritableAuthorizerConfiguration authorizerConfiguration;

    ScopedRoleAddHandler(final WritableAuthorizerConfiguration authorizerConfiguration, AttributeDefinition... attributes) {
        super(enhanceAttributes(authorizerConfiguration, attributes));
        this.authorizerConfiguration = authorizerConfiguration;
    }

    private static Collection<? extends AttributeDefinition> enhanceAttributes(
            final WritableAuthorizerConfiguration authorizerConfiguration, AttributeDefinition... attributes) {
        List<AttributeDefinition> enhanced = new ArrayList<AttributeDefinition>(attributes.length);
        for (AttributeDefinition current : attributes) {
            if (current.getName().equals(ModelDescriptionConstants.BASE_ROLE)) {
                assert current instanceof SimpleAttributeDefinition;
                enhanced.add(new SimpleAttributeDefinitionBuilder((SimpleAttributeDefinition)current)
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
                }).build());
            } else {
                enhanced.add(current);
            }
        }

        return enhanced;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        final PathElement lastElement = address.getLastElement();
        final String roleName = lastElement.getValue();

        Set<String> standardRoles = authorizerConfiguration.getStandardRoles();
        for (String current : standardRoles) {
            if (roleName.equalsIgnoreCase(current)) {
                throw DomainManagementLogger.ROOT_LOGGER.scopedRoleStandardName(roleName, current);
            }
        }

        Resource readResource = context.readResourceFromRoot(AUTHZ_ADDRESS, false);
        Set<String> hostScopedRoles = readResource.getChildrenNames(HOST_SCOPED_ROLE);
        for (String current : hostScopedRoles) {
            if (roleName.equalsIgnoreCase(current)) {
                throw DomainManagementLogger.ROOT_LOGGER.duplicateScopedRole(HOST_SCOPED_ROLE, roleName);
            }
        }

        Set<String> serverGroupScopedRoles = readResource.getChildrenNames(SERVER_GROUP_SCOPED_ROLE);
        for (String current : serverGroupScopedRoles) {
            if (roleName.equalsIgnoreCase(current)) {
                throw DomainManagementLogger.ROOT_LOGGER.duplicateScopedRole(SERVER_GROUP_SCOPED_ROLE, roleName);
            }
        }

        super.execute(context, operation);
    }

}
