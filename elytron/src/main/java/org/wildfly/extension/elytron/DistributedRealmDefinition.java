/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
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

import org.wildfly.security.auth.realm.DistributedSecurityRealm;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.auth.server.event.SecurityRealmUnavailableEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A {@link ResourceDefinition} for a {@link SecurityRealm} for authentication and authorization of identities distributed between multiple realms.
 *
 * @author <a href="mailto:mmazanek@jboss.com">Martin Mazanek</a>
 */
class DistributedRealmDefinition extends SimpleResourceDefinition {

    static final ServiceUtil<SecurityRealm> REALM_SERVICE_UTIL = ServiceUtil.newInstance(SECURITY_REALM_RUNTIME_CAPABILITY, ElytronDescriptionConstants.AGGREGATE_REALM, SecurityRealm.class);

    static final StringListAttributeDefinition REALMS = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.REALMS)
            .setMinSize(1)
            .setCapabilityReference(SECURITY_REALM_CAPABILITY)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition IGNORE_UNAVAILABLE_REALMS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.IGNORE_UNAVAILABLE_REALMS, ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.FALSE)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition EMIT_EVENTS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.EMIT_EVENTS, ModelType.BOOLEAN, true)
            .setRequires(ElytronDescriptionConstants.IGNORE_UNAVAILABLE_REALMS)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setDefaultValue(ModelNode.TRUE)
            .build();

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {REALMS, IGNORE_UNAVAILABLE_REALMS, EMIT_EVENTS};

    private static final AbstractAddStepHandler ADD = new RealmAddHandler();
    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, SECURITY_REALM_RUNTIME_CAPABILITY);

    DistributedRealmDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.DISTRIBUTED_REALM), ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.DISTRIBUTED_REALM))
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(SECURITY_REALM_RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        AbstractWriteAttributeHandler write = new ElytronReloadRequiredWriteAttributeHandler(ATTRIBUTES);
        for (AttributeDefinition current : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, write);
        }
    }

    private static class RealmAddHandler extends BaseAddHandler {

        private RealmAddHandler() {
            super(SECURITY_REALM_RUNTIME_CAPABILITY, ATTRIBUTES);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            ServiceTarget serviceTarget = context.getServiceTarget();
            RuntimeCapability<Void> runtimeCapability = SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName realmName = runtimeCapability.getCapabilityServiceName(SecurityRealm.class);

            final List<InjectedValue<SecurityRealm>> distributedRealmValues = new ArrayList<>();

            boolean ignoreUnavailableRealms = IGNORE_UNAVAILABLE_REALMS.resolveModelAttribute(context, model).asBoolean();
            boolean emitEvents = EMIT_EVENTS.resolveModelAttribute(context, model).asBoolean();

            List<String> distributedRealms = REALMS.unwrap(context, model);

            TrivialService<SecurityRealm> distributedRealmService = new TrivialService<SecurityRealm>(() ->
            {
                SecurityRealm[] realms = new SecurityRealm[distributedRealmValues.size()];

                Consumer<Integer> unavailableRealmConsumer = emitEvents ? (realmIndex) -> {
                    SecurityDomain domain = SecurityDomain.getCurrent();
                    String realm = distributedRealms.get(realmIndex);
                    if (domain != null) {
                        domain.handleSecurityEvent(new SecurityRealmUnavailableEvent(domain.getCurrentSecurityIdentity(), realm));
                    }
                } : (realmIndex) -> {};

                for (int i = 0; i < distributedRealmValues.size(); i++) {
                    realms[i] = distributedRealmValues.get(i).getValue();
                }

                return new DistributedSecurityRealm(ignoreUnavailableRealms, unavailableRealmConsumer, realms);
            });

            ServiceBuilder<SecurityRealm> serviceBuilder = serviceTarget.addService(realmName, distributedRealmService);

            for (String distributedRealm : distributedRealms) {
                InjectedValue<SecurityRealm> authorizationRealmValue = new InjectedValue<SecurityRealm>();
                addRealmDependency(context, serviceBuilder, distributedRealm, authorizationRealmValue);
                distributedRealmValues.add(authorizationRealmValue);
            }

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

}
