/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

import org.jboss.as.controller.DelegatingResourceDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.wildfly.common.Assert;
import org.wildfly.extension.elytron.common.util.ElytronCommonMessages;
import org.wildfly.security.keystore.PasswordEntry;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;

import static org.wildfly.extension.elytron.common.ElytronCommonCapabilities.KEY_STORE_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.common.CertificateChainAttributeDefinitions.writeCertificate;
import static org.wildfly.extension.elytron.common.CertificateChainAttributeDefinitions.writeCertificates;
import static org.wildfly.extension.elytron.common.ElytronCommonDefinitions.ISO_8601_FORMAT;
import static org.wildfly.extension.elytron.common.ElytronCommonDefinitions.getRequiredService;
import static org.wildfly.extension.elytron.common.ElytronCommonDefinitions.isServerOrHostController;

/**
 * A {@link DelegatingResourceDefinition} that decorates a {@link KeyStore} resource
 * with alias manipulation operations definitions.
 *
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
public class ModifiableKeyStoreDecorator extends DelegatingResourceDefinition {

    private final Class<?> extensionClass;

    protected final Class<?> getExtensionClass() {
        return extensionClass;
    }

    public static ResourceDefinition wrap(final Class<?> extensionClass, ResourceDefinition resourceDefinition) {
        return new ModifiableKeyStoreDecorator(extensionClass, resourceDefinition);
    }

    protected ModifiableKeyStoreDecorator(final Class<?> extensionClass, ResourceDefinition resourceDefinition) {
        setDelegate(resourceDefinition);
        this.extensionClass = extensionClass;
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        ResourceDescriptionResolver resolver = ElytronCommonDefinitions.getResourceDescriptionResolver(extensionClass, ElytronCommonConstants.MODIFIABLE_KEY_STORE);
        ReadAliasesHandler.register(resourceRegistration, resolver);
        ReadAliasHandler.register(resourceRegistration, resolver);

        if (isServerOrHostController(resourceRegistration)) { // server-only operations
            RemoveAliasHandler.register(resourceRegistration, resolver);
        }
    }

    public static class ReadAliasesHandler extends ElytronRuntimeOnlyHandler {

        public static final SimpleAttributeDefinition RECURSIVE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.RECURSIVE, ModelType.BOOLEAN, true)
                .setAllowExpression(false)
                .setDefaultValue(ModelNode.FALSE)
                .build();

        public static final SimpleAttributeDefinition VERBOSE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.VERBOSE, ModelType.BOOLEAN, true)
                .setAllowExpression(false)
                .setDefaultValue(ModelNode.TRUE)
                .setRequires(RECURSIVE.getName())
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            SimpleOperationDefinition READ_ALIASES = new SimpleOperationDefinitionBuilder(ElytronCommonConstants.READ_ALIASES, descriptionResolver)
                    .setParameters(RECURSIVE,VERBOSE)
                    .setReadOnly()
                    .setRuntimeOnly()
                    .build();
            resourceRegistration.registerOperationHandler(READ_ALIASES, new ReadAliasesHandler());
        }

        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            final KeyStore keyStore = getKeyStore(context);

            try {
                final ModelNode result = context.getResult();
                final Enumeration<String> aliases = keyStore.aliases();
                final boolean verbose = VERBOSE.resolveModelAttribute(context, operation).asBoolean();
                final boolean recursive = RECURSIVE.resolveModelAttribute(context, operation).asBoolean();
                ModelNode aliasNode = null;
                String alias = null;
                while (aliases.hasMoreElements()) {
                    alias = aliases.nextElement();
                    if(recursive) {
                        aliasNode = result.get(alias);
                        ReadAliasHandler.readAlias(keyStore, alias, verbose, aliasNode);
                    } else {
                        result.add(alias);
                    }
                }
            } catch (KeyStoreException | NoSuchAlgorithmException | CertificateEncodingException e) {
                throw new OperationFailedException(e);
            }
        }
    }

    public static class ReadAliasHandler extends ElytronRuntimeOnlyHandler {

        public static final SimpleAttributeDefinition ALIAS = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.ALIAS, ModelType.STRING, false)
                .setAllowExpression(false)
                .build();

        public static final SimpleAttributeDefinition VERBOSE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.VERBOSE, ModelType.BOOLEAN, true)
                .setAllowExpression(false)
                .setDefaultValue(ModelNode.TRUE)
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            SimpleOperationDefinition READ_ALIAS = new SimpleOperationDefinitionBuilder(ElytronCommonConstants.READ_ALIAS, descriptionResolver)
                    .setParameters(ALIAS, VERBOSE)
                    .setReadOnly()
                    .setRuntimeOnly()
                    .build();
            resourceRegistration.registerOperationHandler(READ_ALIAS, new ReadAliasHandler());
        }

        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            final String alias = ALIAS.resolveModelAttribute(context, operation).asString();
            final boolean verbose = VERBOSE.resolveModelAttribute(context, operation).asBoolean();
            KeyStore keyStore = getKeyStore(context);

            try {
                ModelNode result = context.getResult();
                readAlias(keyStore,alias,verbose,result);
            } catch (KeyStoreException | NoSuchAlgorithmException | CertificateEncodingException e) {
                throw new OperationFailedException(e);
            }
        }

        private static String getEntryType(KeyStore keyStore, String alias) throws KeyStoreException {
            if (keyStore.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class)) {
                return KeyStore.PrivateKeyEntry.class.getSimpleName();
            } else if (keyStore.entryInstanceOf(alias, KeyStore.SecretKeyEntry.class)) {
                return KeyStore.SecretKeyEntry.class.getSimpleName();
            } else if (keyStore.entryInstanceOf(alias, KeyStore.TrustedCertificateEntry.class)) {
                return KeyStore.TrustedCertificateEntry.class.getSimpleName();
            } else if (keyStore.entryInstanceOf(alias, PasswordEntry.class)) {
                return PasswordEntry.class.getSimpleName();
            } else {
                return "Other";
            }
        }

        private static void readAlias(final KeyStore keyStore, final String alias, final boolean verbose,
                final ModelNode result) throws KeyStoreException, NoSuchAlgorithmException, CertificateEncodingException {
            if (!keyStore.containsAlias(alias)) {
                ElytronCommonMessages.ROOT_LOGGER.tracef("Alias [%s] does not exists in KeyStore");
                return;
            }

            result.get(ElytronCommonConstants.ALIAS).set(alias);
            result.get(ElytronCommonConstants.ENTRY_TYPE).set(getEntryType(keyStore, alias));

            Date creationDate = keyStore.getCreationDate(alias);
            if (creationDate != null) {
                SimpleDateFormat sdf = new SimpleDateFormat(ISO_8601_FORMAT);
                result.get(ElytronCommonConstants.CREATION_DATE).set(sdf.format(creationDate));
            }

            Certificate[] chain = keyStore.getCertificateChain(alias);
            if (chain == null) {
                Certificate cert = keyStore.getCertificate(alias);
                if (cert != null) {
                    writeCertificate(result.get(ElytronCommonConstants.CERTIFICATE), cert, verbose);
                }
            } else {
                writeCertificates(result.get(ElytronCommonConstants.CERTIFICATE_CHAIN), chain, verbose);
            }
        }
    }

    protected static class RemoveAliasHandler extends ElytronRuntimeOnlyHandler {

        protected static final SimpleAttributeDefinition ALIAS = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.ALIAS, ModelType.STRING, false)
                .setAllowExpression(false)
                .build();

        protected static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(new SimpleOperationDefinitionBuilder(ElytronCommonConstants.REMOVE_ALIAS, descriptionResolver)
                        .setParameters(ALIAS)
                        .setRuntimeOnly()
                        .build()
                    , new RemoveAliasHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            String alias = ALIAS.resolveModelAttribute(context, operation).asString();
            KeyStore keyStore = getModifiableKeyStore(context);

            try {
                keyStore.deleteEntry(alias);
            } catch (KeyStoreException e) {
                throw new OperationFailedException(e);
            }
        }
    }

    /**
     * Try to obtain a {@link KeyStore} based on the given {@link OperationContext}.
     *
     * @param context the current context
     * @return the unmodifiable KeyStore
     * @throws OperationFailedException if any error occurs while obtaining.
     */
    public static KeyStore getKeyStore(OperationContext context) throws OperationFailedException {
        ServiceRegistry serviceRegistry = context.getServiceRegistry(true);
        PathAddress currentAddress = context.getCurrentAddress();
        RuntimeCapability<Void> runtimeCapability = KEY_STORE_RUNTIME_CAPABILITY.fromBaseCapability(currentAddress.getLastElement().getValue());
        ServiceName serviceName = runtimeCapability.getCapabilityServiceName();
        ServiceController<KeyStore> serviceController = getRequiredService(serviceRegistry, serviceName, KeyStore.class);

        return Assert.assertNotNull(serviceController.getValue());
    }

    /**
     * Try to obtain a modifiable {@link KeyStore} based on the given {@link OperationContext}.
     *
     * @param context the current context
     * @return the modifiable KeyStore
     * @throws OperationFailedException if any error occurs while obtaining.
     */
    public static KeyStore getModifiableKeyStore(OperationContext context) throws OperationFailedException {
        return getModifiableKeyStoreService(context).getModifiableValue();
    }

    /**
     * Try to obtain a modifiable {@link KeyStoreService} based on the given {@link OperationContext}.
     *
     * @param context the current context
     * @return the modifiable KeyStore service
     * @throws OperationFailedException if an error occurs while attempting to obtain the modifiable KeyStore service
     */
    public static ModifiableKeyStoreService getModifiableKeyStoreService(OperationContext context) throws OperationFailedException {
        ServiceRegistry serviceRegistry = context.getServiceRegistry(true);
        PathAddress currentAddress = context.getCurrentAddress();
        RuntimeCapability<Void> runtimeCapability = KEY_STORE_RUNTIME_CAPABILITY.fromBaseCapability(currentAddress.getLastElement().getValue());
        ServiceName serviceName = runtimeCapability.getCapabilityServiceName();

        ServiceController<KeyStore> serviceContainer = getRequiredService(serviceRegistry, serviceName, KeyStore.class);
        ServiceController.State serviceState = serviceContainer.getState();
        if (serviceState != ServiceController.State.UP) {
            throw ElytronCommonMessages.ROOT_LOGGER.requiredServiceNotUp(serviceName, serviceState);
        }

        return (ModifiableKeyStoreService) serviceContainer.getService();
    }
}
