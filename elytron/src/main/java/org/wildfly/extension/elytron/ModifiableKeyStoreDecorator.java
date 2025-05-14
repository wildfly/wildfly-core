/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

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
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.wildfly.common.Assert;
import org.wildfly.security.keystore.PasswordEntry;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;

import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.CertificateChainAttributeDefinitions.writeCertificate;
import static org.wildfly.extension.elytron.CertificateChainAttributeDefinitions.writeCertificates;
import static org.wildfly.extension.elytron.ElytronExtension.ISO_8601_FORMAT;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
import static org.wildfly.extension.elytron.ElytronExtension.isServerOrHostController;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

/**
 * A {@link DelegatingResourceDefinition} that decorates a {@link KeyStore} resource
 * with alias manipulation operations definitions.
 *
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
class ModifiableKeyStoreDecorator extends DelegatingResourceDefinition {

    static ResourceDefinition wrap(ResourceDefinition resourceDefinition) {
        return new ModifiableKeyStoreDecorator(resourceDefinition);
    }

    ModifiableKeyStoreDecorator(ResourceDefinition resourceDefinition) {
        setDelegate(resourceDefinition);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        ResourceDescriptionResolver resolver = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.MODIFIABLE_KEY_STORE);
        ReadAliasesHandler.register(resourceRegistration, resolver);
        ReadAliasHandler.register(resourceRegistration, resolver);

        if (isServerOrHostController(resourceRegistration)) { // server-only operations
            RemoveAliasHandler.register(resourceRegistration, resolver);
        }
    }

    static class ReadAliasesHandler extends ElytronRuntimeOnlyHandler {

        static final SimpleAttributeDefinition RECURSIVE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.RECURSIVE, ModelType.BOOLEAN, true)
                .setAllowExpression(false)
                .setDefaultValue(ModelNode.FALSE)
                .build();

        static final SimpleAttributeDefinition VERBOSE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.VERBOSE, ModelType.BOOLEAN, true)
                .setAllowExpression(false)
                .setDefaultValue(ModelNode.TRUE)
                .setRequires(RECURSIVE.getName())
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            SimpleOperationDefinition READ_ALIASES = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.READ_ALIASES, descriptionResolver)
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
                        ModelNode expirationWatermarkModelAttribute = KeyStoreDefinition.EXPIRATION_WATERMARK.resolveModelAttribute(context, context.readResource(PathAddress.EMPTY_ADDRESS).getModel());
                        ReadAliasHandler.readAlias(keyStore, alias, verbose, aliasNode, context.getStability(), expirationWatermarkModelAttribute.asLong());
                    } else {
                        result.add(alias);
                    }
                }
            } catch (KeyStoreException | NoSuchAlgorithmException | CertificateEncodingException e) {
                throw new OperationFailedException(e);
            }
        }
    }

    static class ReadAliasHandler extends ElytronRuntimeOnlyHandler {

        static final SimpleAttributeDefinition ALIAS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALIAS, ModelType.STRING, false)
                .setAllowExpression(false)
                .build();

        static final SimpleAttributeDefinition VERBOSE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.VERBOSE, ModelType.BOOLEAN, true)
                .setAllowExpression(false)
                .setDefaultValue(ModelNode.TRUE)
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            SimpleOperationDefinition READ_ALIAS = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.READ_ALIAS, descriptionResolver)
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
                ModelNode expirationWatermarkModelAttribute = KeyStoreDefinition.EXPIRATION_WATERMARK.resolveModelAttribute(context, context.readResource(PathAddress.EMPTY_ADDRESS).getModel());
                readAlias(keyStore, alias, verbose, result, context.getStability(), expirationWatermarkModelAttribute.asLong());
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
                final ModelNode result, final Stability stability, long expirationWarningWaterMark) throws KeyStoreException, NoSuchAlgorithmException, CertificateEncodingException {
            if (!keyStore.containsAlias(alias)) {
                ROOT_LOGGER.tracef("Alias [%s] does not exists in KeyStore");
                return;
            }

            result.get(ElytronDescriptionConstants.ALIAS).set(alias);
            result.get(ElytronDescriptionConstants.ENTRY_TYPE).set(getEntryType(keyStore, alias));

            Date creationDate = keyStore.getCreationDate(alias);
            if (creationDate != null) {
                SimpleDateFormat sdf = new SimpleDateFormat(ISO_8601_FORMAT);
                result.get(ElytronDescriptionConstants.CREATION_DATE).set(sdf.format(creationDate));
            }

            Certificate[] chain = keyStore.getCertificateChain(alias);
            if (chain == null) {
                Certificate cert = keyStore.getCertificate(alias);
                if (cert != null) {
                    writeCertificate(result.get(ElytronDescriptionConstants.CERTIFICATE), cert, verbose, stability, expirationWarningWaterMark);
                }
            } else {
                writeCertificates(result.get(ElytronDescriptionConstants.CERTIFICATE_CHAIN), chain, verbose, stability, expirationWarningWaterMark);
            }
        }
    }

    static class RemoveAliasHandler extends ElytronRuntimeOnlyHandler {

        static final SimpleAttributeDefinition ALIAS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALIAS, ModelType.STRING, false)
                .setAllowExpression(false)
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.REMOVE_ALIAS, descriptionResolver)
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
    static KeyStore getKeyStore(OperationContext context) throws OperationFailedException {
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
    static KeyStore getModifiableKeyStore(OperationContext context) throws OperationFailedException {
        return getModifiableKeyStoreService(context).getModifiableValue();
    }

    /**
     * Try to obtain a modifiable {@link KeyStoreService} based on the given {@link OperationContext}.
     *
     * @param context the current context
     * @return the modifiable KeyStore service
     * @throws OperationFailedException if an error occurs while attempting to obtain the modifiable KeyStore service
     */
    static ModifiableKeyStoreService getModifiableKeyStoreService(OperationContext context) throws OperationFailedException {
        ServiceRegistry serviceRegistry = context.getServiceRegistry(true);
        PathAddress currentAddress = context.getCurrentAddress();
        RuntimeCapability<Void> runtimeCapability = KEY_STORE_RUNTIME_CAPABILITY.fromBaseCapability(currentAddress.getLastElement().getValue());
        ServiceName serviceName = runtimeCapability.getCapabilityServiceName();

        ServiceController<KeyStore> serviceContainer = getRequiredService(serviceRegistry, serviceName, KeyStore.class);
        ServiceController.State serviceState = serviceContainer.getState();
        if (serviceState != ServiceController.State.UP) {
            throw ROOT_LOGGER.requiredServiceNotUp(serviceName, serviceState);
        }

        return (ModifiableKeyStoreService) serviceContainer.getService();
    }
}
