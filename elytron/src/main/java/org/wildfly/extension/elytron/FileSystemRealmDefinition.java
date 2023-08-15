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

package org.wildfly.extension.elytron;

import static org.jboss.as.controller.capability.RuntimeCapability.buildDynamicCapabilityName;
import static org.wildfly.extension.elytron.Capabilities.CREDENTIAL_STORE_API_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.CREDENTIAL_STORE_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.BASE64;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.HEX;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.UTF_8;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
import static org.wildfly.extension.elytron.ElytronExtension.isServerOrHostController;
import static org.wildfly.extension.elytron.common.FileAttributeDefinitions.pathName;
import static org.wildfly.extension.elytron.common.FileAttributeDefinitions.pathResolver;
import static org.wildfly.extension.elytron.KeyStoreServiceUtil.getModifiableKeyStoreService;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.util.stream.Stream;

import javax.crypto.SecretKey;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.CharsetValidator;
import org.jboss.as.controller.operations.validation.StringAllowedValuesValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.extension.elytron.common.ElytronReloadRequiredWriteAttributeHandler;
import org.wildfly.extension.elytron.common.ElytronRuntimeOnlyHandler;
import org.wildfly.extension.elytron.common.FileAttributeDefinitions;
import org.wildfly.extension.elytron.common.FileAttributeDefinitions.PathResolver;
import org.wildfly.extension.elytron.common.KeyStoreService;
import org.wildfly.extension.elytron.common.ModifiableKeyStoreService;
import org.wildfly.extension.elytron.common.TrivialService;
import org.wildfly.security.auth.realm.FileSystemSecurityRealm;
import org.wildfly.security.auth.realm.FileSystemSecurityRealmBuilder;
import org.wildfly.security.auth.server.NameRewriter;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.credential.SecretKeyCredential;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.CredentialStoreException;
import org.wildfly.security.password.spec.Encoding;


/**
 * A {@link ResourceDefinition} for a {@link SecurityRealm} backed by a {@link KeyStore}.
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
class FileSystemRealmDefinition extends SimpleResourceDefinition {

    static final SimpleAttributeDefinition PATH =
            new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PATH, FileAttributeDefinitions.PATH)
                    .setAttributeGroup(ElytronDescriptionConstants.FILE)
                    .setRequired(true)
                    .setRestartAllServices()
                    .build();

    static final SimpleAttributeDefinition RELATIVE_TO =
            new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.RELATIVE_TO, FileAttributeDefinitions.RELATIVE_TO)
                    .setAttributeGroup(ElytronDescriptionConstants.FILE)
                    .setRestartAllServices()
                    .build();

    static final SimpleAttributeDefinition LEVELS =
            new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.LEVELS, ModelType.INT, true)
                    .setDefaultValue(new ModelNode(2))
                    .setAllowExpression(true)
                    .setRestartAllServices()
                    .build();

    static final SimpleAttributeDefinition ENCODED =
            new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ENCODED, ModelType.BOOLEAN, true)
                    .setDefaultValue(ModelNode.TRUE)
                    .setAllowExpression(true)
                    .setRestartAllServices()
                    .build();

    static final SimpleAttributeDefinition HASH_ENCODING = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.HASH_ENCODING, ModelType.STRING, true)
            .setDefaultValue(new ModelNode(BASE64))
            .setValidator(new StringAllowedValuesValidator(BASE64, HEX))
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition HASH_CHARSET = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.HASH_CHARSET, ModelType.STRING, true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setDefaultValue(new ModelNode(UTF_8))
            .setValidator(new CharsetValidator())
            .setAllowExpression(true)
            .build();

    static final SimpleAttributeDefinition CREDENTIAL_STORE =
            new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CREDENTIAL_STORE, ModelType.STRING, false)
                    .setAllowExpression(true)
                    .setRequired(false)
                    .setRequires(ElytronDescriptionConstants.SECRET_KEY)
                    .setMinSize(1)
                    .setRestartAllServices()
                    .setCapabilityReference(CREDENTIAL_STORE_CAPABILITY, SECURITY_REALM_CAPABILITY)
                    .build();

    static final SimpleAttributeDefinition SECRET_KEY =
            new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SECRET_KEY, ModelType.STRING, false)
                    .setAllowExpression(true)
                    .setRequired(false)
                    .setRequires(ElytronDescriptionConstants.CREDENTIAL_STORE)
                    .setMinSize(1)
                    .setRestartAllServices()
                    .build();

    static final SimpleAttributeDefinition KEY_STORE =
            new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEY_STORE, ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setRequires(ElytronDescriptionConstants.KEY_STORE_ALIAS)
                    .setMinSize(1)
                    .setRestartAllServices()
                    .setCapabilityReference(KEY_STORE_CAPABILITY, SECURITY_REALM_RUNTIME_CAPABILITY)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

    static final SimpleAttributeDefinition KEY_STORE_ALIAS =
            new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEY_STORE_ALIAS, ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setRequires(ElytronDescriptionConstants.KEY_STORE)
                    .setMinSize(1)
                    .setRestartAllServices()
                    .build();

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{PATH, RELATIVE_TO, LEVELS, ENCODED, HASH_ENCODING, HASH_CHARSET};
    static final AttributeDefinition[] INTEGRITY_ATTRIBUTES = new AttributeDefinition[]{KEY_STORE, KEY_STORE_ALIAS};
    static final AttributeDefinition[] ENCRYPTION_ATTRIBUTES = new AttributeDefinition[]{CREDENTIAL_STORE, SECRET_KEY};
    static final AttributeDefinition[] ALL_ATTRIBUTES = Stream.of(ATTRIBUTES, INTEGRITY_ATTRIBUTES, ENCRYPTION_ATTRIBUTES)
            .flatMap(Stream::of).toArray(AttributeDefinition[]::new);

    private static final AbstractAddStepHandler ADD = new RealmAddHandler();
    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY, SECURITY_REALM_RUNTIME_CAPABILITY);

    FileSystemRealmDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.FILESYSTEM_REALM),
                ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.FILESYSTEM_REALM))
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY, SECURITY_REALM_RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        AbstractWriteAttributeHandler handler = new ElytronReloadRequiredWriteAttributeHandler(ATTRIBUTES);
        AbstractWriteAttributeHandler integrityHandler = new IntegrityWriteAttributeDisabledHandler(INTEGRITY_ATTRIBUTES);
        AbstractWriteAttributeHandler encryptionHandler = new EncryptionWriteAttributeDisabledHandler(ENCRYPTION_ATTRIBUTES);

        for (AttributeDefinition attr : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(attr, null, handler);
        }
        for (AttributeDefinition attr : INTEGRITY_ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(attr, null, integrityHandler);
        }
        for (AttributeDefinition attr : ENCRYPTION_ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(attr, null, encryptionHandler);
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        ResourceDescriptionResolver resolver = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.FILESYSTEM_REALM);
        if (isServerOrHostController(resourceRegistration)) { // server-only operations
            UpdateKeyPairHandler.register(resourceRegistration, resolver);
            VerifyRealmIntegrity.register(resourceRegistration, resolver);
        }
    }

    static class UpdateKeyPairHandler extends ElytronRuntimeOnlyHandler {

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.UPDATE_KEY_PAIR, descriptionResolver)
                            .setRuntimeOnly()
                            .build(),
                    new FileSystemRealmDefinition.UpdateKeyPairHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            TrivialService<FileSystemSecurityRealm> filesystemService = (TrivialService<FileSystemSecurityRealm>) getFileSystemService(context);
            FileSystemSecurityRealm fileSystemRealm = filesystemService.getValue();
            try {
                if (! fileSystemRealm.hasIntegrityEnabled()) {
                    throw ROOT_LOGGER.filesystemMissingKeypair();
                }
                fileSystemRealm.updateRealmKeyPair();
            } catch (IOException e) {
                throw ROOT_LOGGER.unableToVerifyIntegrity(e, e.getLocalizedMessage());
            }
        }
    }

    static class VerifyRealmIntegrity extends ElytronRuntimeOnlyHandler {

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.VERIFY_INTEGRITY, descriptionResolver)
                            .setRuntimeOnly()
                            .build(),
                    new FileSystemRealmDefinition.VerifyRealmIntegrity());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            TrivialService<FileSystemSecurityRealm> filesystemService = (TrivialService<FileSystemSecurityRealm>) getFileSystemService(context);
            FileSystemSecurityRealm fileSystemRealm = filesystemService.getValue();
            try {
                if (! fileSystemRealm.hasIntegrityEnabled()) {
                    throw ROOT_LOGGER.filesystemMissingKeypair();
                }
                FileSystemSecurityRealm.IntegrityResult result = fileSystemRealm.verifyRealmIntegrity();
                if(!result.isValid()) {
                    throw ROOT_LOGGER.filesystemIntegrityInvalid(result.getIdentityNames());
                }
            } catch (IOException e) {
                throw ROOT_LOGGER.unableToVerifyIntegrity(e, e.getLocalizedMessage());
            }
        }
    }

    private static class RealmAddHandler extends BaseAddHandler {

        private RealmAddHandler() {
            super(SECURITY_REALM_RUNTIME_CAPABILITY, ALL_ATTRIBUTES);
        }

        private static SecretKey getSecretKey(OperationContext context, String credentialStoreReference, String alias) throws OperationFailedException {
            ExceptionFunction<OperationContext, CredentialStore, OperationFailedException> credentialStoreApi = context.getCapabilityRuntimeAPI(CREDENTIAL_STORE_API_CAPABILITY, credentialStoreReference, ExceptionFunction.class);
            CredentialStore credentialStoreResource = credentialStoreApi.apply(context);
            try {
                SecretKeyCredential credential = credentialStoreResource.retrieve(alias, SecretKeyCredential.class);
                if (credential == null) {
                    throw ROOT_LOGGER.credentialDoesNotExist(alias, SecretKeyCredential.class.getSimpleName());
                }
                return credential.getSecretKey();
            } catch (CredentialStoreException e) {
                throw ROOT_LOGGER.unableToLoadCredentialStore(e);
            }
        }
        private static char[] getKeyStorePassword(KeyStoreService keyStoreService) throws RuntimeException {
            InjectedValue<ExceptionSupplier<CredentialSource, Exception>> credentialSourceSupplierInjector = new InjectedValue<>();
            try {
                return keyStoreService.resolveKeyPassword(credentialSourceSupplierInjector.getOptionalValue());
            } catch (Exception e) {
                throw ROOT_LOGGER.unableToGetKeyStorePassword();
            }
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            ServiceTarget serviceTarget = context.getServiceTarget();

            String address = context.getCurrentAddressValue();
            ServiceName mainServiceName = MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(address).getCapabilityServiceName();
            ServiceName aliasServiceName = SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(address).getCapabilityServiceName();

            final int levels = LEVELS.resolveModelAttribute(context, model).asInt();

            final boolean encoded = ENCODED.resolveModelAttribute(context, model).asBoolean();

            final String path = PATH.resolveModelAttribute(context, model).asString();
            final String relativeTo = RELATIVE_TO.resolveModelAttribute(context, model).asStringOrNull();

            final String hashEncoding = HASH_ENCODING.resolveModelAttribute(context, model).asString();
            final String hashCharset = HASH_CHARSET.resolveModelAttribute(context, model).asString();
            final String credentialStore = CREDENTIAL_STORE.resolveModelAttribute(context, model).asStringOrNull();
            final String secretKey = SECRET_KEY.resolveModelAttribute(context, model).asStringOrNull();
            final String keyStoreName = KEY_STORE.resolveModelAttribute(context, model).asStringOrNull();
            final String keyPairAlias = KEY_STORE_ALIAS.resolveModelAttribute(context, model).asStringOrNull();

            final InjectedValue<KeyStore> keyStoreInjector = new InjectedValue<>();
            final InjectedValue<PathManager> pathManagerInjector = new InjectedValue<>();
            final InjectedValue<NameRewriter> nameRewriterInjector = new InjectedValue<>();

            SecretKey key = null;
            if (credentialStore != null && secretKey != null) {
                key = getSecretKey(context, credentialStore, secretKey);
            }
            final SecretKey finalKey = key;
            ServiceRegistry keyStoreServiceRegistry = context.getServiceRegistry(true);

            TrivialService<SecurityRealm> fileSystemRealmService = new TrivialService<>(
                    new TrivialService.ValueSupplier<SecurityRealm>() {

                        private PathResolver pathResolver;
                        ModifiableKeyStoreService keyStoreService;

                        @Override
                        public SecurityRealm get() throws StartException {
                            pathResolver = pathResolver();
                            Path rootPath = pathResolver.path(path).relativeTo(relativeTo, pathManagerInjector.getOptionalValue()).resolve().toPath();

                            NameRewriter nameRewriter = nameRewriterInjector.getOptionalValue();
                            Charset charset = Charset.forName(hashCharset);
                            Encoding encoding = HEX.equals(hashEncoding) ? Encoding.HEX : Encoding.BASE64;
                            if (nameRewriter == null) {
                                nameRewriter = NameRewriter.IDENTITY_REWRITER;
                            }
                            KeyStore keyStore = keyStoreInjector.getOptionalValue();
                            PrivateKey privateKey = null;
                            PublicKey publicKey = null;
                            if (keyStore != null) {
                                    try {
                                        keyStoreService = getModifiableKeyStoreService(keyStoreServiceRegistry, keyStoreName);
                                        char[] keyPassword = getKeyStorePassword((KeyStoreService) keyStoreService);
                                        if(! keyStore.containsAlias(keyPairAlias)) {
                                            throw ROOT_LOGGER.keyStoreMissingAlias(keyPairAlias);
                                        }
                                        privateKey = (PrivateKey) keyStore.getKey(keyPairAlias, keyPassword);
                                        publicKey = keyStore.getCertificate(keyPairAlias).getPublicKey();
                                        if (privateKey == null) {
                                            throw ROOT_LOGGER.missingPrivateKey(keyStoreName, keyPairAlias);
                                        } else if (publicKey == null) {
                                           throw ROOT_LOGGER.missingPublicKey(keyStoreName, keyPairAlias);
                                        }
                                    } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException | OperationFailedException e) {
                                        throw ROOT_LOGGER.unableToAccessEntryFromKeyStore(keyPairAlias, keyStoreName);
                                    }
                            }

                            FileSystemSecurityRealmBuilder fileSystemRealmBuilder = FileSystemSecurityRealm.builder()
                                    .setRoot(rootPath)
                                    .setNameRewriter(nameRewriter)
                                    .setLevels(levels)
                                    .setEncoded(encoded)
                                    .setHashEncoding(encoding)
                                    .setHashCharset(charset);

                            if (finalKey != null) {
                                fileSystemRealmBuilder.setSecretKey(finalKey);
                            }

                            if (privateKey != null && publicKey != null) {
                                fileSystemRealmBuilder.setPrivateKey(privateKey);
                                fileSystemRealmBuilder.setPublicKey(publicKey);
                            }
                            return fileSystemRealmBuilder.build();

                        }

                        @Override
                        public void dispose() {
                            if (pathResolver != null) {
                                pathResolver.clear();
                                pathResolver = null;
                            }
                        }

                    });

            ServiceBuilder<SecurityRealm> serviceBuilder = serviceTarget.addService(mainServiceName, fileSystemRealmService)
                    .addAliases(aliasServiceName);
            if (credentialStore != null) {
                serviceBuilder.requires(context.getCapabilityServiceName(buildDynamicCapabilityName(CREDENTIAL_STORE_CAPABILITY, credentialStore), CredentialStore.class));
            }
            if (keyStoreName != null) {
                serviceBuilder.addDependency(context.getCapabilityServiceName(
                        buildDynamicCapabilityName(KEY_STORE_CAPABILITY, keyStoreName), KeyStore.class),
                        KeyStore.class, keyStoreInjector);
            }
            if (relativeTo != null) {
                serviceBuilder.addDependency(PathManagerService.SERVICE_NAME, PathManager.class, pathManagerInjector);
                serviceBuilder.requires(pathName(relativeTo));
            }
            serviceBuilder.install();
        }

    }

    private static Service getFileSystemService(OperationContext context) throws OperationFailedException {
        ServiceRegistry serviceRegistry = context.getServiceRegistry(true);
        PathAddress currentAddress = context.getCurrentAddress();
        ServiceName mainServiceName = MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(currentAddress.getLastElement().getValue()).getCapabilityServiceName();

        ServiceController<SecurityRealm> serviceContainer = getRequiredService(serviceRegistry, mainServiceName, SecurityRealm.class);
        ServiceController.State serviceState = serviceContainer.getState();
       if (serviceState != ServiceController.State.UP) {
           throw ROOT_LOGGER.requiredServiceNotUp(mainServiceName, serviceState);
       }
        return serviceContainer.getService();
    }

    /**
     * Integrity keypair can only be added at initialization, unless realm contains no identities. Existing realms should
     * be upgraded with Elytron Tool.
     *
     * @see <a href="https://issues.redhat.com/browse/WFCORE-6129">WFCORE-6129</a>
     */
    private static class IntegrityWriteAttributeDisabledHandler extends ReloadRequiredWriteAttributeHandler {
        public IntegrityWriteAttributeDisabledHandler(final AttributeDefinition... definitions) {
            super(definitions);
        }

        @Override
        protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                                               ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> voidHandback) throws OperationFailedException {

            TrivialService<FileSystemSecurityRealm> filesystemService = (TrivialService<FileSystemSecurityRealm>) getFileSystemService(context);
            FileSystemSecurityRealm fileSystemRealm = filesystemService.getValue();

            try {
                if (!currentValue.isDefined() && fileSystemRealm.getRealmIdentityIterator().hasNext()) {
                    throw ROOT_LOGGER.addKeypairToInitializedFilesystemRealm();
                }
            } catch (RealmUnavailableException ignored) {
                throw ROOT_LOGGER.addKeypairToInitializedFilesystemRealm();
            }
            return super.applyUpdateToRuntime(context, operation, attributeName, resolvedValue, currentValue, voidHandback);
        }
    }

    /**
     * Encryption secret key can only be added at initialization, unless realm contains no identities. Existing realms should
     * be upgraded with Elytron Tool.
     *
     * @see <a href="https://issues.redhat.com/browse/WFCORE-6129">WFCORE-6129</a>
     */
    private static class EncryptionWriteAttributeDisabledHandler extends ReloadRequiredWriteAttributeHandler {

        public EncryptionWriteAttributeDisabledHandler(final AttributeDefinition... definitions) {
            super(definitions);
        }

        @Override
        protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                                               ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> voidHandback) throws OperationFailedException {
            TrivialService<FileSystemSecurityRealm> filesystemService = (TrivialService<FileSystemSecurityRealm>) getFileSystemService(context);
            FileSystemSecurityRealm fileSystemRealm = filesystemService.getValue();

            try {
                if (!currentValue.isDefined() && fileSystemRealm.getRealmIdentityIterator().hasNext()) {
                    throw ROOT_LOGGER.addSecretKeyToInitializedFilesystemRealm();
                }
            } catch (RealmUnavailableException ignored) {
                throw ROOT_LOGGER.addSecretKeyToInitializedFilesystemRealm();
            }
            return super.applyUpdateToRuntime(context, operation, attributeName, resolvedValue, currentValue, voidHandback);
        }
    }

}
