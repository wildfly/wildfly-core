/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.ElytronCommonCapabilities.SECURITY_REALM_RUNTIME_CAPABILITY;

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

    static final AttributeDefinition IDENTITY = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.IDENTITY, ModelType.STRING, false)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final AttributeDefinition ATTRIBUTE_NAME = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.ATTRIBUTE_NAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final StringListAttributeDefinition ATTRIBUTE_VALUES = new StringListAttributeDefinition.Builder(ElytronCommonConstants.ATTRIBUTE_VALUES)
            .setMinSize(0)
            .setRequired(false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static AttributeDefinition[] IDENTITY_REALM_ATTRIBUTES = { IDENTITY, ATTRIBUTE_NAME, ATTRIBUTE_VALUES };

    static ResourceDefinition getIdentityRealmDefinition() {
        AbstractAddStepHandler add = new ElytronCommonTrivialAddHandler<SecurityRealm>(SecurityRealm.class, IDENTITY_REALM_ATTRIBUTES, SECURITY_REALM_RUNTIME_CAPABILITY) {

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

        return new ElytronCommonTrivialResourceDefinition(ElytronCommonConstants.IDENTITY_REALM, add, IDENTITY_REALM_ATTRIBUTES, SECURITY_REALM_RUNTIME_CAPABILITY);
    }
}
