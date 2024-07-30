/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;
import org.wildfly.security.auth.realm.SimpleMapBackedSecurityRealm;
import org.wildfly.security.auth.realm.SimpleRealmEntry;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.authz.MapAttributes;
/**
 * Container class for {@link SecurityRealm} resource definitions.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class RealmDefinitions {

    static final AttributeDefinition IDENTITY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.IDENTITY, ModelType.STRING, false)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final AttributeDefinition ATTRIBUTE_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ATTRIBUTE_NAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final StringListAttributeDefinition ATTRIBUTE_VALUES = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.ATTRIBUTE_VALUES)
            .setMinSize(0)
            .setRequired(false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final AttributeDefinition[] IDENTITY_REALM_ATTRIBUTES = { IDENTITY, ATTRIBUTE_NAME, ATTRIBUTE_VALUES };

    static ResourceDefinition getIdentityRealmDefinition() {
        AbstractAddStepHandler add = new TrivialAddHandler<SecurityRealm>(SecurityRealm.class, SECURITY_REALM_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<SecurityRealm> getValueSupplier(ServiceBuilder<SecurityRealm> serviceBuilder,
                    OperationContext context, ModelNode model) throws OperationFailedException {

                final String identity = IDENTITY.resolveModelAttribute(context, model).asString();
                final String attributeName = ATTRIBUTE_NAME.resolveModelAttribute(context, model).asStringOrNull();
                final List<String> attributeValues = ATTRIBUTE_VALUES.unwrap(context, model);

                return () -> {
                    final Map<String, ? extends Collection<String>> attributesMap;
                    if (attributeName != null) {
                        attributesMap = Collections.singletonMap(attributeName, Collections.unmodifiableList(attributeValues));
                    } else {
                        attributesMap = Collections.emptyMap();
                    }
                    final Map<String, SimpleRealmEntry> realmMap = Collections.singletonMap(identity, new SimpleRealmEntry(Collections.emptyList(), new MapAttributes(attributesMap)));
                    SimpleMapBackedSecurityRealm securityRealm = new SimpleMapBackedSecurityRealm();
                    securityRealm.setPasswordMap(realmMap);

                    return securityRealm;
                };
            }
        };

        return new TrivialResourceDefinition(ElytronDescriptionConstants.IDENTITY_REALM, add, IDENTITY_REALM_ATTRIBUTES, SECURITY_REALM_RUNTIME_CAPABILITY);
    }
}
