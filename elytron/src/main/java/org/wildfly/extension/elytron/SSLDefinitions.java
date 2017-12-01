/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
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
import static org.wildfly.extension.elytron.Capabilities.KEY_MANAGER_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_MANAGER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PRINCIPAL_TRANSFORMER_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PROVIDERS_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.REALM_MAPPER_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_DOMAIN_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SSL_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SSL_CONTEXT_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.TRUST_MANAGER_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.TRUST_MANAGER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronExtension.asIntIfDefined;
import static org.wildfly.extension.elytron.ElytronExtension.asStringIfDefined;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
import static org.wildfly.extension.elytron.ElytronExtension.isServerOrHostController;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.PATH;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.RELATIVE_TO;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathName;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.validation.AllowedValuesValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;
import org.wildfly.extension.elytron._private.ElytronSubsystemMessages;
import org.wildfly.extension.elytron.capabilities.PrincipalTransformer;
import org.wildfly.security.auth.server.MechanismConfiguration;
import org.wildfly.security.auth.server.MechanismConfigurationSelector;
import org.wildfly.security.auth.server.RealmMapper;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.keystore.AliasFilter;
import org.wildfly.security.keystore.FilteringKeyStore;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.ssl.CipherSuiteSelector;
import org.wildfly.security.ssl.Protocol;
import org.wildfly.security.ssl.ProtocolSelector;
import org.wildfly.security.ssl.SSLContextBuilder;
import org.wildfly.security.ssl.X509CRLExtendedTrustManager;

/**
 * Definitions for resources used to configure SSLContexts.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class SSLDefinitions {

    private static final BooleanSupplier IS_FIPS = getFipsSupplier();

    static final ServiceUtil<SSLContext> SERVER_SERVICE_UTIL = ServiceUtil.newInstance(SSL_CONTEXT_RUNTIME_CAPABILITY, ElytronDescriptionConstants.SERVER_SSL_CONTEXT, SSLContext.class);
    static final ServiceUtil<SSLContext> CLIENT_SERVICE_UTIL = ServiceUtil.newInstance(SSL_CONTEXT_RUNTIME_CAPABILITY, ElytronDescriptionConstants.CLIENT_SSL_CONTEXT, SSLContext.class);

    static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition PROVIDER_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PROVIDER_NAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition PROVIDERS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PROVIDERS, ModelType.STRING, true)
            .setAllowExpression(false)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition KEYSTORE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEY_STORE, ModelType.STRING, false)
            .setAllowExpression(true)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition ALIAS_FILTER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALIAS_FILTER, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition SECURITY_DOMAIN = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SECURITY_DOMAIN, ModelType.STRING, true)
            .setMinSize(1)
            .setCapabilityReference(SECURITY_DOMAIN_CAPABILITY, SSL_CONTEXT_CAPABILITY, true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition PRE_REALM_PRINCIPAL_TRANSFORMER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PRE_REALM_PRINCIPAL_TRANSFORMER, ModelType.STRING, true)
            .setMinSize(1)
            .setCapabilityReference(PRINCIPAL_TRANSFORMER_CAPABILITY, SSL_CONTEXT_CAPABILITY, true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition POST_REALM_PRINCIPAL_TRANSFORMER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.POST_REALM_PRINCIPAL_TRANSFORMER, ModelType.STRING, true)
            .setMinSize(1)
            .setCapabilityReference(PRINCIPAL_TRANSFORMER_CAPABILITY, SSL_CONTEXT_CAPABILITY, true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition FINAL_PRINCIPAL_TRANSFORMER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.FINAL_PRINCIPAL_TRANSFORMER, ModelType.STRING, true)
            .setMinSize(1)
            .setCapabilityReference(PRINCIPAL_TRANSFORMER_CAPABILITY, SSL_CONTEXT_CAPABILITY, true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition REALM_MAPPER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REALM_MAPPER, ModelType.STRING, true)
            .setMinSize(1)
            .setCapabilityReference(REALM_MAPPER_CAPABILITY, SSL_CONTEXT_CAPABILITY, true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition CIPHER_SUITE_FILTER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CIPHER_SUITE_FILTER, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .setValidator(new CipherSuiteFilterValidator())
            .setDefaultValue(new ModelNode("DEFAULT"))
            .build();

    private static final String[] ALLOWED_PROTOCOLS = { "SSLv2", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3" };

    static final StringListAttributeDefinition PROTOCOLS = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.PROTOCOLS)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRequired(false)
            .setAllowedValues(ALLOWED_PROTOCOLS)
            .setValidator(new StringValuesValidator(ALLOWED_PROTOCOLS))
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition WANT_CLIENT_AUTH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.WANT_CLIENT_AUTH, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(false))
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition NEED_CLIENT_AUTH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NEED_CLIENT_AUTH, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(false))
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition AUTHENTICATION_OPTIONAL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.AUTHENTICATION_OPTIONAL, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(false))
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition USE_CIPHER_SUITES_ORDER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.USE_CIPHER_SUITES_ORDER, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(true))
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition MAXIMUM_SESSION_CACHE_SIZE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MAXIMUM_SESSION_CACHE_SIZE, ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(-1))
            .setValidator(new IntRangeValidator(-1))
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition SESSION_TIMEOUT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SESSION_TIMEOUT, ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(-1))
            .setValidator(new IntRangeValidator(-1))
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition WRAP = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.WRAP, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(false))
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition KEY_MANAGER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEY_MANAGER, ModelType.STRING, true)
            .setMinSize(1)
            .setCapabilityReference(KEY_MANAGER_CAPABILITY, SSL_CONTEXT_CAPABILITY, true)
            .setRestartAllServices()
            .setAllowExpression(false)
            .build();

    static final SimpleAttributeDefinition TRUST_MANAGER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.TRUST_MANAGER, ModelType.STRING, true)
            .setMinSize(1)
            .setCapabilityReference(TRUST_MANAGER_CAPABILITY, SSL_CONTEXT_CAPABILITY, true)
            .setRestartAllServices()
            .setAllowExpression(false)
            .build();

    private static final SimpleAttributeDefinition MAXIMUM_CERT_PATH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MAXIMUM_CERT_PATH, ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(5))
            .setValidator(new IntRangeValidator(1))
            .setRestartAllServices()
            .build();

    static final ObjectTypeAttributeDefinition CERTIFICATE_REVOCATION_LIST = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.CERTIFICATE_REVOCATION_LIST, PATH, RELATIVE_TO, MAXIMUM_CERT_PATH)
            .setRequired(false)
            .setRestartAllServices()
            .build();

    /*
     * Runtime Attributes
     */

    private static SimpleAttributeDefinition ACTIVE_SESSION_COUNT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ACTIVE_SESSION_COUNT, ModelType.INT)
            .setStorageRuntime()
            .build();

    /**
     * A simple {@link ModelTypeValidator} that requires that values are contained on a pre-defined list of string.
     *
     * //TODO: couldn't find a built-in validator for that. see if there is one or even if it can be moved to its own file.
     */
    static class StringValuesValidator extends ModelTypeValidator implements AllowedValuesValidator {

        private List<ModelNode> allowedValues = new ArrayList<>();

        StringValuesValidator(String... values) {
            super(ModelType.STRING);
            for (String value : values) {
                allowedValues.add(new ModelNode().set(value));
            }
        }

        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            super.validateParameter(parameterName, value);
            if (value.isDefined()) {
                if (!allowedValues.contains(value)) {
                    throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidValue(value.asString(), parameterName, allowedValues));
                }
            }
        }

        @Override
        public List<ModelNode> getAllowedValues() {
            return this.allowedValues;
        }
    }

    static class CipherSuiteFilterValidator extends ModelTypeValidator{

        CipherSuiteFilterValidator() {
            super(ModelType.STRING, true, true, false);
        }

        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            super.validateParameter(parameterName,value);
            if (value.isDefined()) {
                try {
                    CipherSuiteSelector.fromString(value.asString());
                }catch (IllegalArgumentException e){
                    throw ROOT_LOGGER.invalidCipherSuiteFilter(e, e.getLocalizedMessage());
                }
            }
        }
    }

    static ResourceDefinition getKeyManagerDefinition() {

        final ServiceUtil<KeyManager> KEY_MANAGER_UTIL = ServiceUtil.newInstance(KEY_MANAGER_RUNTIME_CAPABILITY, ElytronDescriptionConstants.KEY_MANAGER, KeyManager.class);

        final StandardResourceDescriptionResolver RESOURCE_RESOLVER = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.KEY_MANAGER);

        final SimpleAttributeDefinition providersDefinition = new SimpleAttributeDefinitionBuilder(PROVIDERS)
                .setCapabilityReference(PROVIDERS_CAPABILITY, KEY_MANAGER_CAPABILITY, true)
                .setAllowExpression(false)
                .setRestartAllServices()
                .build();

        final SimpleAttributeDefinition keystoreDefinition = new SimpleAttributeDefinitionBuilder(KEYSTORE)
                .setCapabilityReference(KEY_STORE_CAPABILITY, KEY_MANAGER_CAPABILITY, true)
                .setAllowExpression(false)
                .setRestartAllServices()
                .build();

        final ObjectTypeAttributeDefinition credentialReferenceDefinition = CredentialReference.getAttributeDefinition(true);

        AttributeDefinition[] attributes = new AttributeDefinition[] { ALGORITHM, providersDefinition, PROVIDER_NAME, keystoreDefinition, ALIAS_FILTER, credentialReferenceDefinition};

        AbstractAddStepHandler add = new TrivialAddHandler<KeyManager>(KeyManager.class, attributes, KEY_MANAGER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<KeyManager> getValueSupplier(ServiceBuilder<KeyManager> serviceBuilder, OperationContext context, ModelNode model) throws OperationFailedException {
                final String algorithmName = asStringIfDefined(context, ALGORITHM, model);
                final String providerName = asStringIfDefined(context, PROVIDER_NAME, model);

                String providersName = asStringIfDefined(context, providersDefinition, model);
                final InjectedValue<Provider[]> providersInjector = new InjectedValue<>();
                if (providersName != null) {
                    serviceBuilder.addDependency(context.getCapabilityServiceName(
                            buildDynamicCapabilityName(PROVIDERS_CAPABILITY, providersName), Provider[].class),
                            Provider[].class, providersInjector);
                }

                final String keyStoreName = asStringIfDefined(context, keystoreDefinition, model);
                final InjectedValue<KeyStore> keyStoreInjector = new InjectedValue<>();
                if (keyStoreName != null) {
                    serviceBuilder.addDependency(context.getCapabilityServiceName(
                            buildDynamicCapabilityName(KEY_STORE_CAPABILITY, keyStoreName), KeyStore.class),
                            KeyStore.class, keyStoreInjector);
                }

                final String aliasFilter = asStringIfDefined(context, ALIAS_FILTER, model);
                final String algorithm = algorithmName != null ? algorithmName : KeyManagerFactory.getDefaultAlgorithm();

                ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier =
                        CredentialReference.getCredentialSourceSupplier(context, credentialReferenceDefinition, model, serviceBuilder);

                DelegatingKeyManager delegatingKeyManager = new DelegatingKeyManager();
                return () -> {
                    Provider[] providers = providersInjector.getOptionalValue();
                    KeyManagerFactory keyManagerFactory = null;
                    if (providers != null) {
                        for (Provider current : providers) {
                            if (providerName == null || providerName.equals(current.getName())) {
                                try {
                                    // TODO - We could check the Services within each Provider to check there is one of the required type/algorithm
                                    // However the same loop would need to remain as it is still possible a specific provider can't create it.
                                    keyManagerFactory = KeyManagerFactory.getInstance(algorithm, current);
                                    break;
                                } catch (NoSuchAlgorithmException ignored) {
                                }
                            }
                        }
                        if (keyManagerFactory == null) throw ROOT_LOGGER.unableToCreateManagerFactory(KeyManagerFactory.class.getSimpleName(), algorithm);
                    } else {
                        try {
                            keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
                        } catch (NoSuchAlgorithmException e) {
                            throw new StartException(e);
                        }
                    }

                    try {
                        CredentialSource cs = credentialSourceSupplier.get();
                        char[] password;
                        if (cs != null) {
                            password = cs.getCredential(PasswordCredential.class).getPassword(ClearPassword.class).getPassword();
                        } else {
                            throw new StartException(ROOT_LOGGER.keyStorePasswordCannotBeResolved(keyStoreName));
                        }
                        KeyStore keyStore = keyStoreInjector.getOptionalValue();
                        if (aliasFilter != null) {
                            keyStore = FilteringKeyStore.filteringKeyStore(keyStore, AliasFilter.fromString(aliasFilter));
                        }

                        if (ROOT_LOGGER.isTraceEnabled()) {
                            ROOT_LOGGER.tracef(
                                    "KeyManager supplying:  providers = %s  provider = %s  algorithm = %s  keyManagerFactory = %s  " +
                                            "keyStoreName = %s  aliasFilter = %s  keyStore = %s  keyStoreSize = %d  password (of item) = %b",
                                    Arrays.toString(providers), providerName, algorithm, keyManagerFactory, keyStoreName, aliasFilter, keyStore, keyStore.size(), password != null
                            );
                        }

                        keyManagerFactory.init(keyStore, password);
                    } catch (StartException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new StartException(e);
                    }

                    KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();
                    for (KeyManager keyManager : keyManagers) {
                        if (keyManager instanceof X509ExtendedKeyManager) {
                            delegatingKeyManager.setKeyManager((X509ExtendedKeyManager) keyManager);
                            return delegatingKeyManager;
                        }
                    }
                    throw ROOT_LOGGER.noTypeFound(X509ExtendedKeyManager.class.getSimpleName());
                };
            }
        };


        OperationStepHandler init = new ElytronRuntimeOnlyHandler() {
            @Override
            protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
                ServiceName keyStoreName = KEY_MANAGER_UTIL.serviceName(operation);
                ServiceController<KeyManager> serviceContainer = getRequiredService(context.getServiceRegistry(false), keyStoreName, KeyManager.class);
                try {
                    // fictive restart to recreate keyManager and exchange it in delegatingKeyManager
                    serviceContainer.getService().stop(null);
                    serviceContainer.getService().start(null);
                } catch (Exception e) {
                    throw new OperationFailedException(e);
                }
            }
        };

        return new TrivialResourceDefinition(ElytronDescriptionConstants.KEY_MANAGER, add, attributes, KEY_MANAGER_RUNTIME_CAPABILITY) {
            @Override
            public void registerOperations(ManagementResourceRegistration registration) {
                super.registerOperations(registration);
                registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.INIT, RESOURCE_RESOLVER)
                            .setRuntimeOnly()
                            .build()
                        , init);
            }
        };

    }

    static ResourceDefinition getTrustManagerDefinition() {

        final SimpleAttributeDefinition providersDefinition = new SimpleAttributeDefinitionBuilder(PROVIDERS)
                .setCapabilityReference(PROVIDERS_CAPABILITY, TRUST_MANAGER_CAPABILITY, true)
                .setAllowExpression(false)
                .setRestartAllServices()
                .build();

        final SimpleAttributeDefinition keystoreDefinition = new SimpleAttributeDefinitionBuilder(KEYSTORE)
                .setCapabilityReference(KEY_STORE_CAPABILITY, TRUST_MANAGER_CAPABILITY, true)
                .setAllowExpression(false)
                .setRestartAllServices()
                .build();

        AttributeDefinition[] attributes = new AttributeDefinition[] { ALGORITHM, providersDefinition, PROVIDER_NAME, keystoreDefinition, ALIAS_FILTER, CERTIFICATE_REVOCATION_LIST};

        AtomicBoolean reloadCrl = new AtomicBoolean(false);

        AbstractAddStepHandler add = new TrivialAddHandler<TrustManager>(TrustManager.class, attributes, TRUST_MANAGER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<TrustManager> getValueSupplier(ServiceBuilder<TrustManager> serviceBuilder, OperationContext context, ModelNode model) throws OperationFailedException {
                final String algorithmName = asStringIfDefined(context, ALGORITHM, model);
                final String providerName = asStringIfDefined(context, PROVIDER_NAME, model);

                String providerLoader = asStringIfDefined(context, providersDefinition, model);
                final InjectedValue<Provider[]> providersInjector = new InjectedValue<>();
                if (providerLoader != null) {
                    serviceBuilder.addDependency(context.getCapabilityServiceName(
                            buildDynamicCapabilityName(PROVIDERS_CAPABILITY, providerLoader), Provider[].class),
                            Provider[].class, providersInjector);
                }

                final String keyStoreName = asStringIfDefined(context, keystoreDefinition, model);
                final InjectedValue<KeyStore> keyStoreInjector = new InjectedValue<>();
                if (keyStoreName != null) {
                    serviceBuilder.addDependency(context.getCapabilityServiceName(
                            buildDynamicCapabilityName(KEY_STORE_CAPABILITY, keyStoreName), KeyStore.class),
                            KeyStore.class, keyStoreInjector);
                }

                final String aliasFilter = asStringIfDefined(context, ALIAS_FILTER, model);
                final String algorithm = algorithmName != null ? algorithmName : TrustManagerFactory.getDefaultAlgorithm();

                ModelNode crlNode = CERTIFICATE_REVOCATION_LIST.resolveModelAttribute(context, model);

                if (crlNode.isDefined()) {
                    return createX509CRLExtendedTrustManager(serviceBuilder, context, algorithm, providerName, providersInjector, keyStoreInjector, crlNode, reloadCrl);
                }

                return () -> {
                    Provider[] providers = providersInjector.getOptionalValue();

                    TrustManagerFactory trustManagerFactory = createTrustManagerFactory(providers, providerName, algorithm);
                    KeyStore keyStore = keyStoreInjector.getOptionalValue();

                    try {
                        if (aliasFilter != null) {
                            keyStore = FilteringKeyStore.filteringKeyStore(keyStore, AliasFilter.fromString(aliasFilter));
                        }

                        if (ROOT_LOGGER.isTraceEnabled()) {
                            ROOT_LOGGER.tracef(
                                    "TrustManager supplying:  providers = %s  provider = %s  algorithm = %s  trustManagerFactory = %s  keyStoreName = %s  keyStore = %s  aliasFilter = %s  keyStoreSize = %d",
                                    Arrays.toString(providers), providerName, algorithm, trustManagerFactory, keyStoreName, keyStore, aliasFilter, keyStore.size()
                            );
                        }

                        trustManagerFactory.init(keyStoreInjector.getOptionalValue());
                    } catch (Exception e) {
                        throw new StartException(e);
                    }

                    TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
                    for (TrustManager trustManager : trustManagers) {
                        if (trustManager instanceof X509ExtendedTrustManager) {
                            return trustManager;
                        }
                    }
                    throw ROOT_LOGGER.noTypeFound(X509ExtendedKeyManager.class.getSimpleName());
                };
            }

            private ValueSupplier<TrustManager> createX509CRLExtendedTrustManager(ServiceBuilder<TrustManager> serviceBuilder, OperationContext context, String algorithm, String providerName, InjectedValue<Provider[]> providersInjector, InjectedValue<KeyStore> keyStoreInjector, ModelNode crlNode, AtomicBoolean reloadCrl) throws OperationFailedException {
                String crlPath = asStringIfDefined(context, PATH, crlNode);
                String crlRelativeTo = asStringIfDefined(context, RELATIVE_TO, crlNode);
                int certPath = asIntIfDefined(context, MAXIMUM_CERT_PATH, crlNode);
                final InjectedValue<PathManager> pathManagerInjector = new InjectedValue<>();

                if (crlPath != null) {
                    if (crlRelativeTo != null) {
                        serviceBuilder.addDependency(PathManagerService.SERVICE_NAME, PathManager.class, pathManagerInjector);
                        serviceBuilder.addDependency(pathName(crlRelativeTo));
                    }
                }

                return () -> {
                    TrustManagerFactory trustManagerFactory = createTrustManagerFactory(providersInjector.getOptionalValue(), providerName, algorithm);
                    KeyStore keyStore = keyStoreInjector.getOptionalValue();

                    if (crlPath != null) {
                        try {
                            X509CRLExtendedTrustManager trustManager = new X509CRLExtendedTrustManager(keyStore, trustManagerFactory, new FileInputStream(resolveFileLocation(crlPath, crlRelativeTo, pathManagerInjector)), certPath, null);
                            return createReloadableX509CRLTrustManager(reloadCrl, crlPath, crlRelativeTo, certPath, pathManagerInjector, trustManagerFactory, keyStore, trustManager);
                        } catch (FileNotFoundException e) {
                            throw ElytronSubsystemMessages.ROOT_LOGGER.unableToAccessCRL(e);
                        }
                    }

                    return new X509CRLExtendedTrustManager(keyStore, trustManagerFactory, null, certPath, null);
                };
            }

            private TrustManager createReloadableX509CRLTrustManager(final AtomicBoolean reloadCrl, final String crlPath, final String crlRelativeTo, final int certPath, final InjectedValue<PathManager> pathManagerInjector, final TrustManagerFactory trustManagerFactory, final KeyStore keyStore, final X509CRLExtendedTrustManager trustManager) {
                return new X509ExtendedTrustManager() {

                    private volatile X509ExtendedTrustManager delegate = trustManager;
                    private AtomicBoolean reloading = new AtomicBoolean();

                    private X509ExtendedTrustManager getDelegate() {
                        if (reloadCrl.get() && reloading.compareAndSet(false, true)) {
                            X509ExtendedTrustManager reloaded = null;
                            try {
                                reloaded = new X509CRLExtendedTrustManager(keyStore, trustManagerFactory, new FileInputStream(resolveFileLocation(crlPath, crlRelativeTo, pathManagerInjector)), certPath, null);
                            } catch (FileNotFoundException cause) {
                                throw ElytronSubsystemMessages.ROOT_LOGGER.unableToReloadCRL(cause);
                            } finally {
                                if (reloaded != null) {
                                    delegate = reloaded;
                                }
                                reloadCrl.lazySet(false);
                                reloading.lazySet(false);
                            }
                        }
                        return delegate;
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s, Socket socket) throws CertificateException {
                        getDelegate().checkClientTrusted(x509Certificates, s, socket);
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] x509Certificates, String s, Socket socket) throws CertificateException {
                        getDelegate().checkServerTrusted(x509Certificates, s, socket);
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine) throws CertificateException {
                        getDelegate().checkClientTrusted(x509Certificates, s, sslEngine);
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine) throws CertificateException {
                        getDelegate().checkServerTrusted(x509Certificates, s, sslEngine);
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                        getDelegate().checkClientTrusted(x509Certificates, s);
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                        getDelegate().checkServerTrusted(x509Certificates, s);
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return getDelegate().getAcceptedIssuers();
                    }
                };
            }

            private File resolveFileLocation(String path, String relativeTo, InjectedValue<PathManager> pathManagerInjector) {
                final File resolvedPath;
                if (relativeTo != null) {
                    PathManager pathManager =  pathManagerInjector.getValue();
                    resolvedPath = new File(pathManager.resolveRelativePathEntry(path, relativeTo));
                } else {
                    resolvedPath = new File(path);
                }
                return resolvedPath;
            }

            private TrustManagerFactory createTrustManagerFactory(Provider[] providers, String providerName, String algorithm) throws StartException {
                TrustManagerFactory trustManagerFactory = null;

                if (providers != null) {
                    for (Provider current : providers) {
                        if (providerName == null || providerName.equals(current.getName())) {
                            try {
                                // TODO - We could check the Services within each Provider to check there is one of the required type/algorithm
                                // However the same loop would need to remain as it is still possible a specific provider can't create it.
                                return TrustManagerFactory.getInstance(algorithm, current);
                            } catch (NoSuchAlgorithmException ignored) {
                            }
                        }
                    }
                    if (trustManagerFactory == null) throw ROOT_LOGGER.unableToCreateManagerFactory(TrustManagerFactory.class.getSimpleName(), algorithm);
                }

                try {
                    return TrustManagerFactory.getInstance(algorithm);
                } catch (NoSuchAlgorithmException e) {
                    throw new StartException(e);
                }
            }
        };

        return new TrivialResourceDefinition(ElytronDescriptionConstants.TRUST_MANAGER, add, attributes, TRUST_MANAGER_RUNTIME_CAPABILITY) {
            @Override
            public void registerOperations(ManagementResourceRegistration resourceRegistration) {
                super.registerOperations(resourceRegistration);
                resourceRegistration.registerOperationHandler(new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.RELOAD_CERTIFICATE_REVOCATION_LIST, getResourceDescriptionResolver())
                            .setRuntimeOnly()
                            .build()
                        , new ReloadCertificateRevocationList());
            }

            class ReloadCertificateRevocationList extends ElytronRuntimeOnlyHandler {

                private ReloadCertificateRevocationList() {
                }

                @Override
                protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
                    reloadCrl.compareAndSet(false, true);
                }
            }
        };
    }

    private static class DelegatingKeyManager extends X509ExtendedKeyManager {

        private final AtomicReference<X509ExtendedKeyManager> delegating = new AtomicReference<>();

        private void setKeyManager(X509ExtendedKeyManager keyManager) {
            delegating.set(keyManager);
        }

        @Override
        public String[] getClientAliases(String s, Principal[] principals) {
            return delegating.get().getClientAliases(s, principals);
        }

        @Override
        public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
            return delegating.get().chooseClientAlias(strings, principals, socket);
        }

        @Override
        public String[] getServerAliases(String s, Principal[] principals) {
            return delegating.get().getServerAliases(s, principals);
        }

        @Override
        public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
            return delegating.get().chooseServerAlias(s, principals, socket);
        }

        @Override
        public X509Certificate[] getCertificateChain(String s) {
            return delegating.get().getCertificateChain(s);
        }

        @Override
        public PrivateKey getPrivateKey(String s) {
            return delegating.get().getPrivateKey(s);
        }

        @Override
        public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
            return delegating.get().chooseEngineClientAlias(keyType, issuers, engine);
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            return delegating.get().chooseEngineServerAlias(keyType, issuers, engine);
        }
    }

    private static class SSLContextDefinition extends TrivialResourceDefinition {
        final boolean server;

        private SSLContextDefinition(String pathKey, boolean server, AbstractAddStepHandler addHandler, AttributeDefinition[] attributes) {
            super(pathKey, addHandler, attributes, SSL_CONTEXT_RUNTIME_CAPABILITY);
            this.server = server;
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            super.registerAttributes(resourceRegistration);

            if (isServerOrHostController(resourceRegistration)) {
                resourceRegistration.registerReadOnlyAttribute(ACTIVE_SESSION_COUNT, new SSLContextRuntimeHandler() {
                    @Override
                    protected void performRuntime(ModelNode result, ModelNode operation, SSLContext sslContext) throws OperationFailedException {
                        SSLSessionContext sessionContext = server ? sslContext.getServerSessionContext() : sslContext.getClientSessionContext();
                        result.set(Collections.list(sessionContext.getIds()).stream().mapToInt((byte[] b) -> 1).sum());
                    }

                    @Override
                    protected ServiceUtil<SSLContext> getSSLContextServiceUtil() {
                        return server ? SERVER_SERVICE_UTIL : CLIENT_SERVICE_UTIL;
                    }
                });
            }
        }

        @Override
        public void registerChildren(ManagementResourceRegistration resourceRegistration) {
            super.registerChildren(resourceRegistration);

            if (isServerOrHostController(resourceRegistration)) {
                resourceRegistration.registerSubModel(new SSLSessionDefinition(server));
            }
        }
    }

    private static <T> InjectedValue<T> addDependency(String baseName, SimpleAttributeDefinition attribute,
            Class<T> type, ServiceBuilder<SSLContext> serviceBuilder, OperationContext context, ModelNode model) throws OperationFailedException {

        String dynamicNameElement = asStringIfDefined(context, attribute, model);
        InjectedValue<T> injectedValue = new InjectedValue<>();

        if (dynamicNameElement != null) {
            serviceBuilder.addDependency(context.getCapabilityServiceName(
                    buildDynamicCapabilityName(baseName, dynamicNameElement), type),
                    type, injectedValue);
        }
        return injectedValue;
    }

    static ResourceDefinition getServerSSLContextDefinition() {

        final SimpleAttributeDefinition providersDefinition = new SimpleAttributeDefinitionBuilder(PROVIDERS)
                .setCapabilityReference(PROVIDERS_CAPABILITY, SSL_CONTEXT_CAPABILITY, true)
                .setAllowExpression(false)
                .setRestartAllServices()
                .build();

        final SimpleAttributeDefinition keyManagerDefinition = new SimpleAttributeDefinitionBuilder(KEY_MANAGER)
                .setRequired(true)
                .setRestartAllServices()
                .build();

        AttributeDefinition[] attributes = new AttributeDefinition[] { CIPHER_SUITE_FILTER, PROTOCOLS,
                SECURITY_DOMAIN, WANT_CLIENT_AUTH, NEED_CLIENT_AUTH, AUTHENTICATION_OPTIONAL,
                USE_CIPHER_SUITES_ORDER, MAXIMUM_SESSION_CACHE_SIZE, SESSION_TIMEOUT, WRAP, keyManagerDefinition, TRUST_MANAGER,
                PRE_REALM_PRINCIPAL_TRANSFORMER, POST_REALM_PRINCIPAL_TRANSFORMER, FINAL_PRINCIPAL_TRANSFORMER, REALM_MAPPER,
                providersDefinition, PROVIDER_NAME };

        return new SSLContextDefinition(ElytronDescriptionConstants.SERVER_SSL_CONTEXT, true, new TrivialAddHandler<SSLContext>(SSLContext.class, attributes, SSL_CONTEXT_RUNTIME_CAPABILITY) {
            @Override
            protected ValueSupplier<SSLContext> getValueSupplier(ServiceBuilder<SSLContext> serviceBuilder, OperationContext context, ModelNode model) throws OperationFailedException {

                final InjectedValue<SecurityDomain> securityDomainInjector = addDependency(SECURITY_DOMAIN_CAPABILITY, SECURITY_DOMAIN, SecurityDomain.class, serviceBuilder, context, model);
                final InjectedValue<KeyManager> keyManagerInjector = addDependency(KEY_MANAGER_CAPABILITY, KEY_MANAGER, KeyManager.class, serviceBuilder, context, model);
                final InjectedValue<TrustManager> trustManagerInjector = addDependency(TRUST_MANAGER_CAPABILITY, TRUST_MANAGER, TrustManager.class, serviceBuilder, context, model);
                final InjectedValue<PrincipalTransformer> preRealmPrincipalTransformerInjector = addDependency(PRINCIPAL_TRANSFORMER_CAPABILITY, PRE_REALM_PRINCIPAL_TRANSFORMER, PrincipalTransformer.class, serviceBuilder, context, model);
                final InjectedValue<PrincipalTransformer> postRealmPrincipalTransformerInjector = addDependency(PRINCIPAL_TRANSFORMER_CAPABILITY, POST_REALM_PRINCIPAL_TRANSFORMER, PrincipalTransformer.class, serviceBuilder, context, model);
                final InjectedValue<PrincipalTransformer> finalPrincipalTransformerInjector = addDependency(PRINCIPAL_TRANSFORMER_CAPABILITY, FINAL_PRINCIPAL_TRANSFORMER, PrincipalTransformer.class, serviceBuilder, context, model);
                final InjectedValue<RealmMapper> realmMapperInjector = addDependency(REALM_MAPPER_CAPABILITY, REALM_MAPPER, RealmMapper.class, serviceBuilder, context, model);
                final InjectedValue<Provider[]> providersInjector = addDependency(PROVIDERS_CAPABILITY, providersDefinition, Provider[].class, serviceBuilder, context, model);

                final String providerName = asStringIfDefined(context, PROVIDER_NAME, model);
                final List<String> protocols = PROTOCOLS.unwrap(context, model);
                final String cipherSuiteFilter = asStringIfDefined(context, CIPHER_SUITE_FILTER, model);
                final boolean wantClientAuth = WANT_CLIENT_AUTH.resolveModelAttribute(context, model).asBoolean();
                final boolean needClientAuth = NEED_CLIENT_AUTH.resolveModelAttribute(context, model).asBoolean();
                final boolean authenticationOptional = AUTHENTICATION_OPTIONAL.resolveModelAttribute(context, model).asBoolean();
                final boolean useCipherSuitesOrder = USE_CIPHER_SUITES_ORDER.resolveModelAttribute(context, model).asBoolean();
                final int maximumSessionCacheSize = MAXIMUM_SESSION_CACHE_SIZE.resolveModelAttribute(context, model).asInt();
                final int sessionTimeout = SESSION_TIMEOUT.resolveModelAttribute(context, model).asInt();
                final boolean wrap = WRAP.resolveModelAttribute(context, model).asBoolean();

                return () -> {
                    SecurityDomain securityDomain = securityDomainInjector.getOptionalValue();
                    X509ExtendedKeyManager keyManager = getX509KeyManager(keyManagerInjector.getOptionalValue());
                    X509ExtendedTrustManager trustManager = getX509TrustManager(trustManagerInjector.getOptionalValue());
                    PrincipalTransformer preRealmRewriter = preRealmPrincipalTransformerInjector.getOptionalValue();
                    PrincipalTransformer postRealmRewriter = postRealmPrincipalTransformerInjector.getOptionalValue();
                    PrincipalTransformer finalRewriter = finalPrincipalTransformerInjector.getOptionalValue();
                    RealmMapper realmMapper = realmMapperInjector.getOptionalValue();
                    Provider[] providers = filterProviders(providersInjector.getOptionalValue(), providerName);

                    SSLContextBuilder builder = new SSLContextBuilder();
                    if (securityDomain != null) builder.setSecurityDomain(securityDomain);
                    if (keyManager != null) builder.setKeyManager(keyManager);
                    if (trustManager != null) builder.setTrustManager(trustManager);
                    if (providers != null) builder.setProviderSupplier(() -> providers);
                    if (cipherSuiteFilter != null) builder.setCipherSuiteSelector(CipherSuiteSelector.fromString(cipherSuiteFilter));
                    if ( ! protocols.isEmpty()) builder.setProtocolSelector(ProtocolSelector.empty().add(
                            EnumSet.copyOf(protocols.stream().map(Protocol::forName).collect(Collectors.toList()))
                    ));
                    if (preRealmRewriter != null || postRealmRewriter != null || finalRewriter != null || realmMapper != null) {
                        MechanismConfiguration.Builder mechBuilder = MechanismConfiguration.builder();
                        if (preRealmRewriter != null) mechBuilder.setPreRealmRewriter(preRealmRewriter);
                        if (postRealmRewriter != null) mechBuilder.setPostRealmRewriter(postRealmRewriter);
                        if (finalRewriter != null) mechBuilder.setFinalRewriter(finalRewriter);
                        if (realmMapper != null) mechBuilder.setRealmMapper(realmMapper);
                        builder.setMechanismConfigurationSelector(MechanismConfigurationSelector.constantSelector(mechBuilder.build()));
                    }
                    builder.setWantClientAuth(wantClientAuth)
                           .setNeedClientAuth(needClientAuth)
                           .setAuthenticationOptional(authenticationOptional)
                           .setUseCipherSuitesOrder(useCipherSuitesOrder)
                           .setSessionCacheSize(maximumSessionCacheSize)
                           .setSessionTimeout(sessionTimeout)
                           .setWrap(wrap);

                    if (ROOT_LOGGER.isTraceEnabled()) {
                        ROOT_LOGGER.tracef(
                                "ServerSSLContext supplying:  securityDomain = %s  keyManager = %s  trustManager = %s  " +
                                "providers = %s  cipherSuiteFilter = %s  protocols = %s  wantClientAuth = %s  needClientAuth = %s  " +
                                "authenticationOptional = %s  maximumSessionCacheSize = %s  sessionTimeout = %s wrap = %s",
                                securityDomain, keyManager, trustManager, Arrays.toString(providers), cipherSuiteFilter,
                                Arrays.toString(protocols.toArray()), wantClientAuth, needClientAuth, authenticationOptional,
                                maximumSessionCacheSize, sessionTimeout, wrap);
                    }

                    try {
                        return builder.build().create();
                    } catch (GeneralSecurityException e) {
                        throw new StartException(e);
                    }
                };
            }

            @Override
            protected Resource createResource(OperationContext context) {
                SSLContextResource resource = new SSLContextResource(Resource.Factory.create(), true);
                context.addResource(PathAddress.EMPTY_ADDRESS, resource);
                return resource;
            }

            @Override
            protected void installedForResource(ServiceController<SSLContext> serviceController, Resource resource) {
                ((SSLContextResource)resource).setSSLContextServiceController(serviceController);
            }
        }, attributes);
    }

    static ResourceDefinition getClientSSLContextDefinition() {

        final SimpleAttributeDefinition providersDefinition = new SimpleAttributeDefinitionBuilder(PROVIDERS)
                .setCapabilityReference(PROVIDERS_CAPABILITY, SSL_CONTEXT_CAPABILITY, true)
                .setAllowExpression(false)
                .setRestartAllServices()
                .build();

        AttributeDefinition[] attributes = new AttributeDefinition[] { CIPHER_SUITE_FILTER, PROTOCOLS,
                KEY_MANAGER, TRUST_MANAGER, providersDefinition, PROVIDER_NAME };

        return new SSLContextDefinition(ElytronDescriptionConstants.CLIENT_SSL_CONTEXT, false, new TrivialAddHandler<SSLContext>(SSLContext.class, attributes, SSL_CONTEXT_RUNTIME_CAPABILITY) {
            @Override
            protected ValueSupplier<SSLContext> getValueSupplier(ServiceBuilder<SSLContext> serviceBuilder, OperationContext context, ModelNode model) throws OperationFailedException {

                final InjectedValue<KeyManager> keyManagerInjector = addDependency(KEY_MANAGER_CAPABILITY, KEY_MANAGER, KeyManager.class, serviceBuilder, context, model);
                final InjectedValue<TrustManager> trustManagerInjector = addDependency(TRUST_MANAGER_CAPABILITY, TRUST_MANAGER, TrustManager.class, serviceBuilder, context, model);
                final InjectedValue<Provider[]> providersInjector = addDependency(PROVIDERS_CAPABILITY, providersDefinition, Provider[].class, serviceBuilder, context, model);

                final String providerName = asStringIfDefined(context, PROVIDER_NAME, model);
                final List<String> protocols = PROTOCOLS.unwrap(context, model);
                final String cipherSuiteFilter = asStringIfDefined(context, CIPHER_SUITE_FILTER, model);

                return () -> {
                    X509ExtendedKeyManager keyManager = getX509KeyManager(keyManagerInjector.getOptionalValue());
                    X509ExtendedTrustManager trustManager = getX509TrustManager(trustManagerInjector.getOptionalValue());
                    Provider[] providers = filterProviders(providersInjector.getOptionalValue(), providerName);

                    SSLContextBuilder builder = new SSLContextBuilder();
                    if (keyManager != null) builder.setKeyManager(keyManager);
                    if (trustManager != null) builder.setTrustManager(trustManager);
                    if (providers != null) builder.setProviderSupplier(() -> providers);
                    if (cipherSuiteFilter != null) builder.setCipherSuiteSelector(CipherSuiteSelector.fromString(cipherSuiteFilter));
                    if ( ! protocols.isEmpty()) builder.setProtocolSelector(ProtocolSelector.empty().add(
                            EnumSet.copyOf(protocols.stream().map(Protocol::forName).collect(Collectors.toList()))
                    ));
                    builder.setClientMode(true)
                        .setWrap(false);

                    if (ROOT_LOGGER.isTraceEnabled()) {
                        ROOT_LOGGER.tracef(
                                "ClientSSLContext supplying:  keyManager = %s  trustManager = %s  providers = %s  " +
                                "cipherSuiteFilter = %s  protocols = %s",
                                keyManager, trustManager, Arrays.toString(providers), cipherSuiteFilter,
                                Arrays.toString(protocols.toArray())
                        );
                    }

                    try {
                        return builder.build().create();
                    } catch (GeneralSecurityException e) {
                        throw new StartException(e);
                    }
                };
            }

            @Override
            protected Resource createResource(OperationContext context) {
                SSLContextResource resource = new SSLContextResource(Resource.Factory.create(), false);
                context.addResource(PathAddress.EMPTY_ADDRESS, resource);
                return resource;
            }

            @Override
            protected void installedForResource(ServiceController<SSLContext> serviceController, Resource resource) {
                ((SSLContextResource)resource).setSSLContextServiceController(serviceController);
            }
        }, attributes);
    }

    private static Provider[] filterProviders(Provider[] all, String provider) {
        if (provider == null || all == null) return all;
        return Arrays.stream(all)
                .filter(current -> provider.equals(current.getName()))
                .toArray(Provider[]::new);
    }

    private static X509ExtendedKeyManager getX509KeyManager(KeyManager keyManager) throws StartException {
        if (keyManager == null) {
            return null;
        }
        if (keyManager instanceof X509ExtendedKeyManager) {
            X509ExtendedKeyManager x509KeyManager = (X509ExtendedKeyManager) keyManager;
            if (x509KeyManager instanceof DelegatingKeyManager && IS_FIPS.getAsBoolean()) {
                ROOT_LOGGER.trace("FIPS enabled on JVM, unwrapping KeyManager");
                // If FIPS is enabled unwrap the KeyManager
                x509KeyManager = ((DelegatingKeyManager)x509KeyManager).delegating.get();
            }

            return x509KeyManager;
        }
        throw ROOT_LOGGER.invalidTypeInjected(X509ExtendedKeyManager.class.getSimpleName());
    }

    private static X509ExtendedTrustManager getX509TrustManager(TrustManager trustManager) throws StartException {
        if (trustManager == null) {
            return null;
        }
        if (trustManager instanceof X509ExtendedTrustManager) {
            return (X509ExtendedTrustManager) trustManager;
        }
        throw ROOT_LOGGER.invalidTypeInjected(X509ExtendedTrustManager.class.getSimpleName());
    }

    abstract static class SSLContextRuntimeHandler extends ElytronRuntimeOnlyHandler {
        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            ServiceName serviceName = getSSLContextServiceUtil().serviceName(operation);

            ServiceController<SSLContext> serviceController = getRequiredService(context.getServiceRegistry(false), serviceName, SSLContext.class);
            State serviceState;
            if ((serviceState = serviceController.getState()) != State.UP) {
                    throw ROOT_LOGGER.requiredServiceNotUp(serviceName, serviceState);
            }

            performRuntime(context.getResult(), operation, serviceController.getService().getValue());
        }

        protected abstract void performRuntime(ModelNode result, ModelNode operation, SSLContext sslContext) throws OperationFailedException;

        protected abstract ServiceUtil<SSLContext> getSSLContextServiceUtil();
    }

    private static BooleanSupplier getFipsSupplier() {
        try {
            final Class<?> providerClazz = SSLDefinitions.class.getClassLoader().loadClass("com.sun.net.ssl.internal.ssl.Provider");
            final Method isFipsMethod = providerClazz.getMethod("isFIPS", new Class[0]);

            return () -> {
                Object isFips;
                try {
                    isFips = isFipsMethod.invoke(null, new Object[0]);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    ROOT_LOGGER.trace("Unable to invoke com.sun.net.ssl.internal.ssl.Provider.isFIPS() method.", e);
                    return false;
                }

                return isFips != null && isFips instanceof Boolean ? ((Boolean)isFips).booleanValue() : false;
            };

        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException e) {
            ROOT_LOGGER.trace("Unable to find com.sun.net.ssl.internal.ssl.Provider.isFIPS() method.", e);
        }

        return Boolean.FALSE::booleanValue;
    }

}
