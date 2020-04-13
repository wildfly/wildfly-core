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
import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;
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
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
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
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
import org.jboss.as.controller.MapAttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.validation.AllowedValuesValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.registry.AttributeAccess;
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
import org.wildfly.extension.elytron.TrivialResourceDefinition.Builder;
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
import org.wildfly.security.ssl.SNIContextMatcher;
import org.wildfly.security.ssl.SNISSLContext;
import org.wildfly.security.ssl.SSLContextBuilder;
import org.wildfly.security.ssl.X509RevocationTrustManager;

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
            .setCapabilityReference(SECURITY_DOMAIN_CAPABILITY, SSL_CONTEXT_CAPABILITY)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition PRE_REALM_PRINCIPAL_TRANSFORMER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PRE_REALM_PRINCIPAL_TRANSFORMER, ModelType.STRING, true)
            .setMinSize(1)
            .setCapabilityReference(PRINCIPAL_TRANSFORMER_CAPABILITY, SSL_CONTEXT_CAPABILITY)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition POST_REALM_PRINCIPAL_TRANSFORMER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.POST_REALM_PRINCIPAL_TRANSFORMER, ModelType.STRING, true)
            .setMinSize(1)
            .setCapabilityReference(PRINCIPAL_TRANSFORMER_CAPABILITY, SSL_CONTEXT_CAPABILITY)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition FINAL_PRINCIPAL_TRANSFORMER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.FINAL_PRINCIPAL_TRANSFORMER, ModelType.STRING, true)
            .setMinSize(1)
            .setCapabilityReference(PRINCIPAL_TRANSFORMER_CAPABILITY, SSL_CONTEXT_CAPABILITY)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition REALM_MAPPER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REALM_MAPPER, ModelType.STRING, true)
            .setMinSize(1)
            .setCapabilityReference(REALM_MAPPER_CAPABILITY, SSL_CONTEXT_CAPABILITY)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition CIPHER_SUITE_FILTER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CIPHER_SUITE_FILTER, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .setValidator(new CipherSuiteFilterValidator())
            .setDefaultValue(new ModelNode("DEFAULT"))
            .build();

    static final SimpleAttributeDefinition CIPHER_SUITE_NAMES = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CIPHER_SUITE_NAMES, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .setValidator(new CipherSuiteNamesValidator())
            // WFCORE-4789: Add the following line back when we are ready to enable TLS 1.3 by default
            //.setDefaultValue(new ModelNode(CipherSuiteSelector.OPENSSL_DEFAULT_CIPHER_SUITE_NAMES))
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
            .setDefaultValue(ModelNode.FALSE)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition NEED_CLIENT_AUTH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NEED_CLIENT_AUTH, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition AUTHENTICATION_OPTIONAL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.AUTHENTICATION_OPTIONAL, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition USE_CIPHER_SUITES_ORDER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.USE_CIPHER_SUITES_ORDER, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.TRUE)
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
            .setDefaultValue(ModelNode.FALSE)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition KEY_MANAGER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEY_MANAGER, ModelType.STRING, true)
            .setMinSize(1)
            .setCapabilityReference(KEY_MANAGER_CAPABILITY, SSL_CONTEXT_CAPABILITY)
            .setRestartAllServices()
            .setAllowExpression(false)
            .build();

    static final SimpleAttributeDefinition TRUST_MANAGER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.TRUST_MANAGER, ModelType.STRING, true)
            .setMinSize(1)
            .setCapabilityReference(TRUST_MANAGER_CAPABILITY, SSL_CONTEXT_CAPABILITY)
            .setRestartAllServices()
            .setAllowExpression(false)
            .build();

    static final SimpleAttributeDefinition MAXIMUM_CERT_PATH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MAXIMUM_CERT_PATH, ModelType.INT, true)
            .setAllowExpression(true)
            .setValidator(new IntRangeValidator(1))
            .setRestartAllServices()
            .build();

    @Deprecated
    static final SimpleAttributeDefinition MAXIMUM_CERT_PATH_CRL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MAXIMUM_CERT_PATH, ModelType.INT, true)
            .setAllowExpression(true)
            .setValidator(new IntRangeValidator(1))
            .setDeprecated(ModelVersion.create(8))
            .setRestartAllServices()
            .build();

    static final ObjectTypeAttributeDefinition CERTIFICATE_REVOCATION_LIST = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.CERTIFICATE_REVOCATION_LIST, PATH, RELATIVE_TO, MAXIMUM_CERT_PATH_CRL)
            .setRequired(false)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition RESPONDER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.RESPONDER, ModelType.STRING, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition PREFER_CRLS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PREFER_CRLS, ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.FALSE)
            .setRequired(false)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition SOFT_FAIL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SOFT_FAIL, ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.FALSE)
            .setRequired(false)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition ONLY_LEAF_CERT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ONLY_LEAF_CERT, ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.FALSE)
            .setRequired(false)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition RESPONDER_CERTIFICATE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.RESPONDER_CERTIFICATE, ModelType.STRING, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setRequired(false)
            .build();

    static final SimpleAttributeDefinition RESPONDER_KEYSTORE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.RESPONDER_KEYSTORE, ModelType.STRING, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setRequired(false)
            .setRequires(ElytronDescriptionConstants.RESPONDER_CERTIFICATE)
            .build();

    static final ObjectTypeAttributeDefinition OCSP = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.OCSP, RESPONDER, PREFER_CRLS, RESPONDER_CERTIFICATE, RESPONDER_KEYSTORE)
            .setRequired(false)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition DEFAULT_SSL_CONTEXT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DEFAULT_SSL_CONTEXT, ModelType.STRING)
            .setCapabilityReference(SSL_CONTEXT_CAPABILITY)
            .setRequired(true)
            .setRestartAllServices()
            .build();

    static final MapAttributeDefinition HOST_CONTEXT_MAP = new SimpleMapAttributeDefinition.Builder(ElytronDescriptionConstants.HOST_CONTEXT_MAP, ModelType.STRING, true)
            .setMinSize(0)
            .setAllowExpression(false)
            .setCapabilityReference(SSL_CONTEXT_CAPABILITY)
            .setMapValidator(new HostContextMapValidator())
            .setRestartAllServices()
            .build();


    /*
     * Runtime Attributes
     */

    private static final SimpleAttributeDefinition ACTIVE_SESSION_COUNT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ACTIVE_SESSION_COUNT, ModelType.INT)
            .setStorageRuntime()
            .build();


    /**
     * A simple {@link ModelTypeValidator} that requires that values are contained on a pre-defined list of string.
     * <p>
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

    static class CipherSuiteFilterValidator extends ModelTypeValidator {

        CipherSuiteFilterValidator() {
            super(ModelType.STRING, true, true, false);
        }

        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            super.validateParameter(parameterName, value);
            if (value.isDefined()) {
                try {
                    CipherSuiteSelector.fromString(value.asString());
                } catch (IllegalArgumentException e) {
                    throw ROOT_LOGGER.invalidCipherSuiteFilter(e, e.getLocalizedMessage());
                }
            }
        }
    }

    static class HostContextMapValidator implements ParameterValidator {
        // Hostnames can contain ASCII letters a-z (case-insensitive), digits 0-9, hyphens and dots.
        // This pattern allows also [,],*,? characters to make regular expressions possible. Non-escaped dot represents any character, escaped dot is delimeter.
        static Pattern hostnameRegexPattern = Pattern.compile("[0-9a-zA-Z\\[.*]" + // first character can be digit, letter, left square bracket, non-escaped dot or asterisk
                "([0-9a-zA-Z*.\\[\\]?-]" + // any combination of digits, letters, asterisks, non-escaped dots, square brackets, question marks and hyphens
                "|" +                       // OR
                "(?<!\\\\\\.)\\\\\\.)*" +   // if there is an escaped dot, there cannot be another escaped dot right behind it
                // backslash must be escaped, so '\\\\' translates to literally slash, and '\\.' translates to literally dot
                "[0-9a-zA-Z*.\\[\\]?]");   // escaped dot or hyphen cannot be at the end

        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            if (value.isDefined()) {
                for (String hostname : value.keys()) {
                    if (!hostnameRegexPattern.matcher(hostname).matches()) {
                        throw ROOT_LOGGER.invalidHostContextMapValue(hostname);
                    }
                    try {
                        Pattern.compile(hostname);  // make sure the input is valid regex as well (eg. will check that the square brackets are paired)
                    } catch (PatternSyntaxException exception) {
                        throw ROOT_LOGGER.invalidHostContextMapValue(hostname);
                    }
                }
            }
        }
    }

    static class CipherSuiteNamesValidator extends ModelTypeValidator {

        CipherSuiteNamesValidator() {
            super(ModelType.STRING, true, true, false);
        }

        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            super.validateParameter(parameterName, value);
            if (value.isDefined()) {
                try {
                    CipherSuiteSelector.fromNamesString(value.asString());
                } catch (IllegalArgumentException e) {
                    throw ROOT_LOGGER.invalidCipherSuiteNames(e, e.getLocalizedMessage());
                }
            }
        }
    }

    static ResourceDefinition getKeyManagerDefinition() {

        final StandardResourceDescriptionResolver RESOURCE_RESOLVER = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.KEY_MANAGER);

        final SimpleAttributeDefinition providersDefinition = new SimpleAttributeDefinitionBuilder(PROVIDERS)
                .setCapabilityReference(PROVIDERS_CAPABILITY, KEY_MANAGER_CAPABILITY)
                .setAllowExpression(false)
                .setRestartAllServices()
                .build();

        final SimpleAttributeDefinition keystoreDefinition = new SimpleAttributeDefinitionBuilder(KEYSTORE)
                .setCapabilityReference(KEY_STORE_CAPABILITY, KEY_MANAGER_CAPABILITY)
                .setAllowExpression(false)
                .setRestartAllServices()
                .build();

        final ObjectTypeAttributeDefinition credentialReferenceDefinition = CredentialReference.getAttributeDefinition(true);

        AttributeDefinition[] attributes = new AttributeDefinition[]{ALGORITHM, providersDefinition, PROVIDER_NAME, keystoreDefinition, ALIAS_FILTER, credentialReferenceDefinition};

        AbstractAddStepHandler add = new TrivialAddHandler<KeyManager>(KeyManager.class, attributes, KEY_MANAGER_RUNTIME_CAPABILITY) {

            @Override
            protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws  OperationFailedException {
                super.populateModel(context, operation, resource);
                handleCredentialReferenceUpdate(context, resource.getModel());
            }

            @Override
            protected ValueSupplier<KeyManager> getValueSupplier(ServiceBuilder<KeyManager> serviceBuilder, OperationContext context, ModelNode model) throws OperationFailedException {
                final String algorithmName = ALGORITHM.resolveModelAttribute(context, model).asStringOrNull();
                final String providerName = PROVIDER_NAME.resolveModelAttribute(context, model).asStringOrNull();

                String providersName = providersDefinition.resolveModelAttribute(context, model).asStringOrNull();
                final InjectedValue<Provider[]> providersInjector = new InjectedValue<>();
                if (providersName != null) {
                    serviceBuilder.addDependency(context.getCapabilityServiceName(
                            buildDynamicCapabilityName(PROVIDERS_CAPABILITY, providersName), Provider[].class),
                            Provider[].class, providersInjector);
                }

                final String keyStoreName = keystoreDefinition.resolveModelAttribute(context, model).asStringOrNull();
                final InjectedValue<KeyStore> keyStoreInjector = new InjectedValue<>();
                if (keyStoreName != null) {
                    serviceBuilder.addDependency(context.getCapabilityServiceName(
                            buildDynamicCapabilityName(KEY_STORE_CAPABILITY, keyStoreName), KeyStore.class),
                            KeyStore.class, keyStoreInjector);
                }

                final String aliasFilter = ALIAS_FILTER.resolveModelAttribute(context, model).asStringOrNull();
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
                        if (keyManagerFactory == null)
                            throw ROOT_LOGGER.unableToCreateManagerFactory(KeyManagerFactory.class.getSimpleName(), algorithm);
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

            @Override
            protected void rollbackRuntime(OperationContext context, final ModelNode operation, final Resource resource) {
                rollbackCredentialStoreUpdate(credentialReferenceDefinition, context, resource);
            }
        };

        final ServiceUtil<KeyManager> KEY_MANAGER_UTIL = ServiceUtil.newInstance(KEY_MANAGER_RUNTIME_CAPABILITY, ElytronDescriptionConstants.KEY_MANAGER, KeyManager.class);
        return TrivialResourceDefinition.builder()
                .setPathKey(ElytronDescriptionConstants.KEY_MANAGER)
                .setAddHandler(add)
                .setAttributes(attributes)
                .setRuntimeCapabilities(KEY_MANAGER_RUNTIME_CAPABILITY)
                .addOperation(new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.INIT, RESOURCE_RESOLVER)
                        .setRuntimeOnly()
                        .build(), init(KEY_MANAGER_UTIL))
                .build();
    }

    private abstract static class ReloadableX509ExtendedTrustManager extends X509ExtendedTrustManager {
        abstract void reload();
    }

    static ResourceDefinition getTrustManagerDefinition() {

        final StandardResourceDescriptionResolver RESOURCE_RESOLVER = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.TRUST_MANAGER);

        final SimpleAttributeDefinition providersDefinition = new SimpleAttributeDefinitionBuilder(PROVIDERS)
                .setCapabilityReference(PROVIDERS_CAPABILITY, TRUST_MANAGER_CAPABILITY)
                .setAllowExpression(false)
                .setRestartAllServices()
                .build();

        final SimpleAttributeDefinition keystoreDefinition = new SimpleAttributeDefinitionBuilder(KEYSTORE)
                .setCapabilityReference(KEY_STORE_CAPABILITY, TRUST_MANAGER_CAPABILITY)
                .setAllowExpression(false)
                .setRestartAllServices()
                .build();


        AttributeDefinition[] attributes = new AttributeDefinition[]{ALGORITHM, providersDefinition, PROVIDER_NAME, keystoreDefinition, ALIAS_FILTER, CERTIFICATE_REVOCATION_LIST, OCSP, SOFT_FAIL, ONLY_LEAF_CERT, MAXIMUM_CERT_PATH};

        AbstractAddStepHandler add = new TrivialAddHandler<TrustManager>(TrustManager.class, attributes, TRUST_MANAGER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<TrustManager> getValueSupplier(ServiceBuilder<TrustManager> serviceBuilder, OperationContext context, ModelNode model) throws OperationFailedException {
                final String algorithmName = ALGORITHM.resolveModelAttribute(context, model).asStringOrNull();
                final String providerName = PROVIDER_NAME.resolveModelAttribute(context, model).asStringOrNull();

                String providerLoader = providersDefinition.resolveModelAttribute(context, model).asStringOrNull();
                final InjectedValue<Provider[]> providersInjector = new InjectedValue<>();
                if (providerLoader != null) {
                    serviceBuilder.addDependency(context.getCapabilityServiceName(
                            buildDynamicCapabilityName(PROVIDERS_CAPABILITY, providerLoader), Provider[].class),
                            Provider[].class, providersInjector);
                }

                final String keyStoreName = keystoreDefinition.resolveModelAttribute(context, model).asStringOrNull();
                final InjectedValue<KeyStore> keyStoreInjector = new InjectedValue<>();
                if (keyStoreName != null) {
                    serviceBuilder.addDependency(context.getCapabilityServiceName(
                            buildDynamicCapabilityName(KEY_STORE_CAPABILITY, keyStoreName), KeyStore.class),
                            KeyStore.class, keyStoreInjector);
                }

                final String aliasFilter = ALIAS_FILTER.resolveModelAttribute(context, model).asStringOrNull();
                final String algorithm = algorithmName != null ? algorithmName : TrustManagerFactory.getDefaultAlgorithm();

                ModelNode crlNode = CERTIFICATE_REVOCATION_LIST.resolveModelAttribute(context, model);
                ModelNode ocspNode = OCSP.resolveModelAttribute(context, model);
                boolean softFail = SOFT_FAIL.resolveModelAttribute(context, model).asBoolean();
                Integer maxCertPath = MAXIMUM_CERT_PATH.resolveModelAttribute(context, model).asIntOrNull();

                if (crlNode.isDefined() || ocspNode.isDefined()) {
                    return createX509RevocationTrustManager(serviceBuilder, context, algorithm, providerName, providersInjector, keyStoreInjector, softFail, crlNode, ocspNode, maxCertPath, aliasFilter);
                }

                DelegatingTrustManager delegatingTrustManager = new DelegatingTrustManager();
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
                            delegatingTrustManager.setTrustManager((X509ExtendedTrustManager) trustManager);
                            return delegatingTrustManager;
                        }
                    }
                    throw ROOT_LOGGER.noTypeFound(X509ExtendedKeyManager.class.getSimpleName());
                };
            }

            private ValueSupplier<TrustManager> createX509RevocationTrustManager(ServiceBuilder<TrustManager> serviceBuilder, OperationContext context, String algorithm, String providerName, InjectedValue<Provider[]> providersInjector, InjectedValue<KeyStore> keyStoreInjector, boolean softFail, ModelNode crlNode, ModelNode ocspNode, Integer maxCertPath, String aliasFilter) throws OperationFailedException {


                //BW compatibility, max cert path is now in trust-manager
                @Deprecated
                Integer crlCertPath = MAXIMUM_CERT_PATH_CRL.resolveModelAttribute(context, crlNode).asIntOrNull();
                if (crlCertPath != null) {
                    ROOT_LOGGER.warn("maximum-cert-path in certificate-revocation-list is for legacy support. Please use only the one in trust-manager!");
                    if (maxCertPath != null) {
                        throw ROOT_LOGGER.multipleMaximumCertPathDefinitions();
                    }
                    maxCertPath = crlCertPath;
                }

                String crlPath = null;
                String crlRelativeTo = null;
                final InjectedValue<PathManager> pathManagerInjector = new InjectedValue<>();

                if (crlNode.isDefined()) {
                    crlPath = PATH.resolveModelAttribute(context, crlNode).asStringOrNull();
                    crlRelativeTo = RELATIVE_TO.resolveModelAttribute(context, crlNode).asStringOrNull();

                    if (crlPath != null) {
                        if (crlRelativeTo != null) {
                            serviceBuilder.addDependency(PathManagerService.SERVICE_NAME, PathManager.class, pathManagerInjector);
                            serviceBuilder.requires(pathName(crlRelativeTo));
                        }
                    }
                }
                boolean preferCrls = PREFER_CRLS.resolveModelAttribute(context, ocspNode).asBoolean(false);
                String responder = RESPONDER.resolveModelAttribute(context, ocspNode).asStringOrNull();
                String responderCertAlias = RESPONDER_CERTIFICATE.resolveModelAttribute(context, ocspNode).asStringOrNull();
                String responderKeystore = RESPONDER_KEYSTORE.resolveModelAttribute(context, ocspNode).asStringOrNull();

                final InjectedValue<KeyStore> responderStoreInjector = responderKeystore != null ? new InjectedValue<>() : keyStoreInjector;

                if (responderKeystore != null) {
                    serviceBuilder.addDependency(context.getCapabilityServiceName(
                            buildDynamicCapabilityName(KEY_STORE_CAPABILITY, responderKeystore), KeyStore.class),
                            KeyStore.class, responderStoreInjector);
                }

                URI responderUri;
                try {
                    responderUri = responder == null ? null : new URI(responder);
                } catch (Exception e) {
                    throw new OperationFailedException(e);
                }

                X509RevocationTrustManager.Builder builder = X509RevocationTrustManager.builder();
                builder.setResponderURI(responderUri);
                builder.setSoftFail(softFail);
                if (maxCertPath != null) {
                    builder.setMaxCertPath(maxCertPath.intValue());
                }
                if (crlNode.isDefined()) {
                    if (!ocspNode.isDefined()) {
                        builder.setPreferCrls(true);
                        builder.setNoFallback(true);
                    }
                }
                if (ocspNode.isDefined()) {
                    builder.setResponderURI(responderUri);
                    if (!crlNode.isDefined()) {
                        builder.setPreferCrls(false);
                        builder.setNoFallback(true);
                    } else {
                        builder.setPreferCrls(preferCrls);
                    }
                }

                final String finalCrlPath = crlPath;
                final String finalCrlRelativeTo = crlRelativeTo;

                return () -> {
                    TrustManagerFactory trustManagerFactory = createTrustManagerFactory(providersInjector.getOptionalValue(), providerName, algorithm);
                    KeyStore keyStore = keyStoreInjector.getOptionalValue();

                    if (aliasFilter != null) {
                        try {
                            keyStore = FilteringKeyStore.filteringKeyStore(keyStore, AliasFilter.fromString(aliasFilter));
                        } catch (Exception e) {
                            throw new StartException(e);
                        }
                    }

                    if (responderCertAlias != null) {
                        KeyStore responderStore = responderStoreInjector.getOptionalValue();
                        try {
                            builder.setOcspResponderCert((X509Certificate) responderStore.getCertificate(responderCertAlias));
                        } catch (KeyStoreException e) {
                            throw ElytronSubsystemMessages.ROOT_LOGGER.failedToLoadResponderCert(responderCertAlias, e);
                        }
                    }

                    builder.setTrustStore(keyStore);
                    builder.setTrustManagerFactory(trustManagerFactory);

                    if (finalCrlPath != null) {
                        try {
                            builder.setCrlStream(new FileInputStream(resolveFileLocation(finalCrlPath, finalCrlRelativeTo, pathManagerInjector)));
                            return createReloadableX509CRLTrustManager(finalCrlPath, finalCrlRelativeTo, pathManagerInjector, builder);
                        } catch (FileNotFoundException e) {
                            throw ElytronSubsystemMessages.ROOT_LOGGER.unableToAccessCRL(e);
                        }
                    }

                    return builder.build();
                };
            }

            private TrustManager createReloadableX509CRLTrustManager(final String crlPath, final String crlRelativeTo, final InjectedValue<PathManager> pathManagerInjector, final X509RevocationTrustManager.Builder builder) {
                return new ReloadableX509ExtendedTrustManager() {

                    private volatile X509ExtendedTrustManager delegate = builder.build();
                    private AtomicBoolean reloading = new AtomicBoolean();

                    @Override
                    void reload() {
                        if (reloading.compareAndSet(false, true)) {
                            try {
                                builder.setCrlStream(new FileInputStream(resolveFileLocation(crlPath, crlRelativeTo, pathManagerInjector)));
                                delegate = builder.build();
                            } catch (FileNotFoundException cause) {
                                throw ElytronSubsystemMessages.ROOT_LOGGER.unableToReloadCRL(cause);
                            } finally {
                                reloading.lazySet(false);
                            }
                        }
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s, Socket socket) throws CertificateException {
                        delegate.checkClientTrusted(x509Certificates, s, socket);
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] x509Certificates, String s, Socket socket) throws CertificateException {
                        delegate.checkServerTrusted(x509Certificates, s, socket);
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine) throws CertificateException {
                        delegate.checkClientTrusted(x509Certificates, s, sslEngine);
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine) throws CertificateException {
                        delegate.checkServerTrusted(x509Certificates, s, sslEngine);
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                        delegate.checkClientTrusted(x509Certificates, s);
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                        delegate.checkServerTrusted(x509Certificates, s);
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return delegate.getAcceptedIssuers();
                    }
                };
            }

            private File resolveFileLocation(String path, String relativeTo, InjectedValue<PathManager> pathManagerInjector) {
                final File resolvedPath;
                if (relativeTo != null) {
                    PathManager pathManager = pathManagerInjector.getValue();
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
                    if (trustManagerFactory == null)
                        throw ROOT_LOGGER.unableToCreateManagerFactory(TrustManagerFactory.class.getSimpleName(), algorithm);
                }

                try {
                    return TrustManagerFactory.getInstance(algorithm);
                } catch (NoSuchAlgorithmException e) {
                    throw new StartException(e);
                }
            }
        };

        ResourceDescriptionResolver resolver = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.TRUST_MANAGER);
        final ServiceUtil<TrustManager> TRUST_MANAGER_UTIL = ServiceUtil.newInstance(TRUST_MANAGER_RUNTIME_CAPABILITY, ElytronDescriptionConstants.TRUST_MANAGER, TrustManager.class);
        return TrivialResourceDefinition.builder()
                .setPathKey(ElytronDescriptionConstants.TRUST_MANAGER)
                .setResourceDescriptionResolver(resolver)
                .setAddHandler(add)
                .setAttributes(attributes)
                .setRuntimeCapabilities(TRUST_MANAGER_RUNTIME_CAPABILITY)
                .addOperation(new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.RELOAD_CERTIFICATE_REVOCATION_LIST, resolver)
                        .setRuntimeOnly()
                        .build(), new ElytronRuntimeOnlyHandler() {

                            @Override
                            protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
                                ServiceName serviceName = TRUST_MANAGER_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue()).getCapabilityServiceName();
                                ServiceController<TrustManager> serviceContainer = getRequiredService(context.getServiceRegistry(true), serviceName, TrustManager.class);
                                State serviceState;
                                if ((serviceState = serviceContainer.getState()) != State.UP) {
                                    throw ROOT_LOGGER.requiredServiceNotUp(serviceName, serviceState);
                                }
                                TrustManager trustManager = serviceContainer.getValue();
                                if (! (trustManager instanceof ReloadableX509ExtendedTrustManager)) {
                                    throw ROOT_LOGGER.unableToReloadCRLNotReloadable();
                                }
                                ((ReloadableX509ExtendedTrustManager) trustManager).reload();
                            }
                        })
                .addOperation(new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.INIT, RESOURCE_RESOLVER)
                        .setRuntimeOnly()
                        .build(), init(TRUST_MANAGER_UTIL))
                .build();
    }

    private static OperationStepHandler init(ServiceUtil<?> managerUtil) {
        return new ElytronRuntimeOnlyHandler() {
            @Override
            protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
                try {
                    ServiceName serviceName = managerUtil.serviceName(operation);
                    ServiceController<?> serviceContainer = null;
                    if(serviceName.getParent().getCanonicalName().equals(KEY_MANAGER_CAPABILITY)){
                        serviceContainer = getRequiredService(context.getServiceRegistry(false), serviceName, KeyManager.class);
                    } else if (serviceName.getParent().getCanonicalName().equals(TRUST_MANAGER_CAPABILITY)) {
                        serviceContainer = getRequiredService(context.getServiceRegistry(false), serviceName, TrustManager.class);
                    }
                    serviceContainer.getService().stop(null);
                    serviceContainer.getService().start(null);
                } catch (Exception e) {
                    throw new OperationFailedException(e);
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

    private static class DelegatingTrustManager extends X509ExtendedTrustManager {

        private final AtomicReference<X509ExtendedTrustManager> delegating = new AtomicReference<>();

        public void setTrustManager(X509ExtendedTrustManager trustManager){
            delegating.set(trustManager);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s, Socket socket) throws CertificateException {
            delegating.get().checkClientTrusted(x509Certificates, s, socket);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s, Socket socket) throws CertificateException {
            delegating.get().checkServerTrusted(x509Certificates, s, socket);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine) throws CertificateException {
            delegating.get().checkClientTrusted(x509Certificates, s, sslEngine);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine) throws CertificateException {
            delegating.get().checkServerTrusted(x509Certificates, s, sslEngine);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            delegating.get().checkClientTrusted(x509Certificates, s);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            delegating.get().checkServerTrusted(x509Certificates, s);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegating.get().getAcceptedIssuers();
        }
    }

    private static ResourceDefinition createSSLContextDefinition(String pathKey, boolean server, AbstractAddStepHandler addHandler, AttributeDefinition[] attributes, boolean serverOrHostController) {
        Builder builder = TrivialResourceDefinition.builder()
                .setPathKey(pathKey)
                .setAddHandler(addHandler)
                .setAttributes(attributes)
                .setRuntimeCapabilities(SSL_CONTEXT_RUNTIME_CAPABILITY);

        if (serverOrHostController) {
            builder.addReadOnlyAttribute(ACTIVE_SESSION_COUNT, new SSLContextRuntimeHandler() {
                @Override
                protected void performRuntime(ModelNode result, ModelNode operation, SSLContext sslContext) throws OperationFailedException {
                    SSLSessionContext sessionContext = server ? sslContext.getServerSessionContext() : sslContext.getClientSessionContext();
                    int sum = 0;
                    for (byte[] b : Collections.list(sessionContext.getIds())) {
                        int i = 1;
                        sum += i;
                    }
                    result.set(sum);
                }

                @Override
                protected ServiceUtil<SSLContext> getSSLContextServiceUtil() {
                    return server ? SERVER_SERVICE_UTIL : CLIENT_SERVICE_UTIL;
                }
            }).addChild(new SSLSessionDefinition(server));
        }

        return builder.build();
    }

    private static <T> InjectedValue<T> addDependency(String baseName, SimpleAttributeDefinition attribute,
                                                      Class<T> type, ServiceBuilder<SSLContext> serviceBuilder, OperationContext context, ModelNode model) throws OperationFailedException {

        String dynamicNameElement = attribute.resolveModelAttribute(context, model).asStringOrNull();
        InjectedValue<T> injectedValue = new InjectedValue<>();

        if (dynamicNameElement != null) {
            serviceBuilder.addDependency(context.getCapabilityServiceName(
                    buildDynamicCapabilityName(baseName, dynamicNameElement), type),
                    type, injectedValue);
        }
        return injectedValue;
    }

    static ResourceDefinition getServerSSLContextDefinition(boolean serverOrHostController) {

        final SimpleAttributeDefinition providersDefinition = new SimpleAttributeDefinitionBuilder(PROVIDERS)
                .setCapabilityReference(PROVIDERS_CAPABILITY, SSL_CONTEXT_CAPABILITY)
                .setAllowExpression(false)
                .setRestartAllServices()
                .build();

        final SimpleAttributeDefinition keyManagerDefinition = new SimpleAttributeDefinitionBuilder(KEY_MANAGER)
                .setRequired(true)
                .setRestartAllServices()
                .build();

        AttributeDefinition[] attributes = new AttributeDefinition[]{CIPHER_SUITE_FILTER, CIPHER_SUITE_NAMES, PROTOCOLS,
                SECURITY_DOMAIN, WANT_CLIENT_AUTH, NEED_CLIENT_AUTH, AUTHENTICATION_OPTIONAL,
                USE_CIPHER_SUITES_ORDER, MAXIMUM_SESSION_CACHE_SIZE, SESSION_TIMEOUT, WRAP, keyManagerDefinition, TRUST_MANAGER,
                PRE_REALM_PRINCIPAL_TRANSFORMER, POST_REALM_PRINCIPAL_TRANSFORMER, FINAL_PRINCIPAL_TRANSFORMER, REALM_MAPPER,
                providersDefinition, PROVIDER_NAME};

        AbstractAddStepHandler add = new TrivialAddHandler<SSLContext>(SSLContext.class, ServiceController.Mode.ACTIVE, ServiceController.Mode.PASSIVE, attributes, SSL_CONTEXT_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<SSLContext> getValueSupplier(ServiceBuilder<SSLContext> serviceBuilder,
                                                                 OperationContext context, ModelNode model) throws OperationFailedException {

                final InjectedValue<SecurityDomain> securityDomainInjector = addDependency(SECURITY_DOMAIN_CAPABILITY, SECURITY_DOMAIN, SecurityDomain.class, serviceBuilder, context, model);
                final InjectedValue<KeyManager> keyManagerInjector = addDependency(KEY_MANAGER_CAPABILITY, KEY_MANAGER, KeyManager.class, serviceBuilder, context, model);
                final InjectedValue<TrustManager> trustManagerInjector = addDependency(TRUST_MANAGER_CAPABILITY, TRUST_MANAGER, TrustManager.class, serviceBuilder, context, model);
                final InjectedValue<PrincipalTransformer> preRealmPrincipalTransformerInjector = addDependency(PRINCIPAL_TRANSFORMER_CAPABILITY, PRE_REALM_PRINCIPAL_TRANSFORMER, PrincipalTransformer.class, serviceBuilder, context, model);
                final InjectedValue<PrincipalTransformer> postRealmPrincipalTransformerInjector = addDependency(PRINCIPAL_TRANSFORMER_CAPABILITY, POST_REALM_PRINCIPAL_TRANSFORMER, PrincipalTransformer.class, serviceBuilder, context, model);
                final InjectedValue<PrincipalTransformer> finalPrincipalTransformerInjector = addDependency(PRINCIPAL_TRANSFORMER_CAPABILITY, FINAL_PRINCIPAL_TRANSFORMER, PrincipalTransformer.class, serviceBuilder, context, model);
                final InjectedValue<RealmMapper> realmMapperInjector = addDependency(REALM_MAPPER_CAPABILITY, REALM_MAPPER, RealmMapper.class, serviceBuilder, context, model);
                final InjectedValue<Provider[]> providersInjector = addDependency(PROVIDERS_CAPABILITY, providersDefinition, Provider[].class, serviceBuilder, context, model);

                final String providerName = PROVIDER_NAME.resolveModelAttribute(context, model).asStringOrNull();
                final List<String> protocols = PROTOCOLS.unwrap(context, model);
                final String cipherSuiteFilter = CIPHER_SUITE_FILTER.resolveModelAttribute(context, model).asString(); // has default value, can't be null
                final String cipherSuiteNames = CIPHER_SUITE_NAMES.resolveModelAttribute(context, model).asStringOrNull(); // doesn't have a default value yet since we are disabling TLS 1.3 by default
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
                    if (securityDomain != null)
                        builder.setSecurityDomain(securityDomain);
                    if (keyManager != null)
                        builder.setKeyManager(keyManager);
                    if (trustManager != null)
                        builder.setTrustManager(trustManager);
                    if (providers != null)
                        builder.setProviderSupplier(() -> providers);
                    builder.setCipherSuiteSelector(CipherSuiteSelector.aggregate(cipherSuiteNames != null ? CipherSuiteSelector.fromNamesString(cipherSuiteNames) : null, CipherSuiteSelector.fromString(cipherSuiteFilter)));
                    if (!protocols.isEmpty()) {
                        List<Protocol> list = new ArrayList<>();
                        for (String protocol : protocols) {
                            Protocol forName = Protocol.forName(protocol);
                            list.add(forName);
                        }
                        builder.setProtocolSelector(ProtocolSelector.empty().add(EnumSet.copyOf(list)));
                    }
                    if (preRealmRewriter != null || postRealmRewriter != null || finalRewriter != null || realmMapper != null) {
                        MechanismConfiguration.Builder mechBuilder = MechanismConfiguration.builder();
                        if (preRealmRewriter != null)
                            mechBuilder.setPreRealmRewriter(preRealmRewriter);
                        if (postRealmRewriter != null)
                            mechBuilder.setPostRealmRewriter(postRealmRewriter);
                        if (finalRewriter != null)
                            mechBuilder.setFinalRewriter(finalRewriter);
                        if (realmMapper != null)
                            mechBuilder.setRealmMapper(realmMapper);
                        builder.setMechanismConfigurationSelector(
                                MechanismConfigurationSelector.constantSelector(mechBuilder.build()));
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
                                "ServerSSLContext supplying:  securityDomain = %s  keyManager = %s  trustManager = %s  "
                                        + "providers = %s  cipherSuiteFilter = %s  cipherSuiteNames = %s protocols = %s  wantClientAuth = %s  needClientAuth = %s  "
                                        + "authenticationOptional = %s  maximumSessionCacheSize = %s  sessionTimeout = %s wrap = %s",
                                securityDomain, keyManager, trustManager, Arrays.toString(providers), cipherSuiteFilter, cipherSuiteNames,
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
                ((SSLContextResource) resource).setSSLContextServiceController(serviceController);
            }

        };

        return createSSLContextDefinition(ElytronDescriptionConstants.SERVER_SSL_CONTEXT, true, add, attributes,
                serverOrHostController);
    }

    static ResourceDefinition getServerSNISSLContextDefinition() {

        AttributeDefinition[] attributes = new AttributeDefinition[] { DEFAULT_SSL_CONTEXT, HOST_CONTEXT_MAP };

        AbstractAddStepHandler add = new TrivialAddHandler<SSLContext>(SSLContext.class, attributes, SSL_CONTEXT_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<SSLContext> getValueSupplier(ServiceBuilder<SSLContext> serviceBuilder,
                                                                 OperationContext context, ModelNode model) throws OperationFailedException {

                final InjectedValue<SSLContext> defaultContext = new InjectedValue<>();

                ModelNode defaultContextName = DEFAULT_SSL_CONTEXT.resolveModelAttribute(context, model);
                serviceBuilder.addDependency(SSL_CONTEXT_RUNTIME_CAPABILITY.getCapabilityServiceName(defaultContextName.asString()), SSLContext.class, defaultContext);

                ModelNode hostContextMap = HOST_CONTEXT_MAP.resolveModelAttribute(context, model);

                Set<String> keys;
                if (hostContextMap.isDefined() && (keys = hostContextMap.keys()).size() > 0) {
                    final Map<String, InjectedValue<SSLContext>> sslContextMap = new HashMap<>(keys.size());
                    for (String host : keys) {
                        String sslContextName = hostContextMap.require(host).asString();
                        final InjectedValue<SSLContext> injector = new InjectedValue<>();
                        serviceBuilder.addDependency(SSL_CONTEXT_RUNTIME_CAPABILITY.getCapabilityServiceName(sslContextName), SSLContext.class, injector);
                        sslContextMap.put(host, injector);
                    }

                    return () -> {
                        SNIContextMatcher.Builder builder = new SNIContextMatcher.Builder();
                        for(Map.Entry<String, InjectedValue<SSLContext>> e : sslContextMap.entrySet()) {
                            builder.addMatch(e.getKey(), e.getValue().getValue());
                        }
                        return new SNISSLContext(builder
                                .setDefaultContext(defaultContext.getValue())
                                .build());
                    };
                } else {
                    return () -> defaultContext.getValue();
                }
            }
        };

        Builder builder = TrivialResourceDefinition.builder()
                .setPathKey(ElytronDescriptionConstants.SERVER_SSL_SNI_CONTEXT)
                .setAddHandler(add)
                .setAttributes(attributes)
                .setRuntimeCapabilities(SSL_CONTEXT_RUNTIME_CAPABILITY);
        return builder.build();
    }

    static ResourceDefinition getClientSSLContextDefinition(boolean serverOrHostController) {

        final SimpleAttributeDefinition providersDefinition = new SimpleAttributeDefinitionBuilder(PROVIDERS)
                .setCapabilityReference(PROVIDERS_CAPABILITY, SSL_CONTEXT_CAPABILITY)
                .setAllowExpression(false)
                .setRestartAllServices()
                .build();

        AttributeDefinition[] attributes = new AttributeDefinition[]{CIPHER_SUITE_FILTER, CIPHER_SUITE_NAMES, PROTOCOLS,
                KEY_MANAGER, TRUST_MANAGER, providersDefinition, PROVIDER_NAME};

        AbstractAddStepHandler add = new TrivialAddHandler<SSLContext>(SSLContext.class, attributes, SSL_CONTEXT_RUNTIME_CAPABILITY) {
            @Override
            protected ValueSupplier<SSLContext> getValueSupplier(ServiceBuilder<SSLContext> serviceBuilder, OperationContext context, ModelNode model) throws OperationFailedException {

                final InjectedValue<KeyManager> keyManagerInjector = addDependency(KEY_MANAGER_CAPABILITY, KEY_MANAGER, KeyManager.class, serviceBuilder, context, model);
                final InjectedValue<TrustManager> trustManagerInjector = addDependency(TRUST_MANAGER_CAPABILITY, TRUST_MANAGER, TrustManager.class, serviceBuilder, context, model);
                final InjectedValue<Provider[]> providersInjector = addDependency(PROVIDERS_CAPABILITY, providersDefinition, Provider[].class, serviceBuilder, context, model);

                final String providerName = PROVIDER_NAME.resolveModelAttribute(context, model).asStringOrNull();
                final List<String> protocols = PROTOCOLS.unwrap(context, model);
                final String cipherSuiteFilter = CIPHER_SUITE_FILTER.resolveModelAttribute(context, model).asString(); // has default value, can't be null
                final String cipherSuiteNames = CIPHER_SUITE_NAMES.resolveModelAttribute(context, model).asStringOrNull(); // doesn't have a default value yet since we are disabling TLS 1.3 by default
                return () -> {
                    X509ExtendedKeyManager keyManager = getX509KeyManager(keyManagerInjector.getOptionalValue());
                    X509ExtendedTrustManager trustManager = getX509TrustManager(trustManagerInjector.getOptionalValue());
                    Provider[] providers = filterProviders(providersInjector.getOptionalValue(), providerName);

                    SSLContextBuilder builder = new SSLContextBuilder();
                    if (keyManager != null) builder.setKeyManager(keyManager);
                    if (trustManager != null) builder.setTrustManager(trustManager);
                    if (providers != null) builder.setProviderSupplier(() -> providers);
                    builder.setCipherSuiteSelector(CipherSuiteSelector.aggregate(cipherSuiteNames != null ? CipherSuiteSelector.fromNamesString(cipherSuiteNames) : null, CipherSuiteSelector.fromString(cipherSuiteFilter)));
                    if (!protocols.isEmpty()) {
                        List<Protocol> list = new ArrayList<>();
                        for (String protocol : protocols) {
                            Protocol forName = Protocol.forName(protocol);
                            list.add(forName);
                        }
                        builder.setProtocolSelector(ProtocolSelector.empty().add(
                                EnumSet.copyOf(list)
                        ));
                    }
                    builder.setClientMode(true)
                            .setWrap(false);

                    if (ROOT_LOGGER.isTraceEnabled()) {
                        ROOT_LOGGER.tracef(
                                "ClientSSLContext supplying:  keyManager = %s  trustManager = %s  providers = %s  " +
                                        "cipherSuiteFilter = %s cipherSuiteNames = %s protocols = %s",
                                keyManager, trustManager, Arrays.toString(providers), cipherSuiteFilter, cipherSuiteNames,
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
                ((SSLContextResource) resource).setSSLContextServiceController(serviceController);
            }
        };

        return createSSLContextDefinition(ElytronDescriptionConstants.CLIENT_SSL_CONTEXT, false, add, attributes, serverOrHostController);
    }

    private static Provider[] filterProviders(Provider[] all, String provider) {
        if (provider == null || all == null) return all;
        List<Provider> list = new ArrayList<>();
        for (Provider current : all) {
            if (provider.equals(current.getName())) {
                list.add(current);
            }
        }
        return list.toArray(new Provider[0]);
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
                x509KeyManager = ((DelegatingKeyManager) x509KeyManager).delegating.get();
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
            X509ExtendedTrustManager x509TrustManager = (X509ExtendedTrustManager) trustManager;
            if (x509TrustManager instanceof DelegatingTrustManager && IS_FIPS.getAsBoolean()) {
                ROOT_LOGGER.trace("FIPS enabled on JVM, unwrapping TrustManager");
                x509TrustManager = ((DelegatingTrustManager)x509TrustManager).delegating.get();
            }
            return x509TrustManager;
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

                return isFips != null && isFips instanceof Boolean ? ((Boolean) isFips).booleanValue() : false;
            };

        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException e) {
            ROOT_LOGGER.trace("Unable to find com.sun.net.ssl.internal.ssl.Provider.isFIPS() method.", e);
        }

        return Boolean.FALSE::booleanValue;
    }

}
