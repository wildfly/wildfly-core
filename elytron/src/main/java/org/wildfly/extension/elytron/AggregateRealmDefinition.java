/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PRINCIPAL_TRANSFORMER_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;

import java.util.ArrayList;
import java.util.List;

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

import org.wildfly.extension.elytron.capabilities.PrincipalTransformer;
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
        .setCapabilityReference(SECURITY_REALM_CAPABILITY, SECURITY_REALM_CAPABILITY)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition AUTHORIZATION_REALM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.AUTHORIZATION_REALM, ModelType.STRING, false)
        .setMinSize(1)
        .setAlternatives(ElytronDescriptionConstants.AUTHORIZATION_REALMS)
        .setCapabilityReference(SECURITY_REALM_CAPABILITY, SECURITY_REALM_CAPABILITY)
        .setRestartAllServices()
        .build();

    static final StringListAttributeDefinition AUTHORIZATION_REALMS = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.AUTHORIZATION_REALMS)
            .setAlternatives(ElytronDescriptionConstants.AUTHORIZATION_REALM)
            .setMinSize(1)
            .setCapabilityReference(SECURITY_REALM_CAPABILITY, SECURITY_REALM_CAPABILITY)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition PRINCIPAL_TRANSFORMER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PRINCIPAL_TRANSFORMER, ModelType.STRING, true)
            .setMinSize(1)
            .setCapabilityReference(PRINCIPAL_TRANSFORMER_CAPABILITY, SECURITY_REALM_CAPABILITY)
            .setRestartAllServices()
            .setAllowExpression(true)
            .build();

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { AUTHENTICATION_REALM, AUTHORIZATION_REALM };

    static final AttributeDefinition[] ATTRIBUTES_8_0 = new AttributeDefinition[] { AUTHENTICATION_REALM, AUTHORIZATION_REALM, AUTHORIZATION_REALMS, PRINCIPAL_TRANSFORMER };

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
        AbstractWriteAttributeHandler write = new ElytronReloadRequiredWriteAttributeHandler(ATTRIBUTES_8_0);
        for (AttributeDefinition current : ATTRIBUTES_8_0) {
            resourceRegistration.registerReadWriteAttribute(current, null, write);
        }
    }

    private static class RealmAddHandler extends BaseAddHandler {

        private RealmAddHandler() {
            super(SECURITY_REALM_RUNTIME_CAPABILITY, ATTRIBUTES_8_0);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            ServiceTarget serviceTarget = context.getServiceTarget();
            RuntimeCapability<Void> runtimeCapability = SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName realmName = runtimeCapability.getCapabilityServiceName(SecurityRealm.class);

            String authenticationRealm = AUTHENTICATION_REALM.resolveModelAttribute(context, model).asString();
            final InjectedValue<SecurityRealm> authenticationRealmValue = new InjectedValue<SecurityRealm>();

            final List<InjectedValue<SecurityRealm>> authorizationRealmValues = new ArrayList<>();
            ModelNode authorizationRealmNode = AUTHORIZATION_REALM.resolveModelAttribute(context, model);

            String principalTransformer = PRINCIPAL_TRANSFORMER.resolveModelAttribute(context, model).asStringOrNull();

            InjectedValue<PrincipalTransformer> principalTransformerValue = null;
            String principalTransformerRuntimeCapability;
            ServiceName principalTransformerServiceName = null;

            if (principalTransformer != null) {
                principalTransformerValue = new InjectedValue<PrincipalTransformer>();
                principalTransformerRuntimeCapability = RuntimeCapability.buildDynamicCapabilityName(PRINCIPAL_TRANSFORMER_CAPABILITY, principalTransformer);
                principalTransformerServiceName = context.getCapabilityServiceName(principalTransformerRuntimeCapability, PrincipalTransformer.class);
            }

            final InjectedValue<PrincipalTransformer> finalPrincipalTransformerValue = principalTransformerValue;
            TrivialService<SecurityRealm> aggregateRealmService = new TrivialService<SecurityRealm>(() -> {
                SecurityRealm[] authorizationRealms = new SecurityRealm[authorizationRealmValues.size()];
                for (int i = 0; i < authorizationRealms.length; i++) {
                    authorizationRealms[i] = authorizationRealmValues.get(i).getValue();
                }

                if (finalPrincipalTransformerValue != null) {
                    return new AggregateSecurityRealm(authenticationRealmValue.getValue(), finalPrincipalTransformerValue.getValue(), authorizationRealms);
                } else {
                    return new AggregateSecurityRealm(authenticationRealmValue.getValue(), authorizationRealms);
                }
            });

            ServiceBuilder<SecurityRealm> serviceBuilder = serviceTarget.addService(realmName, aggregateRealmService);

            addRealmDependency(context, serviceBuilder, authenticationRealm, authenticationRealmValue);
            if (principalTransformer != null) {
                serviceBuilder.addDependency(principalTransformerServiceName, PrincipalTransformer.class, principalTransformerValue);
            }

            if (authorizationRealmNode.isDefined()) {
                String authorizationRealm = authorizationRealmNode.asString();
                InjectedValue<SecurityRealm> authorizationRealmValue = new InjectedValue<SecurityRealm>();
                addRealmDependency(context, serviceBuilder, authorizationRealm, authorizationRealmValue);
                authorizationRealmValues.add(authorizationRealmValue);
            } else {
                List<String> authorizationRealms = AUTHORIZATION_REALMS.unwrap(context, model);
                for (String authorizationRealm : authorizationRealms) {
                    InjectedValue<SecurityRealm> authorizationRealmValue = new InjectedValue<SecurityRealm>();
                    addRealmDependency(context, serviceBuilder, authorizationRealm, authorizationRealmValue);
                    authorizationRealmValues.add(authorizationRealmValue);
                }
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
