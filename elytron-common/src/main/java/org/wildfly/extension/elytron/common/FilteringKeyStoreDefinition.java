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

package org.wildfly.extension.elytron.common;

import static org.wildfly.extension.elytron.common.ElytronCommonCapabilities.KEY_STORE_CAPABILITY;
import static org.wildfly.extension.elytron.common.ElytronCommonCapabilities.KEY_STORE_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.common.ElytronCommonDefinitions.commonRequirements;
import static org.wildfly.extension.elytron.common.ElytronCommonDefinitions.isServerOrHostController;

import java.security.KeyStore;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
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
import org.jboss.msc.value.InjectedValue;
import org.wildfly.security.keystore.FilteringKeyStore;

/**
 * A {@link ResourceDefinition} for a single {@link FilteringKeyStore}.
 *
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
public class FilteringKeyStoreDefinition extends SimpleResourceDefinition {

    public static final ServiceUtil<KeyStore> FILTERING_KEY_STORE_UTIL = ServiceUtil.newInstance(KEY_STORE_RUNTIME_CAPABILITY, ElytronCommonConstants.FILTERING_KEY_STORE, KeyStore.class);

    public static final SimpleAttributeDefinition KEY_STORE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.KEY_STORE, ModelType.STRING, false)
            .setAllowExpression(false)
            .setRestartAllServices()
            .setCapabilityReference(KEY_STORE_CAPABILITY, KEY_STORE_CAPABILITY)
            .build();

    public static final SimpleAttributeDefinition ALIAS_FILTER = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.ALIAS_FILTER, ModelType.STRING, false)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    private static final AttributeDefinition[] CONFIG_ATTRIBUTES = new AttributeDefinition[] { KEY_STORE, ALIAS_FILTER };
    private static final ElytronReloadRequiredWriteAttributeHandler WRITE = new ElytronReloadRequiredWriteAttributeHandler(CONFIG_ATTRIBUTES);

    public static FilteringKeyStoreDefinition configure(final Class<?> extensionClass) {
        StandardResourceDescriptionResolver filterKSResourceResolver = ElytronCommonDefinitions.getResourceDescriptionResolver(extensionClass, ElytronCommonConstants.FILTERING_KEY_STORE);

        KeyStoreAddHandler filterKSAddHandler = new KeyStoreAddHandler(extensionClass);
        TrivialCapabilityServiceRemoveHandler filterKSRemoveHandler = new TrivialCapabilityServiceRemoveHandler(filterKSAddHandler, KEY_STORE_RUNTIME_CAPABILITY);

        return new FilteringKeyStoreDefinition(filterKSResourceResolver, filterKSAddHandler, filterKSRemoveHandler);
    }

    protected FilteringKeyStoreDefinition(StandardResourceDescriptionResolver filterKSResourceResolver, KeyStoreAddHandler filterKSAddHandler,
                                        TrivialCapabilityServiceRemoveHandler filterKSRemoveHandler) {
        super(new Parameters(PathElement.pathElement(ElytronCommonConstants.FILTERING_KEY_STORE), filterKSResourceResolver)
                .setAddHandler(filterKSAddHandler)
                .setRemoveHandler(filterKSRemoveHandler)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(KEY_STORE_RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition current : CONFIG_ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, WRITE);
        }

        if (isServerOrHostController(resourceRegistration)) {
            resourceRegistration.registerReadOnlyAttribute(ServiceStateDefinition.STATE, new ElytronRuntimeOnlyHandler() {

                @Override
                protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
                    ServiceName keyStoreName = FILTERING_KEY_STORE_UTIL.serviceName(operation);
                    ServiceController<?> serviceController = context.getServiceRegistry(false).getRequiredService(keyStoreName);

                    ServiceStateDefinition.populateResponse(context.getResult(), serviceController);
                }

            });
        }
    }

    private static class KeyStoreAddHandler extends ElytronCommonBaseAddHandler {
        private final Class<?> extensionClass;

        private KeyStoreAddHandler(final Class<?> extensionClass) {
            super(KEY_STORE_RUNTIME_CAPABILITY, CONFIG_ATTRIBUTES);
            this.extensionClass = extensionClass;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            ModelNode model = resource.getModel();

            String sourceKeyStoreName = KEY_STORE.resolveModelAttribute(context, model).asStringOrNull();
            String aliasFilter = ALIAS_FILTER.resolveModelAttribute(context, model).asStringOrNull();

            String sourceKeyStoreCapability = RuntimeCapability.buildDynamicCapabilityName(KEY_STORE_CAPABILITY, sourceKeyStoreName);
            ServiceName sourceKeyStoreServiceName = context.getCapabilityServiceName(sourceKeyStoreCapability, KeyStore.class);


            final InjectedValue<KeyStore> keyStore = new InjectedValue<>();

            FilteringKeyStoreService filteringKeyStoreService = new FilteringKeyStoreService(keyStore, aliasFilter);

            ServiceTarget serviceTarget = context.getServiceTarget();
            RuntimeCapability<Void> runtimeCapability = KEY_STORE_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName serviceName = runtimeCapability.getCapabilityServiceName(KeyStore.class);
            ServiceBuilder<KeyStore> serviceBuilder = serviceTarget.addService(serviceName, filteringKeyStoreService).setInitialMode(ServiceController.Mode.ACTIVE);

            FILTERING_KEY_STORE_UTIL.addInjection(serviceBuilder, keyStore, sourceKeyStoreServiceName);

            commonRequirements(extensionClass, serviceBuilder).install();
        }
    }
}
