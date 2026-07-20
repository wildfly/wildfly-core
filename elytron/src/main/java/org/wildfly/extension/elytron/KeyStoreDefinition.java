/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;
import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PROVIDERS_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SCHEDULED_EXECUTOR_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;
import static org.wildfly.extension.elytron.ElytronExtension.ISO_8601_FORMAT;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
import static org.wildfly.extension.elytron.ElytronExtension.isServerOrHostController;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.PATH;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.RELATIVE_TO;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathName;
import static org.wildfly.extension.elytron.ProviderAttributeDefinition.LOADED_PROVIDER;
import static org.wildfly.extension.elytron.ProviderAttributeDefinition.populateProvider;
import static org.wildfly.extension.elytron.ServiceStateDefinition.STATE;
import static org.wildfly.extension.elytron.ServiceStateDefinition.populateResponse;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.RollbackHandler;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.LongRangeValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.extension.elytron.KeyStoreService.LoadKey;

/**
 * A {@link ResourceDefinition} for a single {@link KeyStore}.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
final class KeyStoreDefinition extends SimpleResourceDefinition {

    static final ServiceUtil<KeyStore> KEY_STORE_UTIL = ServiceUtil.newInstance(KEY_STORE_RUNTIME_CAPABILITY, ElytronDescriptionConstants.KEY_STORE, KeyStore.class);

    static final SimpleAttributeDefinition TYPE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.TYPE, ModelType.STRING, true)
        .setAttributeGroup(ElytronDescriptionConstants.IMPLEMENTATION)
        .setAllowExpression(true)
        .setMinSize(1)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition PROVIDER_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PROVIDER_NAME, ModelType.STRING, true)
        .setAttributeGroup(ElytronDescriptionConstants.IMPLEMENTATION)
        .setAllowExpression(true)
        .setMinSize(1)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition PROVIDERS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PROVIDERS, ModelType.STRING, true)
        .setAttributeGroup(ElytronDescriptionConstants.IMPLEMENTATION)
        .setMinSize(1)
        .setRestartAllServices()
        .setCapabilityReference(PROVIDERS_CAPABILITY, KEY_STORE_CAPABILITY)
        .build();

    static final ObjectTypeAttributeDefinition CREDENTIAL_REFERENCE = CredentialReference.getAttributeDefinition(true);

    static final SimpleAttributeDefinition REQUIRED = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REQUIRED, ModelType.BOOLEAN, true)
        .setDefaultValue(ModelNode.FALSE)
        .setAllowExpression(true)
        .setAttributeGroup(ElytronDescriptionConstants.FILE)
        .setRequires(ElytronDescriptionConstants.PATH)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition ALIAS_FILTER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALIAS_FILTER, ModelType.STRING, true)
        .setAllowExpression(true)
        .setMinSize(1)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition EXPIRATION_CHECK_DELAY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.EXPIRATION_CHECK_DELAY, ModelType.LONG, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(KeyStoreService.DEFAULT_DELAY))
            .setValidator(new LongRangeValidator(0, Long.MAX_VALUE, true, true))
            .setRestartAllServices()
            .setStability(Stability.COMMUNITY)
            .setMeasurementUnit(MeasurementUnit.MINUTES)
            .build();

    static final SimpleAttributeDefinition EXPIRATION_WATERMARK = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.EXPIRATION_WATERMARK, ModelType.LONG, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(KeyStoreService.DEFAULT_EXPIRATION_WATERMARK))
            .setValidator(new LongRangeValidator(0, Long.MAX_VALUE, true, true))
            .setStability(Stability.COMMUNITY)
            .setMeasurementUnit(MeasurementUnit.MINUTES)
            .build();
    // Resource Resolver

    private static final StandardResourceDescriptionResolver RESOURCE_RESOLVER = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.KEY_STORE);

    // Runtime Attributes

    private static final SimpleAttributeDefinition SIZE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SIZE, ModelType.INT)
        .setStorageRuntime()
        .build();

    private static final SimpleAttributeDefinition SYNCHRONIZED = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SYNCHRONIZED, ModelType.STRING)
        .setStorageRuntime()
        .build();

    private static final SimpleAttributeDefinition MODIFIED = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MODIFIED, ModelType.BOOLEAN)
        .setStorageRuntime()
        .build();

    // Operations

    private static final SimpleOperationDefinition LOAD = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.LOAD, RESOURCE_RESOLVER)
        .setRuntimeOnly()
        .build();

    private static final SimpleOperationDefinition STORE = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.STORE, RESOURCE_RESOLVER)
        .setRuntimeOnly()
        .build();

    private static final AttributeDefinition[] CONFIG_ATTRIBUTES = new AttributeDefinition[] { TYPE, PROVIDER_NAME, PROVIDERS, CREDENTIAL_REFERENCE, PATH, RELATIVE_TO, REQUIRED, ALIAS_FILTER , EXPIRATION_CHECK_DELAY, EXPIRATION_WATERMARK};

    private static final KeyStoreAddHandler ADD = new KeyStoreAddHandler();
    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, KEY_STORE_RUNTIME_CAPABILITY);

    KeyStoreDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.KEY_STORE), RESOURCE_RESOLVER)
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
                    ServiceName keyStoreName = KEY_STORE_UTIL.serviceName(operation);
                    ServiceController<?> serviceController = context.getServiceRegistry(false).getRequiredService(keyStoreName);

                    populateResponse(context.getResult(), serviceController);
                }

            });

            resourceRegistration.registerReadOnlyAttribute(SIZE, new KeyStoreRuntimeOnlyHandler(false) {

                @Override
                protected void performRuntime(ModelNode result, ModelNode operation, KeyStoreService keyStoreService) throws OperationFailedException {
                    try {
                        result.set(keyStoreService.getValue().size());
                    } catch (KeyStoreException e) {
                        throw ROOT_LOGGER.unableToAccessKeyStore(e);
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

            resourceRegistration.registerReadOnlyAttribute(LOADED_PROVIDER, new KeyStoreRuntimeOnlyHandler(false) {

                @Override
                protected void performRuntime(ModelNode result, ModelNode operation, KeyStoreService keyStoreService)
                        throws OperationFailedException {
                    populateProvider(result, keyStoreService.getValue().getProvider(), false);
                }
            });
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(LOAD, PersistanceHandler.INSTANCE);
        if (isServerOrHostController(resourceRegistration)) {
            resourceRegistration.registerOperationHandler(STORE, PersistanceHandler.INSTANCE);
        }
    }

    private static class KeyStoreAddHandler extends BaseAddHandler {

        private KeyStoreAddHandler() {
            super(KEY_STORE_RUNTIME_CAPABILITY);
        }

        @Override
        protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws  OperationFailedException {
            super.populateModel(context, operation, resource);
            handleCredentialReferenceUpdate(context, resource.getModel());
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            final ModelNode model = resource.getModel();
            final String providers = PROVIDERS.resolveModelAttribute(context, model).asStringOrNull();
            final String providerName = PROVIDER_NAME.resolveModelAttribute(context, model).asStringOrNull();
            final String type = TYPE.resolveModelAttribute(context, model).asStringOrNull();
            final String path = PATH.resolveModelAttribute(context, model).asStringOrNull();
            final long expirationCheckDelay = EXPIRATION_CHECK_DELAY.resolveModelAttribute(context, model).asLong();
            final long expirationWatermark = EXPIRATION_WATERMARK.resolveModelAttribute(context, model).asLong();
            String relativeTo = null;
            boolean required;
            final String aliasFilter = ALIAS_FILTER.resolveModelAttribute(context, model).asStringOrNull();
            final String keyStoreName = context.getCurrentAddressValue();
            final KeyStoreService keyStoreService;

            if (path != null) {
                relativeTo = RELATIVE_TO.resolveModelAttribute(context, model).asStringOrNull();
                required = REQUIRED.resolveModelAttribute(context, model).asBoolean();
                keyStoreService = KeyStoreService.createFileBasedKeyStoreService(keyStoreName, providerName, type, relativeTo, path, required, aliasFilter, expirationCheckDelay, expirationWatermark);
            } else {
                if (type == null) {
                    throw ROOT_LOGGER.filelessKeyStoreMissingType();
                }
                keyStoreService = KeyStoreService.createFileLessKeyStoreService(keyStoreName, providerName, type, aliasFilter, expirationCheckDelay, expirationWatermark);
            }

            final ServiceTarget serviceTarget = context.getServiceTarget();
            final RuntimeCapability<Void> runtimeCapability = KEY_STORE_RUNTIME_CAPABILITY.fromBaseCapability(keyStoreName);
            final ServiceName serviceName = runtimeCapability.getCapabilityServiceName(KeyStore.class);
            final ServiceBuilder<KeyStore> serviceBuilder = serviceTarget.addService(serviceName, keyStoreService).setInitialMode(Mode.ACTIVE);

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

            serviceBuilder.addDependency(SCHEDULED_EXECUTOR_RUNTIME_CAPABILITY.getCapabilityServiceName(), ScheduledExecutorService.class, keyStoreService.getScheduledExecutorInjector());

            commonDependencies(serviceBuilder).install();
        }

        @Override
        protected void rollbackRuntime(OperationContext context, final ModelNode operation, final Resource resource) {
            rollbackCredentialStoreUpdate(KeyStoreDefinition.CREDENTIAL_REFERENCE, context, resource);
        }
    }

    /*
     * Runtime Attribute and Operation Handlers
     */

    abstract static class KeyStoreRuntimeOnlyHandler extends ElytronRuntimeOnlyHandler {

        private final boolean serviceMustBeUp;
        private final boolean writeAccess;

        KeyStoreRuntimeOnlyHandler(final boolean serviceMustBeUp, final boolean writeAccess) {
            this.serviceMustBeUp = serviceMustBeUp;
            this.writeAccess = writeAccess;
        }

        KeyStoreRuntimeOnlyHandler(final boolean serviceMustBeUp) {
            this(serviceMustBeUp, false);
        }


        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            ServiceName keyStoreName = KEY_STORE_UTIL.serviceName(operation);

            ServiceController<KeyStore> serviceContainer = getRequiredService(context.getServiceRegistry(writeAccess), keyStoreName, KeyStore.class);
            State serviceState;
            if ((serviceState = serviceContainer.getState()) != State.UP) {
                if (serviceMustBeUp) {
                    throw ROOT_LOGGER.requiredServiceNotUp(keyStoreName, serviceState);
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
                case ElytronDescriptionConstants.LOAD:
                    final LoadKey loadKey = keyStoreService.load();
                    context.completeStep(new RollbackHandler() {

                        @Override
                        public void handleRollback(OperationContext context, ModelNode operation) {
                            keyStoreService.revertLoad(loadKey);
                        }
                    });
                    break;
                case ElytronDescriptionConstants.STORE:
                    keyStoreService.save();
                    break;
                default:
                    throw ROOT_LOGGER.invalidOperationName(operationName, ElytronDescriptionConstants.LOAD,
                            ElytronDescriptionConstants.STORE);
            }

        }

    }

}
