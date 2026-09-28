/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_API_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;
import static org.wildfly.extension.elytron.ElytronExtension.isServerOrHostController;
import static org.wildfly.extension.elytron.ServiceStateDefinition.STATE;
import static org.wildfly.extension.elytron.ServiceStateDefinition.populateResponse;

import java.security.KeyStore;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityServiceBuilder;
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
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.security.keystore.AliasFilter;
import org.wildfly.security.keystore.FilteringKeyStore;

/**
 * A {@link ResourceDefinition} for a single {@link FilteringKeyStore}.
 *
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
class FilteringKeyStoreDefinition extends SimpleResourceDefinition {

    static final ServiceUtil<KeyStore> FILTERING_KEY_STORE_UTIL = ServiceUtil.newInstance(KEY_STORE_RUNTIME_CAPABILITY, ElytronDescriptionConstants.FILTERING_KEY_STORE, KeyStore.class);

    static final SimpleAttributeDefinition KEY_STORE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEY_STORE, ModelType.STRING, false)
            .setAllowExpression(false)
            .setRestartAllServices()
            .setCapabilityReference(KEY_STORE_CAPABILITY, KEY_STORE_CAPABILITY)
            .build();

    static final SimpleAttributeDefinition ALIAS_FILTER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALIAS_FILTER, ModelType.STRING, false)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    private static final StandardResourceDescriptionResolver RESOURCE_RESOLVER = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.FILTERING_KEY_STORE);

    private static final AttributeDefinition[] CONFIG_ATTRIBUTES = new AttributeDefinition[] { KEY_STORE, ALIAS_FILTER };

    private static final KeyStoreAddHandler ADD = new KeyStoreAddHandler();
    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, KEY_STORE_RUNTIME_CAPABILITY) {
        @Override
        protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource)
                throws OperationFailedException {
            super.recordCapabilitiesAndRequirements(context, operation, resource);
            if (requiresRuntime(context)) {
                context.deregisterCapability(
                        RuntimeCapability.buildDynamicCapabilityName(KEY_STORE_API_CAPABILITY, context.getCurrentAddressValue()));
            }
        }
    };

    FilteringKeyStoreDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.FILTERING_KEY_STORE), RESOURCE_RESOLVER)
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(KEY_STORE_RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition current : CONFIG_ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, ElytronReloadRequiredWriteAttributeHandler.INSTANCE);
        }

        if (isServerOrHostController(resourceRegistration)) {
            resourceRegistration.registerReadOnlyAttribute(STATE, new ElytronRuntimeOnlyHandler() {

                @Override
                protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
                    ServiceName keyStoreName = FILTERING_KEY_STORE_UTIL.serviceName(operation);
                    ServiceController<?> serviceController = context.getServiceRegistry(false).getRequiredService(keyStoreName);

                    populateResponse(context.getResult(), serviceController);
                }

            });
        }
    }

    private static class KeyStoreAddHandler extends BaseAddHandler {

        private KeyStoreAddHandler() {
            super(KEY_STORE_RUNTIME_CAPABILITY);
        }

        @Override
        protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource)
                throws OperationFailedException {
            super.recordCapabilitiesAndRequirements(context, operation, resource);

            if (requiresRuntime(context)) {
                FilteringKeyStoreDoohickey doohickey = new FilteringKeyStoreDoohickey(context.getCurrentAddress());
                context.registerCapability(RuntimeCapability.Builder
                        .<ExceptionFunction<OperationContext, KeyStore, OperationFailedException>> of(
                                KEY_STORE_API_CAPABILITY, true, doohickey)
                        .build().fromBaseCapability(context.getCurrentAddressValue()));
            }
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            final String name = context.getCurrentAddressValue();
            ModelNode model = resource.getModel();

            FilteringKeyStoreDoohickey doohickey = null;
            if (requiresRuntime(context)) {
                ExceptionFunction<OperationContext, KeyStore, OperationFailedException> runtimeApi =
                        context.getCapabilityRuntimeAPI(KEY_STORE_API_CAPABILITY, name, ExceptionFunction.class);
                doohickey = (FilteringKeyStoreDoohickey) runtimeApi;
                doohickey.resolveRuntime(context);
            }

            String sourceKeyStoreName = KEY_STORE.resolveModelAttribute(context, model).asStringOrNull();
            String aliasFilter = ALIAS_FILTER.resolveModelAttribute(context, model).asStringOrNull();

            String sourceKeyStoreCapability = RuntimeCapability.buildDynamicCapabilityName(KEY_STORE_CAPABILITY, sourceKeyStoreName);
            ServiceName sourceKeyStoreServiceName = context.getCapabilityServiceName(sourceKeyStoreCapability, KeyStore.class);

            final InjectedValue<KeyStore> keyStore = new InjectedValue<>();
            FilteringKeyStoreService filteringKeyStoreService = new FilteringKeyStoreService(keyStore, aliasFilter);

            if (doohickey != null) {
                doohickey.setFilteringKeyStoreService(filteringKeyStoreService);
                filteringKeyStoreService.setDoohickey(doohickey);
            }

            ServiceTarget serviceTarget = context.getServiceTarget();
            RuntimeCapability<Void> runtimeCapability = KEY_STORE_RUNTIME_CAPABILITY.fromBaseCapability(name);
            ServiceName serviceName = runtimeCapability.getCapabilityServiceName(KeyStore.class);
            ServiceBuilder<KeyStore> serviceBuilder = serviceTarget.addService(serviceName, filteringKeyStoreService)
                    .setInitialMode(ServiceController.Mode.ACTIVE);

            FILTERING_KEY_STORE_UTIL.addInjection(serviceBuilder, keyStore, sourceKeyStoreServiceName);

            commonDependencies(serviceBuilder).install();
        }
    }

    /**
     * Doohickey for a filtering-key-store resource.  Enforces the single-creation contract:
     * whichever path (early API or MSC service start) runs first builds the canonical
     * {@link FilteringKeyStore} instance; the other path reuses it.
     */
    private static class FilteringKeyStoreDoohickey extends ElytronDoohickey<KeyStore> {

        private volatile String sourceKeyStoreName;
        private volatile String aliasFilter;

        // Cross-link set during performRuntime.
        private volatile FilteringKeyStoreService filteringKeyStoreService;

        FilteringKeyStoreDoohickey(PathAddress resourceAddress) {
            super(resourceAddress);
        }

        void setFilteringKeyStoreService(FilteringKeyStoreService service) {
            this.filteringKeyStoreService = service;
        }

        @Override
        protected void resolveRuntime(ModelNode model, OperationContext ctx) throws OperationFailedException {
            sourceKeyStoreName = KEY_STORE.resolveModelAttribute(ctx, model).asStringOrNull();
            aliasFilter = ALIAS_FILTER.resolveModelAttribute(ctx, model).asStringOrNull();
        }

        @Override
        protected ExceptionSupplier<KeyStore, StartException> prepareServiceSupplier(OperationContext ctx,
                CapabilityServiceBuilder<?> serviceBuilder) throws OperationFailedException {
            // FilteringKeyStoreService manages its own lifecycle; delegate to its getValue().
            return () -> filteringKeyStoreService.getValue();
        }

        @Override
        protected KeyStore createImmediately(OperationContext foreignContext) throws OperationFailedException {
            @SuppressWarnings("unchecked")
            ExceptionFunction<OperationContext, KeyStore, OperationFailedException> sourceApi =
                    foreignContext.getCapabilityRuntimeAPI(KEY_STORE_API_CAPABILITY,
                            sourceKeyStoreName, ExceptionFunction.class);
            KeyStore sourceKeyStore = sourceApi.apply(foreignContext);
            try {
                KeyStore filtered = FilteringKeyStore.filteringKeyStore(sourceKeyStore,
                        AliasFilter.fromString(aliasFilter));
                setValue(filtered);
                return filtered;
            } catch (Exception e) {
                throw new OperationFailedException(e);
            }
        }
    }
}
