/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.domain.management.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHORIZATION;

import java.util.Arrays;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.access.CombinationPolicy;
import org.jboss.as.controller.access.management.AccessConstraintUtilizationRegistry;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.access.management.WritableAuthorizerConfiguration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for the Access Control model.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class AccessAuthorizationResourceDefinition extends SimpleResourceDefinition {

    public static final PathElement PATH_ELEMENT = PathElement.pathElement(ACCESS, AUTHORIZATION);

    public enum Provider {
        SIMPLE("simple"),
        RBAC("rbac");

        private final String toString;

        private Provider(String toString) {
            this.toString = toString;
        }

        @Override
        public String toString() {
            return toString;
        }
    }

    public static final SimpleAttributeDefinition PERMISSION_COMBINATION_POLICY =
            new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PERMISSION_COMBINATION_POLICY, ModelType.STRING, true)
            .setDefaultValue(new ModelNode(CombinationPolicy.PERMISSIVE.toString()))
            .setValidator(EnumValidator.create(CombinationPolicy.class))
            .build();

    public static final SimpleAttributeDefinition PROVIDER = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PROVIDER, ModelType.STRING, true)
            .setDefaultValue(new ModelNode(Provider.SIMPLE.toString()))
            .setValidator(EnumValidator.create(Provider.class))
            .build();

    public static final SimpleAttributeDefinition USE_IDENTITY_ROLES = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.USE_IDENTITY_ROLES, ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    static final ListAttributeDefinition STANDARD_ROLE_NAMES = new StringListAttributeDefinition.Builder(ModelDescriptionConstants.STANDARD_ROLE_NAMES)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    static final ListAttributeDefinition ALL_ROLE_NAMES = new StringListAttributeDefinition.Builder(ModelDescriptionConstants.ALL_ROLE_NAMES)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    public static final List<AttributeDefinition> CONFIG_ATTRIBUTES = Arrays.<AttributeDefinition>asList(PROVIDER, PERMISSION_COMBINATION_POLICY);

    public static AccessAuthorizationResourceDefinition forDomain(DelegatingConfigurableAuthorizer configurableAuthorizer) {
        return new AccessAuthorizationResourceDefinition(configurableAuthorizer, true);
    }

    public static AccessAuthorizationResourceDefinition forDomainServer(DelegatingConfigurableAuthorizer configurableAuthorizer) {
        return new AccessAuthorizationResourceDefinition(configurableAuthorizer, true);
    }

    public static AccessAuthorizationResourceDefinition forStandaloneServer(DelegatingConfigurableAuthorizer configurableAuthorizer) {
        return new AccessAuthorizationResourceDefinition(configurableAuthorizer, false);
    }

    private final DelegatingConfigurableAuthorizer configurableAuthorizer;

    private final boolean isDomain;

    private AccessAuthorizationResourceDefinition(DelegatingConfigurableAuthorizer configurableAuthorizer, boolean domain) {
        super(new Parameters(PATH_ELEMENT, DomainManagementResolver.getResolver("core.access-control"))
                            .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.ACCESS_CONTROL));
        this.configurableAuthorizer = configurableAuthorizer;
        isDomain = domain;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        WritableAuthorizerConfiguration authorizerConfiguration = configurableAuthorizer.getWritableAuthorizerConfiguration();
        resourceRegistration.registerReadWriteAttribute(PROVIDER, null, new AccessAuthorizationProviderWriteAttributeHandler(configurableAuthorizer));
        resourceRegistration.registerReadWriteAttribute(USE_IDENTITY_ROLES, null, new AccessAuthorizationUseIdentityRolesWriteAttributeHandler(configurableAuthorizer.getWritableAuthorizerConfiguration()));
        resourceRegistration.registerReadWriteAttribute(PERMISSION_COMBINATION_POLICY, null,
                new AccessAuthorizationCombinationPolicyWriteAttributeHandler(authorizerConfiguration));

        resourceRegistration.registerReadOnlyAttribute(STANDARD_ROLE_NAMES,
                AccessAuthorizationRolesHandler.getStandardRolesHandler(authorizerConfiguration));
        resourceRegistration.registerReadOnlyAttribute(ALL_ROLE_NAMES,
                AccessAuthorizationRolesHandler.getAllRolesHandler(authorizerConfiguration));
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        // Role Mapping
        resourceRegistration.registerSubModel(RoleMappingResourceDefinition.create(configurableAuthorizer, isDomain));

        // Scoped roles
        if (isDomain) {
            WritableAuthorizerConfiguration authorizerConfiguration = configurableAuthorizer.getWritableAuthorizerConfiguration();
            resourceRegistration.registerSubModel(new ServerGroupScopedRoleResourceDefinition(authorizerConfiguration));
            resourceRegistration.registerSubModel(new HostScopedRolesResourceDefinition(authorizerConfiguration));
        }

        // Constraints
        //  -- Application Type
        resourceRegistration.registerSubModel(ApplicationClassificationParentResourceDefinition.INSTANCE);
        //  -- Sensitivity Classification
        resourceRegistration.registerSubModel(SensitivityClassificationParentResourceDefinition.INSTANCE);
        //  -- Vault Expression
        resourceRegistration.registerSubModel(SensitivityResourceDefinition.createVaultExpressionConfiguration());
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        if (isDomain) {
            // Op to apply config from the master to a slave
            resourceRegistration.registerOperationHandler(AccessAuthorizationDomainSlaveConfigHandler.DEFINITION,
                    new AccessAuthorizationDomainSlaveConfigHandler(configurableAuthorizer));
        }
    }

    public static Resource createResource(AccessConstraintUtilizationRegistry registry) {
        Resource accessControlRoot =  Resource.Factory.create();
        accessControlRoot.registerChild(AccessConstraintResources.APPLICATION_PATH_ELEMENT, AccessConstraintResources.getApplicationConfigResource(registry));
        accessControlRoot.registerChild(AccessConstraintResources.SENSITIVITY_PATH_ELEMENT, AccessConstraintResources.getSensitivityResource(registry));
        accessControlRoot.registerChild(AccessConstraintResources.VAULT_PATH_ELEMENT, AccessConstraintResources.VAULT_RESOURCE);
        return accessControlRoot;
    }

}
