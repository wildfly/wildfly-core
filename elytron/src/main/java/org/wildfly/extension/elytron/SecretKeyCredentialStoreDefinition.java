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

import static org.wildfly.extension.elytron.Capabilities.CREDENTIAL_STORE_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;
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
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController.Mode;
import org.wildfly.extension.elytron.FileAttributeDefinitions.PathResolver;
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

    private static class SecretKeyCredentialStoreAddHandler extends BaseAddHandler {

        private SecretKeyCredentialStoreAddHandler() {
            super(CREDENTIAL_STORE_RUNTIME_CAPABILITY, CONFIG_ATTRIBUTES);
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
            TrivialService<CredentialStore> trivialService = new TrivialService<>(() -> {
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
            }, valueConsumer);

            commonDependencies(serviceBuilder.setInitialMode(Mode.ACTIVE).setInstance(trivialService)).install();
        }

    }


}
