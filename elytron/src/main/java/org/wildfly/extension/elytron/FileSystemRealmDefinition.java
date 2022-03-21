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
import static org.wildfly.extension.elytron.Capabilities.MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.BASE64;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.HEX;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.UTF_8;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathName;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathResolver;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.security.KeyStore;
import javax.crypto.SecretKey;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.operations.validation.CharsetValidator;
import org.jboss.as.controller.operations.validation.StringAllowedValuesValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.extension.elytron.FileAttributeDefinitions.PathResolver;
import org.wildfly.security.auth.realm.FileSystemSecurityRealm;
import org.wildfly.security.auth.realm.FileSystemSecurityRealmBuilder;
import org.wildfly.security.auth.server.NameRewriter;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.credential.SecretKeyCredential;
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

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{PATH, RELATIVE_TO, LEVELS, ENCODED, HASH_ENCODING, HASH_CHARSET, CREDENTIAL_STORE, SECRET_KEY};

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
        for (AttributeDefinition attr : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(attr, null, handler);
        }
    }

    private static class RealmAddHandler extends BaseAddHandler {

        private RealmAddHandler() {
            super(SECURITY_REALM_RUNTIME_CAPABILITY, ATTRIBUTES);
        }

        private SecretKey getKey(OperationContext context, String credentialStoreReference, String alias) throws OperationFailedException{
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

            SecretKey key = null;
            if(credentialStore != null && secretKey != null) {
                key = getKey(context, credentialStore, secretKey);
            }
            SecretKey finalKey = key;

            final InjectedValue<PathManager> pathManagerInjector = new InjectedValue<>();
            final InjectedValue<NameRewriter> nameRewriterInjector = new InjectedValue<>();

            TrivialService<SecurityRealm> fileSystemRealmService = new TrivialService<>(
                    new TrivialService.ValueSupplier<SecurityRealm>() {

                        private PathResolver pathResolver;

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

            if (relativeTo != null) {
                serviceBuilder.addDependency(PathManagerService.SERVICE_NAME, PathManager.class, pathManagerInjector);
                serviceBuilder.requires(pathName(relativeTo));
            }
            serviceBuilder.install();
        }

    }

}
