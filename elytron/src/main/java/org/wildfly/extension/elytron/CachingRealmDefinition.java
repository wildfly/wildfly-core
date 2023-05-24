/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
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
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.elytron._private.ElytronSubsystemMessages;
import org.wildfly.security.auth.realm.CacheableSecurityRealm;
import org.wildfly.security.auth.realm.CachingModifiableSecurityRealm;
import org.wildfly.security.auth.realm.CachingSecurityRealm;
import org.wildfly.security.auth.server.ModifiableSecurityRealm;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.cache.LRURealmIdentityCache;
import org.wildfly.security.cache.RealmIdentityCache;

/**
 * A {@link ResourceDefinition} for a {@link SecurityRealm} which enables caching to another realm.
 *
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
class CachingRealmDefinition extends SimpleResourceDefinition {

    static final ServiceUtil<SecurityRealm> REALM_SERVICE_UTIL = ServiceUtil.newInstance(SECURITY_REALM_RUNTIME_CAPABILITY, ElytronDescriptionConstants.CACHING_REALM, SecurityRealm.class);

    static final SimpleAttributeDefinition REALM_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REALM, ModelType.STRING, false)
            .setMinSize(1)
            .setCapabilityReference(SECURITY_REALM_CAPABILITY)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition MAXIMUM_ENTRIES = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MAXIMUM_ENTRIES, ModelType.INT, true)
            .setDefaultValue(new ModelNode(16))
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition MAXIMUM_AGE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MAXIMUM_AGE, ModelType.LONG, true)
            .setDefaultValue(new ModelNode(-1L))
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {REALM_NAME, MAXIMUM_ENTRIES, MAXIMUM_AGE};

    private static final AbstractAddStepHandler ADD = new RealmAddHandler();
    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, SECURITY_REALM_RUNTIME_CAPABILITY);

    CachingRealmDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.CACHING_REALM), ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.CACHING_REALM))
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

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        ClearCacheHandler.register(resourceRegistration, getResourceDescriptionResolver());
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
            String cacheableRealm = REALM_NAME.resolveModelAttribute(context, model).asString();
            int maxEntries = MAXIMUM_ENTRIES.resolveModelAttribute(context, model).asInt();
            long maxAge = MAXIMUM_AGE.resolveModelAttribute(context, model).asInt();
            InjectedValue<SecurityRealm> cacheableRealmValue = new InjectedValue<>();
            ServiceBuilder<SecurityRealm> serviceBuilder = serviceTarget.addService(realmName, createService(cacheableRealm, maxEntries, maxAge, cacheableRealmValue));

            addRealmDependency(context, serviceBuilder, cacheableRealm, cacheableRealmValue);
            commonDependencies(serviceBuilder).setInitialMode(Mode.ACTIVE).install();
        }

        private TrivialService<SecurityRealm> createService(String realmName, int maxEntries, long maxAge, InjectedValue<SecurityRealm> injector) {
            return new TrivialService<>((TrivialService.ValueSupplier<SecurityRealm>) () -> {
                SecurityRealm securityRealm = injector.getValue();

                if (securityRealm instanceof CacheableSecurityRealm) {
                    RealmIdentityCache cache = createRealmIdentityCache(maxEntries, maxAge);
                    CacheableSecurityRealm cacheableRealm = CacheableSecurityRealm.class.cast(securityRealm);

                    if (securityRealm instanceof ModifiableSecurityRealm) {
                        return new CachingModifiableSecurityRealm(cacheableRealm, cache);
                    }

                    return new CachingSecurityRealm(cacheableRealm, cache);
                }

                throw ElytronSubsystemMessages.ROOT_LOGGER.realmDoesNotSupportCache(realmName);
            });
        }

        private LRURealmIdentityCache createRealmIdentityCache(int maxEntries, long maxAge) {
            return new LRURealmIdentityCache(maxEntries, maxAge);
        }

        private void addRealmDependency(OperationContext context, ServiceBuilder<SecurityRealm> serviceBuilder, String realmName, Injector<SecurityRealm> securityRealmInjector) {
            String runtimeCapability = RuntimeCapability.buildDynamicCapabilityName(SECURITY_REALM_CAPABILITY, realmName);
            ServiceName realmServiceName = context.getCapabilityServiceName(runtimeCapability, SecurityRealm.class);
            REALM_SERVICE_UTIL.addInjection(serviceBuilder, securityRealmInjector, realmServiceName);
        }

    }

    private static class ClearCacheHandler extends ElytronRuntimeOnlyHandler {

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.CLEAR_CACHE, descriptionResolver)
                        .setRuntimeOnly()
                        .build()
                    , new CachingRealmDefinition.ClearCacheHandler());
        }

        private ClearCacheHandler() {
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            ServiceRegistry serviceRegistry = context.getServiceRegistry(true);
            PathAddress currentAddress = context.getCurrentAddress();
            RuntimeCapability<Void> runtimeCapability = SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(currentAddress.getLastElement().getValue());
            ServiceName realmName = runtimeCapability.getCapabilityServiceName();
            ServiceController<SecurityRealm> serviceController = getRequiredService(serviceRegistry, realmName, SecurityRealm.class);
            CachingSecurityRealm securityRealm = CachingSecurityRealm.class.cast(serviceController.getValue());
            securityRealm.removeAllFromCache();
        }
    }
}
