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

package org.wildfly.extension.elytron;

import org.jboss.as.controller.DelegatingResourceDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
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
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

/**
 * A {@link DelegatingResourceDefinition} that decorates a {@link KeyStore} resource
 * with alias manipulation operations definitions.
 *
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
public class ModifiableKeyStoreDecorator extends DelegatingResourceDefinition {

    static ResourceDefinition wrap(ResourceDefinition resourceDefinition) {
        return new ModifiableKeyStoreDecorator(resourceDefinition);
    }

    private ModifiableKeyStoreDecorator(ResourceDefinition resourceDefinition) {
        setDelegate(resourceDefinition);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        ResourceDescriptionResolver resolver = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.MODIFIABLE_KEY_STORE);
        ReadAliasesHandler.register(resourceRegistration, resolver);
        ReadAliasHandler.register(resourceRegistration, resolver);
        RemoveAliasHandler.register(resourceRegistration, resolver);

        // Create Key Pair / Certificate
        // Create CSR
        // Import certificate
    }

    static class ReadAliasesHandler extends ElytronRuntimeOnlyHandler {

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(new SimpleOperationDefinition(ElytronDescriptionConstants.READ_ALIASES, descriptionResolver), new ReadAliasesHandler());
        }

        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            KeyStore keyStore = getKeyStore(context);

            try {
                ModelNode result = context.getResult();
                Enumeration<String> aliases = keyStore.aliases();
                while (aliases.hasMoreElements()) {
                    result.add(aliases.nextElement());
                }
            } catch (KeyStoreException e) {
                throw new OperationFailedException(e);
            }
        }
    }

    static class ReadAliasHandler extends ElytronRuntimeOnlyHandler {

        static final SimpleAttributeDefinition ALIAS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALIAS, ModelType.STRING, false)
                .setAllowExpression(false)
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(new SimpleOperationDefinition(ElytronDescriptionConstants.READ_ALIAS, descriptionResolver, ALIAS), new ReadAliasHandler());
        }

        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            String alias = ALIAS.resolveModelAttribute(context, operation).asString();
            KeyStore keyStore = getKeyStore(context);

            try {
                ModelNode result = context.getResult();
                if ( ! keyStore.containsAlias(alias)) {
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
                        writeCertificate(result.get(ElytronDescriptionConstants.CERTIFICATE), cert);
                    }
                } else {
                    writeCertificates(result.get(ElytronDescriptionConstants.CERTIFICATE_CHAIN), chain);
                }
            } catch (KeyStoreException | NoSuchAlgorithmException | CertificateEncodingException e) {
                throw new OperationFailedException(e);
            }
        }

        private String getEntryType(KeyStore keyStore, String alias) throws KeyStoreException {
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
    }

    static class RemoveAliasHandler extends ElytronRuntimeOnlyHandler {

        static final SimpleAttributeDefinition ALIAS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALIAS, ModelType.STRING, false)
                .setAllowExpression(false)
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(new SimpleOperationDefinition(ElytronDescriptionConstants.REMOVE_ALIAS, descriptionResolver, ALIAS), new RemoveAliasHandler());
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
        ServiceRegistry serviceRegistry = context.getServiceRegistry(true);
        PathAddress currentAddress = context.getCurrentAddress();
        RuntimeCapability<Void> runtimeCapability = KEY_STORE_RUNTIME_CAPABILITY.fromBaseCapability(currentAddress.getLastElement().getValue());
        ServiceName serviceName = runtimeCapability.getCapabilityServiceName();

        ServiceController<KeyStore> serviceContainer = getRequiredService(serviceRegistry, serviceName, KeyStore.class);
        ServiceController.State serviceState = serviceContainer.getState();
        if (serviceState != ServiceController.State.UP) {
            throw ROOT_LOGGER.requiredServiceNotUp(serviceName, serviceState);
        }

        ModifiableKeyStoreService service = (ModifiableKeyStoreService) serviceContainer.getService();
        return service.getModifiableValue();
    }
}
