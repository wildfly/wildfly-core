/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;
import static org.wildfly.extension.elytron.RealmDefinitions.createBruteForceRealmTransformer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RunningMode;
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
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartException;
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

    // Callers are expected to just use a single method get / put / remove not multiple calls so we don't
    // need complex locking beyond the Map itself..
    private static final Map<String, CachingSecurityRealm> REALMS = new ConcurrentHashMap<>();

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
        for (AttributeDefinition current : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, ElytronReloadRequiredWriteAttributeHandler.INSTANCE);
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        ClearCacheHandler.register(resourceRegistration, getResourceDescriptionResolver());
    }

    private static class RealmAddHandler extends BaseAddHandler {

        private RealmAddHandler() {
            super(SECURITY_REALM_RUNTIME_CAPABILITY);
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

            ServiceBuilder<?> serviceBuilder = serviceTarget.addService();
            Consumer<SecurityRealm> valueConsumer = serviceBuilder.provides(realmName);

            final Function<SecurityRealm, SecurityRealm> realmTransformer =
                createBruteForceRealmTransformer(context.getCurrentAddressValue(), SecurityRealm.class, serviceBuilder);

            serviceBuilder.setInstance(createService(context.getCurrentAddressValue(), cacheableRealm, maxEntries, maxAge, cacheableRealmValue, realmTransformer, valueConsumer));

            addRealmDependency(context, serviceBuilder, cacheableRealm, cacheableRealmValue);
            commonDependencies(serviceBuilder).setInitialMode(context.getRunningMode() == RunningMode.ADMIN_ONLY ? ServiceController.Mode.LAZY : ServiceController.Mode.ACTIVE).install();
        }

        private TrivialService<SecurityRealm> createService(String ourRealmName, String wrappedRealmName, int maxEntries, long maxAge,
            InjectedValue<SecurityRealm> injector, Function<SecurityRealm, SecurityRealm> realmTransformer, Consumer<SecurityRealm> valueConsumer) {
            return new TrivialService<>(new TrivialService.ValueSupplier<SecurityRealm>() {

                @Override
                public SecurityRealm get() throws StartException {
                    SecurityRealm securityRealm = injector.getValue();

                    if (securityRealm instanceof CacheableSecurityRealm) {
                        RealmIdentityCache cache = createRealmIdentityCache(maxEntries, maxAge);
                        CacheableSecurityRealm cacheableRealm = CacheableSecurityRealm.class.cast(securityRealm);

                        CachingSecurityRealm cachingRealm = securityRealm instanceof ModifiableSecurityRealm ?
                            new CachingModifiableSecurityRealm(cacheableRealm, cache) : new CachingSecurityRealm(cacheableRealm, cache);

                        REALMS.put(ourRealmName, cachingRealm);

                        return realmTransformer.apply(cachingRealm);
                    }

                    throw ElytronSubsystemMessages.ROOT_LOGGER.realmDoesNotSupportCache(wrappedRealmName);
                }

                @Override
                public void dispose() {
                    REALMS.remove(ourRealmName);
                }

            }, valueConsumer);
        }

        private LRURealmIdentityCache createRealmIdentityCache(int maxEntries, long maxAge) {
            return new LRURealmIdentityCache(maxEntries, maxAge);
        }

        private void addRealmDependency(OperationContext context, ServiceBuilder<?> serviceBuilder, String realmName, Injector<SecurityRealm> securityRealmInjector) {
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
            CachingSecurityRealm securityRealm = REALMS.get(context.getCurrentAddressValue());
            if (securityRealm == null) {
                throw ElytronSubsystemMessages.ROOT_LOGGER.cachedRealmServiceNotAvailable();
            }

            securityRealm.removeAllFromCache();
        }
    }
}
