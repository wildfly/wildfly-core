/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

import static org.wildfly.extension.elytron.ElytronCommonCapabilities.SECURITY_REALM_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronCommonCapabilities.SECURITY_REALM_RUNTIME_CAPABILITY;
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

import org.wildfly.security.auth.realm.FailoverSecurityRealm;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.auth.server.event.SecurityRealmUnavailableEvent;

import java.util.function.Consumer;

/**
 * A {@link ResourceDefinition} for a {@link SecurityRealm} which wraps one realm and fails over to another in case the first is unavailable.
 *
 * @author <a href="mailto:mmazanek@jboss.com">Martin Mazanek</a>
 */
class FailoverRealmDefinition extends SimpleResourceDefinition {

    static final ServiceUtil<SecurityRealm> REALM_SERVICE_UTIL = ServiceUtil.newInstance(SECURITY_REALM_RUNTIME_CAPABILITY, ElytronCommonConstants.AGGREGATE_REALM, SecurityRealm.class);

    static final SimpleAttributeDefinition DELEGATE_REALM = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.DELEGATE_REALM, ModelType.STRING, false)
            .setMinSize(1)
            .setCapabilityReference(SECURITY_REALM_CAPABILITY, SECURITY_REALM_CAPABILITY)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition FAILOVER_REALM = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.FAILOVER_REALM, ModelType.STRING, false)
            .setMinSize(1)
            .setCapabilityReference(SECURITY_REALM_CAPABILITY, SECURITY_REALM_CAPABILITY)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition EMIT_EVENTS = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.EMIT_EVENTS, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setDefaultValue(ModelNode.TRUE)
            .build();

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { DELEGATE_REALM, FAILOVER_REALM, EMIT_EVENTS};

    private static final AbstractAddStepHandler ADD = new RealmAddHandler();
    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, SECURITY_REALM_RUNTIME_CAPABILITY);

    FailoverRealmDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronCommonConstants.FAILOVER_REALM), ElytronExtension.getResourceDescriptionResolver(ElytronCommonConstants.FAILOVER_REALM))
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

    private static class RealmAddHandler extends ElytronCommonBaseAddHandler {

        private RealmAddHandler() {
            super(SECURITY_REALM_RUNTIME_CAPABILITY, ATTRIBUTES);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            ServiceTarget serviceTarget = context.getServiceTarget();
            RuntimeCapability<Void> runtimeCapability = SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName realmName = runtimeCapability.getCapabilityServiceName(SecurityRealm.class);

            String delegateRealm = DELEGATE_REALM.resolveModelAttribute(context, model).asString();
            final InjectedValue<SecurityRealm> delegateRealmValue = new InjectedValue<SecurityRealm>();

            String failoverRealm = FAILOVER_REALM.resolveModelAttribute(context, model).asString();
            final InjectedValue<SecurityRealm> failoverRealmValue = new InjectedValue<SecurityRealm>();

            boolean emitEvents = EMIT_EVENTS.resolveModelAttribute(context, model).asBoolean();

            TrivialService<SecurityRealm> failoverRealmService = new TrivialService<SecurityRealm>(() ->
            {
                SecurityRealm delegate = delegateRealmValue.getValue();
                Consumer<RealmUnavailableException> failoverConsumer = emitEvents ? (e) -> {
                    SecurityDomain domain = SecurityDomain.getCurrent();
                    if (domain != null) {
                        domain.handleSecurityEvent(new SecurityRealmUnavailableEvent(domain.getCurrentSecurityIdentity(), delegateRealm));
                    }
                } : (e) -> {};
                return new FailoverSecurityRealm(delegate, failoverRealmValue.getValue(), failoverConsumer);
            });

            ServiceBuilder<SecurityRealm> serviceBuilder = serviceTarget.addService(realmName, failoverRealmService);

            addRealmDependency(context, serviceBuilder, delegateRealm, delegateRealmValue);
            addRealmDependency(context, serviceBuilder, failoverRealm, failoverRealmValue);

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
