/*
 * Copyright 2021 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.CREDENTIAL_STORE_API_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.CREDENTIAL_STORE_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;
import static org.wildfly.extension.elytron.ElytronExtension.isServerOrHostController;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.RELATIVE_TO;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathName;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathResolver;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;
import static org.wildfly.security.encryption.SecretKeyUtil.generateSecretKey;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.crypto.SecretKey;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.AttachmentKey;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartException;
import org.wildfly.extension.elytron.FileAttributeDefinitions.PathResolver;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;
import org.wildfly.security.credential.SecretKeyCredential;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.CredentialStoreException;

/**
 * A resource definitions for a simple credential store which just supports the storage of
 * {@link SecretKey} instances stored in the clear.
 *
 * Whilst the keys are stored in the clear this resource allows administrators to bootstrap in
 * an initial key which can be used to encrypt passwords defined within the management model.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class SecretKeyCredentialStoreDefinition extends AbstractCredentialStoreResourceDefinition {

    private static final String CREDENTIAL_STORE_TYPE = "PropertiesCredentialStore";

    static final SimpleAttributeDefinition PATH =
            new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PATH, FileAttributeDefinitions.PATH)
                    .setAttributeGroup(ElytronDescriptionConstants.FILE)
                    .setRequired(true)
                    .setRestartAllServices()
                    .build();

    static final SimpleAttributeDefinition CREATE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CREATE, ModelType.BOOLEAN, true)
            .setAttributeGroup(ElytronDescriptionConstants.IMPLEMENTATION)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.TRUE)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition POPULATE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.POPULATE, ModelType.BOOLEAN, true)
            .setAttributeGroup(ElytronDescriptionConstants.IMPLEMENTATION)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.TRUE)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition KEY_SIZE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEY_SIZE, ModelType.INT, true)
            .setAttributeGroup(ElytronDescriptionConstants.IMPLEMENTATION)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(256))
            .setAllowedValues(128, 192, 256)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition DEFAULT_ALIAS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DEFAULT_ALIAS, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.IMPLEMENTATION)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode("key"))
            .setRestartAllServices()
            .build();

    // Resource Resolver
    private static final StandardResourceDescriptionResolver RESOURCE_RESOLVER = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.SECRET_KEY_CREDENTIAL_STORE);

    static final AttributeDefinition[] CONFIG_ATTRIBUTES = new AttributeDefinition[] { RELATIVE_TO, PATH, CREATE, POPULATE, KEY_SIZE, DEFAULT_ALIAS };

    private static final AbstractAddStepHandler ADD = new SecretKeyCredentialStoreAddHandler();
    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, CREDENTIAL_STORE_RUNTIME_CAPABILITY);

    // Operation Definitions and Parameters

    private static final SimpleOperationDefinition REMOVE_ALIAS = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.REMOVE_ALIAS, RESOURCE_RESOLVER)
            .setParameters(ALIAS)
            .setRuntimeOnly()
            .build();

    // TODO Can we move up if we switch to a common ResourceResolver?
    private static final SimpleOperationDefinition READ_ALIASES = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.READ_ALIASES, RESOURCE_RESOLVER)
            .setRuntimeOnly()
            .setReadOnly()
            .build();

    private static final SimpleOperationDefinition EXPORT_SECRET_KEY = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.EXPORT_SECRET_KEY, RESOURCE_RESOLVER)
            .setParameters(ALIAS)
            .setRuntimeOnly()
            .build();

    private static final SimpleOperationDefinition IMPORT_SECRET_KEY = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.IMPORT_SECRET_KEY, RESOURCE_RESOLVER)
            .setParameters(ALIAS, KEY)
            .setRuntimeOnly()
            .build();

    private static final SimpleAttributeDefinition KEY_SIZE_PARAMETER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEY_SIZE, ModelType.INT, true)
            .setAllowExpression(true)
            .setAllowedValues(128, 192, 256)
            .setRestartAllServices()
            .build();

    private static final SimpleOperationDefinition GENERATE_SECRET_KEY = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.GENERATE_SECRET_KEY, RESOURCE_RESOLVER)
            .setParameters(ALIAS, KEY_SIZE_PARAMETER)
            .setRuntimeOnly()
            .build();

    SecretKeyCredentialStoreDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.SECRET_KEY_CREDENTIAL_STORE), RESOURCE_RESOLVER)
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_NONE)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_NONE)
                .setCapabilities(CREDENTIAL_STORE_RUNTIME_CAPABILITY)
        );
    }

    @Override
    protected AttributeDefinition[] getAttributeDefinitions() {
        return CONFIG_ATTRIBUTES;
    }


    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration); // Always needed to register add / remove.

        boolean isServerOrHostController = isServerOrHostController(resourceRegistration);
        Map<String, CredentialStoreRuntimeOperation> operationMethods = new HashMap<>();
        operationMethods.put(ElytronDescriptionConstants.READ_ALIASES, this::readAliasesOperation);
        if (isServerOrHostController) {
            operationMethods.put(ElytronDescriptionConstants.REMOVE_ALIAS, this::removeAliasOperation);
            operationMethods.put(ElytronDescriptionConstants.EXPORT_SECRET_KEY, this::exportSecretKeyOperation);
            operationMethods.put(ElytronDescriptionConstants.GENERATE_SECRET_KEY, this::generateSecretKeyOperation);
            operationMethods.put(ElytronDescriptionConstants.IMPORT_SECRET_KEY, this::importSecretKeyOperation);
        }

        OperationStepHandler operationHandler = new SecretKeyCredentialStoreRuntimeHandler(operationMethods);
        resourceRegistration.registerOperationHandler(READ_ALIASES, operationHandler);
        if (isServerOrHostController) {
            resourceRegistration.registerOperationHandler(REMOVE_ALIAS, operationHandler);
            resourceRegistration.registerOperationHandler(EXPORT_SECRET_KEY, operationHandler);
            resourceRegistration.registerOperationHandler(GENERATE_SECRET_KEY, operationHandler);
            resourceRegistration.registerOperationHandler(IMPORT_SECRET_KEY, operationHandler);
        }

        // TODO - Need to add reload but TBH the implementation on CredentialStoreResourceDefinition is not great.
    }

    /*
     * Operation Handler Methods.
     */

    void removeAliasOperation(OperationContext context, ModelNode operation, CredentialStore credentialStore) throws OperationFailedException {
        super.removeAliasOperation(context, operation, credentialStore, SecretKeyCredential.class);
    }

    protected void generateSecretKeyOperation(OperationContext context, ModelNode operation, CredentialStore credentialStore) throws OperationFailedException {
        final int keySize;
        ModelNode keySizeModel = KEY_SIZE_PARAMETER.resolveModelAttribute(context, operation);
        if (keySizeModel.isDefined()) {
            keySize = keySizeModel.asInt();
        } else {
            ModelNode resourceModel = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
            keySize = KEY_SIZE.resolveModelAttribute(context, resourceModel).asInt();
        }

        generateSecretKeyOperation(context, operation, credentialStore, keySize);
    }



    class SecretKeyCredentialStoreRuntimeHandler extends CredentialStoreRuntimeHandler {

        protected SecretKeyCredentialStoreRuntimeHandler(Map<String, CredentialStoreRuntimeOperation> definedOperations) {
            super(definedOperations);
        }

        @Override
        protected CredentialStore getCredentialStore(OperationContext context) throws OperationFailedException {
            final Supplier<CredentialStore> credentialStoreApi = context
                    .getCapabilityRuntimeAPI(CREDENTIAL_STORE_API_CAPABILITY, context.getCurrentAddressValue(), Supplier.class);

            return credentialStoreApi.get();
        }

    }

    private static class RutimeApiMapper {

        private final Map<String, Consumer<Supplier<CredentialStore>>> mapping = new HashMap<>();

        void require(final String name, final Consumer<Supplier<CredentialStore>> consumer) {
            mapping.put(name, consumer);
        }

        void provide(final String name, Supplier<CredentialStore> supplier) {
            Consumer<Supplier<CredentialStore>> consumer = mapping.remove(name);
            consumer.accept(supplier);
        }

    }

    private static class SecretKeyCredentialStoreAddHandler extends BaseAddHandler {

        private static final AttachmentKey<RutimeApiMapper> API_MAPPER_KEY = AttachmentKey.create(RutimeApiMapper.class);

        private SecretKeyCredentialStoreAddHandler() {
            super(CREDENTIAL_STORE_RUNTIME_CAPABILITY, CONFIG_ATTRIBUTES);
        }

        @Override
        protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource)
                throws OperationFailedException {
            super.recordCapabilitiesAndRequirements(context, operation, resource);
            // Just add one capability to allow access to the CredentialStore using the runtime API.
            RutimeApiMapper apiMapper = context.getAttachment(API_MAPPER_KEY);
            if (apiMapper == null) {
                apiMapper = new RutimeApiMapper();
                context.attach(API_MAPPER_KEY, apiMapper);
            }

            CredentialStoreSupplier credentialStoreSupplier = new CredentialStoreSupplier();
            context.registerCapability(RuntimeCapability.Builder
                    .<Supplier<CredentialStore>>of(CREDENTIAL_STORE_API_CAPABILITY, true, credentialStoreSupplier).build()
                    .fromBaseCapability(context.getCurrentAddressValue()));
            apiMapper.require(context.getCurrentAddressValue(), credentialStoreSupplier::setDelegate);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            final String name = context.getCurrentAddressValue();

            ModelNode model = resource.getModel();
            final String relativeTo = RELATIVE_TO.resolveModelAttribute(context, model).asStringOrNull();
            final String path = PATH.resolveModelAttribute(context, model).asString();
            final boolean create = CREATE.resolveModelAttribute(context, model).asBoolean();
            final boolean populate = POPULATE.resolveModelAttribute(context, model).asBoolean();
            final int keySize = KEY_SIZE.resolveModelAttribute(context, model).asInt();
            final String defaultAlias = DEFAULT_ALIAS.resolveModelAttribute(context, model).asString();

            CapabilityServiceBuilder<?> serviceBuilder = context.getCapabilityServiceTarget().addCapability(CREDENTIAL_STORE_RUNTIME_CAPABILITY);

            final Supplier<PathManagerService> pathManager;
            if (relativeTo != null) {
                pathManager = serviceBuilder.requires(PathManagerService.SERVICE_NAME);
                serviceBuilder.requires(pathName(relativeTo));
            } else {
                pathManager = null;
            }

            Consumer<CredentialStore> valueConsumer = serviceBuilder.provides(CREDENTIAL_STORE_RUNTIME_CAPABILITY); // Does this
                                                                                                                    // take into
                                                                                                                    // account
                                                                                                                    // the
                                                                                                                    // dynamic
                                                                                                                    // portion?
            final OneTimeValueSupplier oneTimeSupplier = new OneTimeValueSupplier(() -> {
                try {
                    CredentialStore credentialStore = CredentialStore.getInstance(CREDENTIAL_STORE_TYPE);

                    PathResolver pathResolver = pathResolver();
                    pathResolver.path(path);
                    if (relativeTo != null) {
                        pathResolver.relativeTo(relativeTo, pathManager.get());
                    }
                    File resolved = pathResolver.resolve();
                    pathResolver.clear();

                    Map<String, String> configuration = new HashMap<>();
                    configuration.put(ElytronDescriptionConstants.LOCATION, resolved.getAbsolutePath());
                    if (create) {
                        configuration.put(ElytronDescriptionConstants.CREATE, Boolean.TRUE.toString());
                    }
                    credentialStore.initialize(configuration);
                    if (populate && !credentialStore.getAliases().contains(defaultAlias)) {
                        SecretKey secretKey = generateSecretKey(keySize);
                        SecretKeyCredential credential = new SecretKeyCredential(secretKey);

                        credentialStore.store(defaultAlias, credential);
                        credentialStore.flush();
                    }

                    return credentialStore;
                } catch (NoSuchAlgorithmException | CredentialStoreException e) {
                    throw ROOT_LOGGER.unableToStartService(e);
                }
            });
            final TrivialService<CredentialStore> trivialService = new TrivialService<>(oneTimeSupplier, valueConsumer);

            context.getAttachment(API_MAPPER_KEY).provide(context.getCurrentAddressValue(), oneTimeSupplier::getUnchecked);

            commonDependencies(serviceBuilder.setInitialMode(Mode.ACTIVE).setInstance(trivialService)).install();
        }

    }

    private static class OneTimeValueSupplier implements ValueSupplier<CredentialStore> {

        private final ValueSupplier<CredentialStore> rawSupplier;
        private volatile CredentialStore value = null;

        OneTimeValueSupplier(ValueSupplier<CredentialStore> rawSupplier) {
            this.rawSupplier = rawSupplier;
        }

        @Override
        public CredentialStore get() throws StartException {
            if (value == null) {
                synchronized(this) {
                    if (value == null) {
                        value = rawSupplier.get();
                    }
                }
            }

            return value;
        }

        public CredentialStore getUnchecked() {
            try {
                return get();
            } catch (StartException e) {
                throw new IllegalStateException(e);
            }
        }

    }

}
