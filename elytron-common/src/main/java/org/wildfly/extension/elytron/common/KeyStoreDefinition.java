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

package org.wildfly.extension.elytron.common;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;
import static org.wildfly.extension.elytron.common.ElytronCommonCapabilities.KEY_STORE_CAPABILITY;
import static org.wildfly.extension.elytron.common.ElytronCommonCapabilities.KEY_STORE_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.common.ElytronCommonCapabilities.PROVIDERS_CAPABILITY;
import static org.wildfly.extension.elytron.common.ElytronCommonDefinitions.commonRequirements;
import static org.wildfly.extension.elytron.common.ElytronCommonDefinitions.ISO_8601_FORMAT;
import static org.wildfly.extension.elytron.common.ElytronCommonDefinitions.getRequiredService;
import static org.wildfly.extension.elytron.common.ElytronCommonDefinitions.isServerOrHostController;
import static org.wildfly.extension.elytron.common.FileAttributeDefinitions.PATH;
import static org.wildfly.extension.elytron.common.FileAttributeDefinitions.RELATIVE_TO;
import static org.wildfly.extension.elytron.common.FileAttributeDefinitions.pathName;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.RollbackHandler;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.extension.elytron.common.util.ElytronCommonMessages;
import org.wildfly.extension.elytron.common.KeyStoreService.LoadKey;

/**
 * A {@link ResourceDefinition} for a single {@link KeyStore}.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public final class KeyStoreDefinition extends SimpleResourceDefinition {

    public static final ServiceUtil<KeyStore> KEY_STORE_UTIL = ServiceUtil.newInstance(KEY_STORE_RUNTIME_CAPABILITY, ElytronCommonConstants.KEY_STORE, KeyStore.class);

    public static final SimpleAttributeDefinition TYPE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.TYPE, ModelType.STRING, true)
        .setAttributeGroup(ElytronCommonConstants.IMPLEMENTATION)
        .setAllowExpression(true)
        .setMinSize(1)
        .setRestartAllServices()
        .build();

    public static final SimpleAttributeDefinition PROVIDER_NAME = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.PROVIDER_NAME, ModelType.STRING, true)
        .setAttributeGroup(ElytronCommonConstants.IMPLEMENTATION)
        .setAllowExpression(true)
        .setMinSize(1)
        .setRestartAllServices()
        .build();

    public static final SimpleAttributeDefinition PROVIDERS = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.PROVIDERS, ModelType.STRING, true)
        .setAttributeGroup(ElytronCommonConstants.IMPLEMENTATION)
        .setMinSize(1)
        .setRestartAllServices()
        .setCapabilityReference(PROVIDERS_CAPABILITY, KEY_STORE_CAPABILITY)
        .build();

    public static final ObjectTypeAttributeDefinition CREDENTIAL_REFERENCE = CredentialReference.getAttributeDefinition(true);

    public static final SimpleAttributeDefinition REQUIRED = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.REQUIRED, ModelType.BOOLEAN, true)
        .setDefaultValue(ModelNode.FALSE)
        .setAllowExpression(true)
        .setAttributeGroup(ElytronCommonConstants.FILE)
        .setRequires(ElytronCommonConstants.PATH)
        .setRestartAllServices()
        .build();

    public static final SimpleAttributeDefinition ALIAS_FILTER = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.ALIAS_FILTER, ModelType.STRING, true)
        .setAllowExpression(true)
        .setMinSize(1)
        .setRestartAllServices()
        .build();

    // Runtime Attributes

    private static final SimpleAttributeDefinition SIZE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.SIZE, ModelType.INT)
        .setStorageRuntime()
        .build();

    private static final SimpleAttributeDefinition SYNCHRONIZED = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.SYNCHRONIZED, ModelType.STRING)
        .setStorageRuntime()
        .build();

    private static final SimpleAttributeDefinition MODIFIED = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.MODIFIED, ModelType.BOOLEAN)
        .setStorageRuntime()
        .build();

    // Operations

    private final SimpleOperationDefinition loadOperation;

    private final SimpleOperationDefinition storeOperation;

    private static final AttributeDefinition[] CONFIG_ATTRIBUTES = new AttributeDefinition[] { TYPE, PROVIDER_NAME, PROVIDERS, CREDENTIAL_REFERENCE, PATH, RELATIVE_TO, REQUIRED, ALIAS_FILTER };
    private static final AbstractWriteAttributeHandler WRITE = new ElytronReloadRequiredWriteAttributeHandler(CONFIG_ATTRIBUTES);

    public static KeyStoreDefinition configure(final Class<?> extensionClass) {
        StandardResourceDescriptionResolver ksResourceResolver = ElytronCommonDefinitions.getResourceDescriptionResolver(extensionClass,
                ElytronCommonConstants.KEY_STORE);

        KeyStoreAddHandler ksAddHandler = new KeyStoreAddHandler(extensionClass);
        TrivialCapabilityServiceRemoveHandler ksRemoveHandler = new TrivialCapabilityServiceRemoveHandler(ksAddHandler,
                KEY_STORE_RUNTIME_CAPABILITY);

        SimpleOperationDefinition ksLoadOperation = new SimpleOperationDefinitionBuilder(ElytronCommonConstants.LOAD, ksResourceResolver)
                .setRuntimeOnly()
                .build();

        SimpleOperationDefinition ksStoreOperation = new SimpleOperationDefinitionBuilder(ElytronCommonConstants.STORE, ksResourceResolver)
                .setRuntimeOnly()
                .build();

        return new KeyStoreDefinition(ksResourceResolver, ksAddHandler, ksRemoveHandler, ksLoadOperation, ksStoreOperation);
    }

    private KeyStoreDefinition(StandardResourceDescriptionResolver ksResourceResolver, KeyStoreAddHandler ksAddHandler,
                               TrivialCapabilityServiceRemoveHandler ksRemoveHandler, SimpleOperationDefinition ksLoadOperation,
                               SimpleOperationDefinition ksStoreOperation) {

        super(new Parameters(PathElement.pathElement(ElytronCommonConstants.KEY_STORE), ksResourceResolver)
            .setAddHandler(ksAddHandler)
            .setRemoveHandler(ksRemoveHandler)
            .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilities(KEY_STORE_RUNTIME_CAPABILITY));

        this.loadOperation = ksLoadOperation;
        this.storeOperation = ksStoreOperation;
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
                    ServiceName keyStoreName = KEY_STORE_UTIL.serviceName(operation);
                    ServiceController<?> serviceController = context.getServiceRegistry(false).getRequiredService(keyStoreName);

                    ServiceStateDefinition.populateResponse(context.getResult(), serviceController);
                }

            });

            resourceRegistration.registerReadOnlyAttribute(SIZE, new KeyStoreRuntimeOnlyHandler(false) {

                @Override
                protected void performRuntime(ModelNode result, ModelNode operation, KeyStoreService keyStoreService) throws OperationFailedException {
                    try {
                        result.set(keyStoreService.getValue().size());
                    } catch (KeyStoreException e) {
                        throw ElytronCommonMessages.ROOT_LOGGER.unableToAccessKeyStore(e);
                    }
                }
            });

            resourceRegistration.registerReadOnlyAttribute(SYNCHRONIZED, new KeyStoreRuntimeOnlyHandler(false) {

                @Override
                protected void performRuntime(ModelNode result, ModelNode operation, KeyStoreService keyStoreService) throws OperationFailedException {
                    SimpleDateFormat sdf = new SimpleDateFormat(ISO_8601_FORMAT);
                    result.set(sdf.format(new Date(keyStoreService.timeSynched())));
                }
            });

            resourceRegistration.registerReadOnlyAttribute(MODIFIED, new KeyStoreRuntimeOnlyHandler(false) {

                @Override
                protected void performRuntime(ModelNode result, ModelNode operation, KeyStoreService keyStoreService) throws OperationFailedException {
                    result.set(keyStoreService.isModified());
                }
            });

            resourceRegistration.registerReadOnlyAttribute(ProviderAttributeDefinition.LOADED_PROVIDER, new KeyStoreRuntimeOnlyHandler(false) {

                @Override
                protected void performRuntime(ModelNode result, ModelNode operation, KeyStoreService keyStoreService)
                        throws OperationFailedException {
                    ProviderAttributeDefinition.populateProvider(result, keyStoreService.getValue().getProvider(), false);
                }
            });
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(loadOperation, PersistanceHandler.INSTANCE);
        if (isServerOrHostController(resourceRegistration)) {
            resourceRegistration.registerOperationHandler(storeOperation, PersistanceHandler.INSTANCE);
        }
    }

    private static class KeyStoreAddHandler extends ElytronCommonBaseAddHandler {
        private final Class<?> extensionClass;

        private KeyStoreAddHandler(final Class<?> extensionClass) {
            super(KEY_STORE_RUNTIME_CAPABILITY, CONFIG_ATTRIBUTES);
            this.extensionClass = extensionClass;
        }

        @Override
        protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws  OperationFailedException {
            super.populateModel(context, operation, resource);
            handleCredentialReferenceUpdate(context, resource.getModel());
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            ModelNode model = resource.getModel();
            String providers = PROVIDERS.resolveModelAttribute(context, model).asStringOrNull();
            String providerName = PROVIDER_NAME.resolveModelAttribute(context, model).asStringOrNull();
            String type = TYPE.resolveModelAttribute(context, model).asStringOrNull();
            String path = PATH.resolveModelAttribute(context, model).asStringOrNull();
            String relativeTo = null;
            boolean required;
            String aliasFilter = ALIAS_FILTER.resolveModelAttribute(context, model).asStringOrNull();

            final KeyStoreService keyStoreService;
            if (path != null) {
                relativeTo = RELATIVE_TO.resolveModelAttribute(context, model).asStringOrNull();
                required = REQUIRED.resolveModelAttribute(context, model).asBoolean();
                keyStoreService = KeyStoreService.createFileBasedKeyStoreService(providerName, type, relativeTo, path, required, aliasFilter);
            } else {
                if (type == null) {
                    throw ElytronCommonMessages.ROOT_LOGGER.filelessKeyStoreMissingType();
                }
                keyStoreService = KeyStoreService.createFileLessKeyStoreService(providerName, type, aliasFilter);
            }

            ServiceTarget serviceTarget = context.getServiceTarget();
            RuntimeCapability<Void> runtimeCapability = KEY_STORE_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName serviceName = runtimeCapability.getCapabilityServiceName(KeyStore.class);
            ServiceBuilder<KeyStore> serviceBuilder = serviceTarget.addService(serviceName, keyStoreService).setInitialMode(Mode.ACTIVE);

            serviceBuilder.addDependency(PathManagerService.SERVICE_NAME, PathManager.class, keyStoreService.getPathManagerInjector());
            if (relativeTo != null) {
                serviceBuilder.requires(pathName(relativeTo));
            }

            if (providers != null) {
                String providersCapabilityName = RuntimeCapability.buildDynamicCapabilityName(PROVIDERS_CAPABILITY, providers);
                ServiceName providerLoaderServiceName = context.getCapabilityServiceName(providersCapabilityName, Provider[].class);
                serviceBuilder.addDependency(providerLoaderServiceName, Provider[].class, keyStoreService.getProvidersInjector());
            }

            keyStoreService.getCredentialSourceSupplierInjector()
                    .inject(CredentialReference.getCredentialSourceSupplier(context, KeyStoreDefinition.CREDENTIAL_REFERENCE, model, serviceBuilder));

            commonRequirements(extensionClass, serviceBuilder).install();
        }

        @Override
        protected void rollbackRuntime(OperationContext context, final ModelNode operation, final Resource resource) {
            rollbackCredentialStoreUpdate(KeyStoreDefinition.CREDENTIAL_REFERENCE, context, resource);
        }
    }

    /*
     * Runtime Attribute and Operation Handlers
     */

    protected abstract static class KeyStoreRuntimeOnlyHandler extends ElytronRuntimeOnlyHandler {

        private final boolean serviceMustBeUp;
        private final boolean writeAccess;

        protected KeyStoreRuntimeOnlyHandler(final boolean serviceMustBeUp, final boolean writeAccess) {
            this.serviceMustBeUp = serviceMustBeUp;
            this.writeAccess = writeAccess;
        }

        protected KeyStoreRuntimeOnlyHandler(final boolean serviceMustBeUp) {
            this(serviceMustBeUp, false);
        }


        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            ServiceName keyStoreName = KEY_STORE_UTIL.serviceName(operation);

            ServiceController<KeyStore> serviceContainer = getRequiredService(context.getServiceRegistry(writeAccess), keyStoreName, KeyStore.class);
            State serviceState;
            if ((serviceState = serviceContainer.getState()) != State.UP) {
                if (serviceMustBeUp) {
                    throw ElytronCommonMessages.ROOT_LOGGER.requiredServiceNotUp(keyStoreName, serviceState);
                }
                return;
            }

            performRuntime(context.getResult(), context, operation, (KeyStoreService) serviceContainer.getService());
        }

        protected void performRuntime(ModelNode result, ModelNode operation,  KeyStoreService keyStoreService) throws OperationFailedException {}

        protected void performRuntime(ModelNode result, OperationContext context, ModelNode operation,  KeyStoreService keyStoreService) throws OperationFailedException {
            performRuntime(result, operation, keyStoreService);
        }

    }

    private static class PersistanceHandler extends KeyStoreRuntimeOnlyHandler {

        private static final PersistanceHandler INSTANCE = new PersistanceHandler();

        private PersistanceHandler() {
            super(true, true);
        }

        @Override
        protected void performRuntime(ModelNode result, OperationContext context, ModelNode operation, final KeyStoreService keyStoreService) throws OperationFailedException {
            String operationName = operation.require(OP).asString();
            switch (operationName) {
                case ElytronCommonConstants.LOAD:
                    final LoadKey loadKey = keyStoreService.load();
                    context.completeStep(new RollbackHandler() {

                        @Override
                        public void handleRollback(OperationContext context, ModelNode operation) {
                            keyStoreService.revertLoad(loadKey);
                        }
                    });
                    break;
                case ElytronCommonConstants.STORE:
                    keyStoreService.save();
                    break;
                default:
                    throw ElytronCommonMessages.ROOT_LOGGER.invalidOperationName(operationName, ElytronCommonConstants.LOAD,
                            ElytronCommonConstants.STORE);
            }

        }

    }

}
