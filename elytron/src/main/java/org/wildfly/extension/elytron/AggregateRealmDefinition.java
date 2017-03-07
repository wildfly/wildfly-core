/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
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

import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.security.auth.realm.AggregateSecurityRealm;
import org.wildfly.security.auth.server.SecurityRealm;

/**
 * A {@link ResourceDefinition} for a {@link SecruityRealm} which is an aggregation of two other realm instances.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class AggregateRealmDefinition extends SimpleResourceDefinition {

    static final ServiceUtil<SecurityRealm> REALM_SERVICE_UTIL = ServiceUtil.newInstance(SECURITY_REALM_RUNTIME_CAPABILITY, ElytronDescriptionConstants.AGGREGATE_REALM, SecurityRealm.class);

    static final SimpleAttributeDefinition AUTHENTICATION_REALM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.AUTHENTICATION_REALM, ModelType.STRING, false)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setCapabilityReference(SECURITY_REALM_CAPABILITY, SECURITY_REALM_CAPABILITY, true)
        .build();

    static final SimpleAttributeDefinition AUTHORIZATION_REALM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.AUTHORIZATION_REALM, ModelType.STRING, false)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setCapabilityReference(SECURITY_REALM_CAPABILITY, SECURITY_REALM_CAPABILITY, true)
        .build();

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { AUTHENTICATION_REALM, AUTHORIZATION_REALM };

    private static final AbstractAddStepHandler ADD = new RealmAddHandler();
    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, SECURITY_REALM_RUNTIME_CAPABILITY);

    AggregateRealmDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.AGGREGATE_REALM), ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.AGGREGATE_REALM))
            .setAddHandler(ADD)
            .setRemoveHandler(REMOVE)
            .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilities(SECURITY_REALM_RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        WriteAttributeHandler write = new WriteAttributeHandler(ElytronDescriptionConstants.AGGREGATE_REALM);
        for (AttributeDefinition current : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, write);
        }
    }

    private static class RealmAddHandler extends BaseAddHandler {

        private RealmAddHandler() {
            super(SECURITY_REALM_RUNTIME_CAPABILITY, ATTRIBUTES);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            ServiceTarget serviceTarget = context.getServiceTarget();
            RuntimeCapability<Void> runtimeCapability = SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName realmName = runtimeCapability.getCapabilityServiceName(SecurityRealm.class);

            String authenticationRealm = AUTHENTICATION_REALM.resolveModelAttribute(context, model).asString();
            String authorizationRealm = AUTHORIZATION_REALM.resolveModelAttribute(context, model).asString();

            final InjectedValue<SecurityRealm> authenticationRealmValue = new InjectedValue<SecurityRealm>();
            final InjectedValue<SecurityRealm> authorizationRealmValue = new InjectedValue<SecurityRealm>();

            TrivialService<SecurityRealm> aggregateRealmService = new TrivialService<SecurityRealm>( () -> new AggregateSecurityRealm(authenticationRealmValue.getValue(), authorizationRealmValue.getValue()));

            ServiceBuilder<SecurityRealm> serviceBuilder = serviceTarget.addService(realmName, aggregateRealmService);

            addRealmDependency(context, serviceBuilder, authenticationRealm, authenticationRealmValue);
            addRealmDependency(context, serviceBuilder, authorizationRealm, authorizationRealmValue);

            commonDependencies(serviceBuilder)
                .setInitialMode(Mode.ACTIVE)
                .install();
        }

        private void addRealmDependency(OperationContext context, ServiceBuilder<SecurityRealm> serviceBuilder, String realmName, Injector<SecurityRealm> securityRealmInjector) {
            String runtimeCapability = RuntimeCapability.buildDynamicCapabilityName(SECURITY_REALM_CAPABILITY, realmName);
            ServiceName realmServiceName = context.getCapabilityServiceName(runtimeCapability, SecurityRealm.class);

            REALM_SERVICE_UTIL.addInjection(serviceBuilder, securityRealmInjector, realmServiceName);
        }

    }

    private static class WriteAttributeHandler extends ElytronRestartParentWriteAttributeHandler {

        WriteAttributeHandler(final String key) {
            super(key, ATTRIBUTES);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress pathAddress) {
            return SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(pathAddress.getLastElement().getValue()).getCapabilityServiceName(SecurityRealm.class);
        }
    }
}
