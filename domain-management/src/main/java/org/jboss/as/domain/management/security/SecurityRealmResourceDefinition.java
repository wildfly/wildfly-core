/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.management.security;

import static org.jboss.as.domain.management.ModelDescriptionConstants.SECURITY_REALM;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.management.Capabilities;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for a management security realm resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class SecurityRealmResourceDefinition extends SimpleResourceDefinition {

    public static final RuntimeCapability<Void> MANAGEMENT_SECURITY_REALM_CAPABILITY = RuntimeCapability.Builder.of(Capabilities.MANAGEMENT_SECURITY_REALM_CAPABILITY, true, SecurityRealm.class).build();
    public static final SecurityRealmResourceDefinition INSTANCE = new SecurityRealmResourceDefinition();

    static final String DEPRECATED_PARENT_CATEGORY = "core.management.security-realm";

    public static final SimpleAttributeDefinition MAP_GROUPS_TO_ROLES = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.MAP_GROUPS_TO_ROLES, ModelType.BOOLEAN, true)
            .setDefaultValue(new ModelNode(true))
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    private SecurityRealmResourceDefinition() {
        super(new Parameters(PathElement.pathElement(SECURITY_REALM), ControllerResolver.getResolver(DEPRECATED_PARENT_CATEGORY))
                .setAddHandler(SecurityRealmAddHandler.INSTANCE)
                .setRemoveHandler(SecurityRealmRemoveHandler.INSTANCE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_NONE)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.SECURITY_REALM)
                .addCapabilities(MANAGEMENT_SECURITY_REALM_CAPABILITY)
                .setDeprecatedSince(ModelVersion.create(1, 7)));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(MAP_GROUPS_TO_ROLES, null, new ReloadRequiredWriteAttributeHandler(MAP_GROUPS_TO_ROLES){
            @Override
            protected boolean requiresRuntime(OperationContext context) {
                // secure realm runs in both running modes so running doesn't affect the result here
                return !context.isBooting();
            }
        });
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(new PlugInResourceDefinition());
        resourceRegistration.registerSubModel(new KerberosServerIdentityResourceDefinition());
        resourceRegistration.registerSubModel(new SecretServerIdentityResourceDefinition());
        resourceRegistration.registerSubModel(new SSLServerIdentityResourceDefinition());
        resourceRegistration.registerSubModel(new TruststoreAuthenticationResourceDefinition());
        resourceRegistration.registerSubModel(new LocalAuthenticationResourceDefinition());
        resourceRegistration.registerSubModel(new JaasAuthenticationResourceDefinition());
        resourceRegistration.registerSubModel(new KerberosAuthenticationResourceDefinition());
        resourceRegistration.registerSubModel(new LdapAuthenticationResourceDefinition());
        resourceRegistration.registerSubModel(new PropertiesAuthenticationResourceDefinition());
        resourceRegistration.registerSubModel(new XmlAuthenticationResourceDefinition());
        resourceRegistration.registerSubModel(new PlugInAuthenticationResourceDefinition());
        resourceRegistration.registerSubModel(new PropertiesAuthorizationResourceDefinition());
        resourceRegistration.registerSubModel(new PlugInAuthorizationResourceDefinition());
        resourceRegistration.registerSubModel(new LdapAuthorizationResourceDefinition());
    }
}
