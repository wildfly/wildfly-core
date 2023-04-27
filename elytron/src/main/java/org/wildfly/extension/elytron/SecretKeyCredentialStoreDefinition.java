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

import static org.wildfly.extension.elytron.ElytronCommonCapabilities.CREDENTIAL_STORE_API_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronCommonCapabilities.CREDENTIAL_STORE_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronExtension.isServerOrHostController;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.RELATIVE_TO;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathName;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathResolver;
import static org.wildfly.extension.elytron._private.ElytronCommonMessages.ROOT_LOGGER;
import static org.wildfly.security.encryption.SecretKeyUtil.generateSecretKey;

import java.io.File;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.crypto.SecretKey;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.IntAllowedValuesValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.StartException;
import org.wildfly.common.function.ExceptionRunnable;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.extension.elytron.FileAttributeDefinitions.PathResolver;
import org.wildfly.security.credential.SecretKeyCredential;
import org.wildfly.security.credential.store.CredentialStore;

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

    private static final ServiceUtil<CredentialStore> SECRET_KEY_CREDENTIAL_STORE_UTIL = ServiceUtil.newInstance(CREDENTIAL_STORE_RUNTIME_CAPABILITY, ElytronCommonConstants.SECRET_KEY_CREDENTIAL_STORE, CredentialStore.class);

    @Override
    protected ServiceUtil<CredentialStore> getCredentialStoreUtil() {
        return SECRET_KEY_CREDENTIAL_STORE_UTIL;
    }

    static final SimpleAttributeDefinition PATH =
            new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.PATH, FileAttributeDefinitions.PATH)
                    .setAttributeGroup(ElytronCommonConstants.FILE)
                    .setRequired(true)
                    .setRestartAllServices()
                    .build();

    static final SimpleAttributeDefinition CREATE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.CREATE, ModelType.BOOLEAN, true)
            .setAttributeGroup(ElytronCommonConstants.IMPLEMENTATION)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.TRUE)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition POPULATE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.POPULATE, ModelType.BOOLEAN, true)
            .setAttributeGroup(ElytronCommonConstants.IMPLEMENTATION)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.TRUE)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition KEY_SIZE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.KEY_SIZE, ModelType.INT, true)
            .setAttributeGroup(ElytronCommonConstants.IMPLEMENTATION)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(256))
            .setValidator(new IntAllowedValuesValidator(128, 192, 256))
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition DEFAULT_ALIAS = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.DEFAULT_ALIAS, ModelType.STRING, true)
            .setAttributeGroup(ElytronCommonConstants.IMPLEMENTATION)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode("key"))
            .setRestartAllServices()
            .build();

    // Resource Resolver
    private static final StandardResourceDescriptionResolver RESOURCE_RESOLVER = ElytronExtension.getResourceDescriptionResolver(ElytronCommonConstants.SECRET_KEY_CREDENTIAL_STORE);

    static final AttributeDefinition[] CONFIG_ATTRIBUTES = new AttributeDefinition[] { RELATIVE_TO, PATH, CREATE, POPULATE, KEY_SIZE, DEFAULT_ALIAS };

    private static final AbstractAddStepHandler ADD = new SecretKeyCredentialStoreAddHandler();
    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, CREDENTIAL_STORE_RUNTIME_CAPABILITY);

    // Operation Definitions and Parameters

    private static final SimpleOperationDefinition REMOVE_ALIAS = new SimpleOperationDefinitionBuilder(ElytronCommonConstants.REMOVE_ALIAS, OPERATION_RESOLVER)
            .setParameters(ALIAS)
            .setRuntimeOnly()
            .build();

    private static final SimpleAttributeDefinition KEY_SIZE_PARAMETER = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.KEY_SIZE, ModelType.INT, true)
            .setAllowExpression(true)
            .setAllowedValues(128, 192, 256)
            .setRestartAllServices()
            .build();

    private static final SimpleOperationDefinition GENERATE_SECRET_KEY = new SimpleOperationDefinitionBuilder(ElytronCommonConstants.GENERATE_SECRET_KEY, OPERATION_RESOLVER)
            .setParameters(ALIAS, KEY_SIZE_PARAMETER)
            .setRuntimeOnly()
            .build();

    SecretKeyCredentialStoreDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronCommonConstants.SECRET_KEY_CREDENTIAL_STORE), RESOURCE_RESOLVER)
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

        operationMethods.put(ElytronCommonConstants.READ_ALIASES, this::readAliasesOperation);
        if (isServerOrHostController) {
            operationMethods.put(ElytronCommonConstants.REMOVE_ALIAS, this::removeAliasOperation);
            operationMethods.put(ElytronCommonConstants.EXPORT_SECRET_KEY, this::exportSecretKeyOperation);
            operationMethods.put(ElytronCommonConstants.GENERATE_SECRET_KEY, this::generateSecretKeyOperation);
            operationMethods.put(ElytronCommonConstants.IMPORT_SECRET_KEY, this::importSecretKeyOperation);
        }

        OperationStepHandler operationHandler = new CredentialStoreRuntimeHandler(operationMethods);
        resourceRegistration.registerOperationHandler(READ_ALIASES, operationHandler);
        if (isServerOrHostController) {
            resourceRegistration.registerOperationHandler(REMOVE_ALIAS, operationHandler);
            resourceRegistration.registerOperationHandler(EXPORT_SECRET_KEY, operationHandler);
            resourceRegistration.registerOperationHandler(GENERATE_SECRET_KEY, operationHandler);
            resourceRegistration.registerOperationHandler(IMPORT_SECRET_KEY, operationHandler);
            resourceRegistration.registerOperationHandler(RELOAD, RELOAD_HANDLER);
        }
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

    static class SecretKeyCredentialStoreAddHandler extends ElytronCommonDoohickeyAddHandler<CredentialStore> {

        private SecretKeyCredentialStoreAddHandler() {
            super(CREDENTIAL_STORE_RUNTIME_CAPABILITY, CONFIG_ATTRIBUTES, CREDENTIAL_STORE_API_CAPABILITY);
        }

        @Override
        protected ElytronDoohickey<CredentialStore> createDoohickey(PathAddress resourceAddress) {
            return new SecretKeyDoohickey(resourceAddress);
        }

    }

    static class SecretKeyDoohickey extends AbstractCredentialStoreDoohickey {

        private volatile String relativeTo;
        private volatile String path;
        private volatile boolean create;
        private volatile boolean populate;
        private volatile int keySize;
        private volatile String defaultAlias;

        private volatile ExceptionRunnable<GeneralSecurityException> reloader;

        protected SecretKeyDoohickey(PathAddress resourceAddress) {
            super(resourceAddress);
        }

        @Override
        protected void resolveRuntime(ModelNode model, OperationContext context) throws OperationFailedException {
            relativeTo = RELATIVE_TO.resolveModelAttribute(context, model).asStringOrNull();
            path = PATH.resolveModelAttribute(context, model).asString();
            create = CREATE.resolveModelAttribute(context, model).asBoolean();
            populate = POPULATE.resolveModelAttribute(context, model).asBoolean();
            keySize = KEY_SIZE.resolveModelAttribute(context, model).asInt();
            defaultAlias = DEFAULT_ALIAS.resolveModelAttribute(context, model).asString();
        }

        @Override
        protected ExceptionSupplier<CredentialStore, StartException> prepareServiceSupplier(OperationContext context,
                CapabilityServiceBuilder<?> serviceBuilder) {

            final Supplier<PathManagerService> pathManager;
            if (relativeTo != null) {
                pathManager = serviceBuilder.requires(PathManagerService.SERVICE_NAME);
                serviceBuilder.requires(pathName(relativeTo));
            } else {
                pathManager = null;
            }

            return new ExceptionSupplier<CredentialStore, StartException>() {

                @Override
                public CredentialStore get() throws StartException {
                    try {
                        PathResolver pathResolver = pathResolver();
                        pathResolver.path(path);
                        if (relativeTo != null) {
                            pathResolver.relativeTo(relativeTo, pathManager.get());
                        }
                        File resolved = pathResolver.resolve();
                        pathResolver.clear();

                        return createCredentialStore(resolved);
                    } catch (GeneralSecurityException e) {
                        throw ROOT_LOGGER.unableToStartService(e);
                    }
                }
            };
        }

        @Override
        protected CredentialStore createImmediately(OperationContext foreignContext) throws OperationFailedException {
            try {
                return createCredentialStore(resolveRelativeToImmediately(path, relativeTo, foreignContext));
            } catch (GeneralSecurityException e) {
                throw ROOT_LOGGER.unableToCreateCredentialStoreImmediately(e);
            }
        }

        private CredentialStore createCredentialStore(final File resolved) throws GeneralSecurityException {
            CredentialStore credentialStore = CredentialStore.getInstance(CREDENTIAL_STORE_TYPE);

            final Map<String, String> configuration = new HashMap<>();
            configuration.put(ElytronCommonConstants.LOCATION, resolved.getAbsolutePath());
            if (create) {
                configuration.put(ElytronCommonConstants.CREATE, Boolean.TRUE.toString());
            }

            reloader = new ExceptionRunnable<GeneralSecurityException>() {

                @Override
                public void run() throws GeneralSecurityException {
                        credentialStore.initialize(configuration);
                }
            };
            reloader.run();

            if (populate && !credentialStore.getAliases().contains(defaultAlias)) {
                SecretKey secretKey = generateSecretKey(keySize);
                SecretKeyCredential credential = new SecretKeyCredential(secretKey);

                credentialStore.store(defaultAlias, credential);
                credentialStore.flush();
            }

            return credentialStore;
        }

        @Override
        protected void reload(OperationContext context) throws GeneralSecurityException, OperationFailedException {
            if (reloader != null) {
                reloader.run();
            } else {
                super.apply(context);
            }
        }

    }

}
