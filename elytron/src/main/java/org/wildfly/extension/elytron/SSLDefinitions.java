/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.controller.capability.RuntimeCapability.buildDynamicCapabilityName;
import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;
import static org.wildfly.extension.elytron.Capabilities.AUTHENTICATION_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.CLIENT_SSL_CONTEXT_API_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.CLIENT_SSL_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.CLIENT_SSL_CONTEXT_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_MANAGER_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_MANAGER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_MANAGER_API_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_API_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PRINCIPAL_TRANSFORMER_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PROVIDERS_API_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PROVIDERS_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.TRUST_MANAGER_API_CAPABILITY;
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
import java.io.InputStream;
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
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
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
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.MapAttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.ObjectListAttributeDefinition;
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
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.operations.validation.StringAllowedValuesValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.extension.elytron.TrivialResourceDefinition.Builder;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;
import org.wildfly.extension.elytron._private.ElytronSubsystemMessages;
import org.wildfly.extension.elytron.capabilities.PrincipalTransformer;
import org.wildfly.security.auth.client.AuthenticationContext;
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
    private static final String ORG_WILDFLY_SECURITY_ELYTRON_DYNAMIC_SSL = "org.wildfly.security.elytron-dynamic-ssl";

    static final ServiceUtil<SSLContext> SERVER_SERVICE_UTIL = ServiceUtil.newInstance(SSL_CONTEXT_RUNTIME_CAPABILITY, ElytronDescriptionConstants.SERVER_SSL_CONTEXT, SSLContext.class);
    static final ServiceUtil<SSLContext> CLIENT_SERVICE_UTIL = ServiceUtil.newInstance(SSL_CONTEXT_RUNTIME_CAPABILITY, ElytronDescriptionConstants.CLIENT_SSL_CONTEXT, SSLContext.class);

    static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition AUTHENTICATION_CONTEXT_ATTRIBUTE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.AUTHENTICATION_CONTEXT, ModelType.STRING, false)
            .setMinSize(1)
            .setRequired(true)
            .setCapabilityReference(AUTHENTICATION_CONTEXT_CAPABILITY, SSL_CONTEXT_CAPABILITY)
            .setAllowExpression(true)
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

    private static final String[] ALLOWED_PROTOCOLS = { "SSLv2", "SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3" };

    static final StringListAttributeDefinition PROTOCOLS = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.PROTOCOLS)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRequired(false)
            .setAllowedValues(ALLOWED_PROTOCOLS)
            .setValidator(new StringAllowedValuesValidator(ALLOWED_PROTOCOLS))
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
            .setValidator(IntRangeValidator.POSITIVE)
            .setRestartAllServices()
            .build();

    @Deprecated
    static final SimpleAttributeDefinition MAXIMUM_CERT_PATH_CRL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MAXIMUM_CERT_PATH, ModelType.INT, true)
            .setAllowExpression(true)
            .setValidator(IntRangeValidator.POSITIVE)
            .setDeprecated(ModelVersion.create(8))
            .setRestartAllServices()
            .build();

    static final ObjectTypeAttributeDefinition CERTIFICATE_REVOCATION_LIST = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.CERTIFICATE_REVOCATION_LIST, PATH, RELATIVE_TO, MAXIMUM_CERT_PATH_CRL)
            .setRequired(false)
            .setRestartAllServices()
            .setAlternatives(ElytronDescriptionConstants.CERTIFICATE_REVOCATION_LISTS)
            .build();

    static final ObjectTypeAttributeDefinition CERTIFICATE_REVOCATION_LIST_NO_MAX_CERT_PATH = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.CERTIFICATE_REVOCATION_LIST, PATH, RELATIVE_TO)
            .setRequired(false)
            .setRestartAllServices()
            .build();

    static final ObjectListAttributeDefinition CERTIFICATE_REVOCATION_LISTS = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.CERTIFICATE_REVOCATION_LISTS, CERTIFICATE_REVOCATION_LIST_NO_MAX_CERT_PATH)
            .setRequired(false)
            .setRestartAllServices()
            .setAlternatives(ElytronDescriptionConstants.CERTIFICATE_REVOCATION_LIST)
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

    static final SimpleAttributeDefinition GENERATE_SELF_SIGNED_CERTIFICATE_HOST = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.GENERATE_SELF_SIGNED_CERTIFICATE_HOST, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    /*
     * Runtime Attributes
     */

    private static final SimpleAttributeDefinition ACTIVE_SESSION_COUNT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ACTIVE_SESSION_COUNT, ModelType.INT)
            .setStorageRuntime()
            .build();

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
                "([0-9a-zA-Z*.\\[\\]?^-]" + // any combination of digits, letters, asterisks, non-escaped dots, square brackets, question marks, hyphens and carets
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

        AttributeDefinition[] attributes = new AttributeDefinition[]{ALGORITHM, providersDefinition, PROVIDER_NAME, keystoreDefinition, ALIAS_FILTER, credentialReferenceDefinition, GENERATE_SELF_SIGNED_CERTIFICATE_HOST};

        DoohickeyAddHandler<KeyManager> add = new DoohickeyAddHandler<KeyManager>(KEY_MANAGER_RUNTIME_CAPABILITY, KEY_MANAGER_API_CAPABILITY) {

            @Override
            protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {
                super.populateModel(context, operation, resource);
                handleCredentialReferenceUpdate(context, resource.getModel());
            }

            @Override
            protected ElytronDoohickey<KeyManager> createDoohickey(PathAddress resourceAddress) {
                return new ElytronDoohickey<KeyManager>(resourceAddress) {

                    private volatile String algorithmName;
                    private volatile String providerName;
                    private volatile String providerLoader;
                    private volatile String keyStoreName;
                    private volatile String aliasFilter;
                    private volatile String generateSelfSignedCertificateHost;
                    private final DelegatingKeyManager delegatingKeyManager = new DelegatingKeyManager();

                    @Override
                    protected void resolveRuntime(ModelNode model, OperationContext context) throws OperationFailedException {
                        algorithmName = ALGORITHM.resolveModelAttribute(context, model).asStringOrNull();
                        providerName = PROVIDER_NAME.resolveModelAttribute(context, model).asStringOrNull();
                        providerLoader = providersDefinition.resolveModelAttribute(context, model).asStringOrNull();
                        keyStoreName = keystoreDefinition.resolveModelAttribute(context, model).asStringOrNull();
                        aliasFilter = ALIAS_FILTER.resolveModelAttribute(context, model).asStringOrNull();
                        generateSelfSignedCertificateHost = GENERATE_SELF_SIGNED_CERTIFICATE_HOST.resolveModelAttribute(context, model).asStringOrNull();
                    }

                    @Override
                    protected ExceptionSupplier<KeyManager, StartException> prepareServiceSupplier(OperationContext context,
                            CapabilityServiceBuilder<?> serviceBuilder) throws OperationFailedException {
                        final InjectedValue<Provider[]> providersInjector = new InjectedValue<>();
                        if (providerLoader != null) {
                            serviceBuilder.addDependency(context.getCapabilityServiceName(
                                    buildDynamicCapabilityName(PROVIDERS_CAPABILITY, providerLoader), Provider[].class),
                                    Provider[].class, providersInjector);
                        }
                        final InjectedValue<KeyStore> keyStoreInjector = new InjectedValue<>();
                        if (keyStoreName != null) {
                            serviceBuilder.addDependency(context.getCapabilityServiceName(
                                    buildDynamicCapabilityName(KEY_STORE_CAPABILITY, keyStoreName), KeyStore.class),
                                    KeyStore.class, keyStoreInjector);
                        }
                        final String algorithm = algorithmName != null ? algorithmName : KeyManagerFactory.getDefaultAlgorithm();
                        final ModifiableKeyStoreService keyStoreService = getModifiableKeyStoreService(context, keyStoreName);
                        ModelNode resourceModel = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
                        @SuppressWarnings("unchecked")
                        ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier =
                                CredentialReference.getCredentialSourceSupplier(context, credentialReferenceDefinition, resourceModel, serviceBuilder);
                        return () -> {
                            KeyStoreService plainKeyStoreService = keyStoreService instanceof KeyStoreService
                                    ? (KeyStoreService) keyStoreService : null;
                            boolean generateSelfSigned = plainKeyStoreService != null
                                    && plainKeyStoreService.shouldAutoGenerateSelfSignedCertificate(generateSelfSignedCertificateHost);
                            return buildKeyManager(providersInjector.getOptionalValue(),
                                    keyStoreInjector.getOptionalValue(), credentialSourceSupplier, algorithm,
                                    generateSelfSigned, () -> plainKeyStoreService, null);
                        };
                    }

                    @Override
                    protected KeyManager createImmediately(OperationContext foreignContext) throws OperationFailedException {
                        Provider[] providers = null;
                        if (providerLoader != null) {
                            @SuppressWarnings("unchecked")
                            ExceptionFunction<OperationContext, Provider[], OperationFailedException> providerApi =
                                    foreignContext.getCapabilityRuntimeAPI(PROVIDERS_API_CAPABILITY, providerLoader, ExceptionFunction.class);
                            providers = providerApi.apply(foreignContext);
                        }

                        KeyStore keyStore = null;
                        KeyStoreDefinition.KeyStoreDoohickey keyStoreDoohickey = null;
                        if (keyStoreName != null) {
                            @SuppressWarnings("unchecked")
                            ExceptionFunction<OperationContext, KeyStore, OperationFailedException> ksApi =
                                    foreignContext.getCapabilityRuntimeAPI(KEY_STORE_API_CAPABILITY, keyStoreName, ExceptionFunction.class);
                            keyStore = ksApi.apply(foreignContext);
                            if (ksApi instanceof KeyStoreDefinition.KeyStoreDoohickey) {
                                keyStoreDoohickey = (KeyStoreDefinition.KeyStoreDoohickey) ksApi;
                            }
                        }

                        final String algorithm = algorithmName != null ? algorithmName : KeyManagerFactory.getDefaultAlgorithm();

                        // F1+F2: read the key-manager's own model (not the caller's model) and resolve the
                        // credential source through the credential-store-api early path rather than the
                        // MSC service registry, which may not have started yet.
                        CredentialSource credentialSource = CredentialReference.getCredentialSource(foreignContext,
                                credentialReferenceDefinition,
                                foreignContext.readResourceFromRoot(resourceAddress).getModel());
                        ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier = () -> credentialSource;

                        final KeyStoreDefinition.KeyStoreDoohickey plainKeyStoreDoohickey = keyStoreDoohickey;
                        boolean generateSelfSigned = plainKeyStoreDoohickey != null
                                && plainKeyStoreDoohickey.shouldAutoGenerateSelfSignedCertificate(generateSelfSignedCertificateHost);
                        try {
                            KeyManager km = buildKeyManager(providers, keyStore, credentialSourceSupplier,
                                    algorithm, generateSelfSigned,
                                    plainKeyStoreDoohickey != null ? plainKeyStoreDoohickey::getKeyStoreService : null,
                                    plainKeyStoreDoohickey);
                            setValue(km);
                            return km;
                        } catch (StartException e) {
                            throw new OperationFailedException(e);
                        }
                    }

                    private KeyManager buildKeyManager(Provider[] providers, KeyStore keyStore,
                            ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier,
                            String algorithm, boolean generateSelfSigned,
                            Supplier<KeyStoreService> keyStoreServiceSupplier,
                            KeyStoreDefinition.KeyStoreDoohickey keyStoreDoohickey) throws StartException {
                        KeyManagerFactory keyManagerFactory = createKeyManagerFactory(providers, providerName, algorithm);

                        char[] password;
                        try {
                            CredentialSource cs = credentialSourceSupplier.get();
                            if (cs != null) {
                                password = cs.getCredential(PasswordCredential.class).getPassword(ClearPassword.class).getPassword();
                            } else {
                                throw new StartException(ROOT_LOGGER.keyStorePasswordCannotBeResolved(keyStoreName));
                            }
                            if (ROOT_LOGGER.isTraceEnabled()) {
                                ROOT_LOGGER.tracef(
                                        "KeyManager supplying:  providers = %s  provider = %s  algorithm = %s  keyManagerFactory = %s  " +
                                                "keyStoreName = %s  aliasFilter = %s  keyStore = %s  keyStoreSize = %d  password (of item) = %b",
                                        Arrays.toString(providers), providerName, algorithm, keyManagerFactory,
                                        keyStoreName, aliasFilter, keyStore, keyStore != null ? keyStore.size() : 0, password != null);
                            }
                        } catch (StartException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new StartException(e);
                        }

                        if (generateSelfSigned) {
                            return new LazyDelegatingKeyManager(keyStoreServiceSupplier, keyStoreDoohickey, password, keyManagerFactory,
                                    generateSelfSignedCertificateHost, aliasFilter);
                        } else {
                            try {
                                if (initKeyManagerFactory(keyStore, delegatingKeyManager, aliasFilter, password, keyManagerFactory)) {
                                    return delegatingKeyManager;
                                }
                            } catch (Exception e) {
                                throw new StartException(e);
                            }
                            throw ROOT_LOGGER.noTypeFound(X509ExtendedKeyManager.class.getSimpleName());
                        }
                    }

                    private KeyManagerFactory createKeyManagerFactory(Provider[] providers, String provName, String algorithm) throws StartException {
                        if (providers != null) {
                            for (Provider current : providers) {
                                if (provName == null || provName.equals(current.getName())) {
                                    try {
                                        // TODO - We could check the Services within each Provider to check there is one of the required type/algorithm
                                        // However the same loop would need to remain as it is still possible a specific provider can't create it.
                                        return KeyManagerFactory.getInstance(algorithm, current);
                                    } catch (NoSuchAlgorithmException ignored) {
                                    }
                                }
                            }
                            throw ROOT_LOGGER.unableToCreateManagerFactory(KeyManagerFactory.class.getSimpleName(), algorithm);
                        }
                        try {
                            return KeyManagerFactory.getInstance(algorithm);
                        } catch (NoSuchAlgorithmException e) {
                            throw new StartException(e);
                        }
                    }
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
                .setRemoveHandler(add.createRemoveHandler(KEY_MANAGER_RUNTIME_CAPABILITY))
                .setAttributes(attributes)
                .setRuntimeCapabilities(KEY_MANAGER_RUNTIME_CAPABILITY)
                .addOperation(new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.INIT, RESOURCE_RESOLVER)
                        .setRuntimeOnly()
                        .build(), init(KEY_MANAGER_UTIL, KEY_MANAGER_API_CAPABILITY))
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

        AttributeDefinition[] attributes = new AttributeDefinition[]{ALGORITHM, providersDefinition, PROVIDER_NAME, keystoreDefinition, ALIAS_FILTER, CERTIFICATE_REVOCATION_LIST, CERTIFICATE_REVOCATION_LISTS, OCSP, SOFT_FAIL, ONLY_LEAF_CERT, MAXIMUM_CERT_PATH};

        DoohickeyAddHandler<TrustManager> add = new DoohickeyAddHandler<TrustManager>(TRUST_MANAGER_RUNTIME_CAPABILITY, TRUST_MANAGER_API_CAPABILITY) {

            @Override
            protected ElytronDoohickey<TrustManager> createDoohickey(PathAddress resourceAddress) {
                return new ElytronDoohickey<TrustManager>(resourceAddress) {

                    // Resolved model attributes
                    private volatile String algorithmName;
                    private volatile String providerName;
                    private volatile String providerLoader;
                    private volatile String keyStoreName;
                    private volatile String aliasFilter;
                    private volatile ModelNode crlNode;
                    private volatile ModelNode ocspNode;
                    private volatile ModelNode multipleCrlsNode;
                    private volatile boolean hasRevocation;
                    // Revocation fields
                    private volatile boolean softFail;
                    private volatile boolean onlyLeafCert;
                    private volatile Integer maxCertPath;
                    private volatile String responderStr;
                    private volatile String responderCertAlias;
                    private volatile String responderKeystore;
                    private volatile boolean preferCrls;
                    // CRL file info — resolved at construction time, stored for both service and early paths
                    private volatile List<CrlFile> crlFiles;
                    private final DelegatingTrustManager delegatingTrustManager = new DelegatingTrustManager();

                    @Override
                    protected void resolveRuntime(ModelNode model, OperationContext context) throws OperationFailedException {
                        algorithmName = ALGORITHM.resolveModelAttribute(context, model).asStringOrNull();
                        providerName = PROVIDER_NAME.resolveModelAttribute(context, model).asStringOrNull();
                        providerLoader = providersDefinition.resolveModelAttribute(context, model).asStringOrNull();
                        keyStoreName = keystoreDefinition.resolveModelAttribute(context, model).asStringOrNull();
                        aliasFilter = ALIAS_FILTER.resolveModelAttribute(context, model).asStringOrNull();
                        hasRevocation = model.hasDefined(CERTIFICATE_REVOCATION_LIST.getName())
                                || model.hasDefined(OCSP.getName())
                                || model.hasDefined(CERTIFICATE_REVOCATION_LISTS.getName());
                        if (hasRevocation) {
                            crlNode = CERTIFICATE_REVOCATION_LIST.resolveModelAttribute(context, model);
                            ocspNode = OCSP.resolveModelAttribute(context, model);
                            multipleCrlsNode = CERTIFICATE_REVOCATION_LISTS.resolveModelAttribute(context, model);
                            softFail = SOFT_FAIL.resolveModelAttribute(context, model).asBoolean();
                            onlyLeafCert = ONLY_LEAF_CERT.resolveModelAttribute(context, model).asBoolean();
                            maxCertPath = MAXIMUM_CERT_PATH.resolveModelAttribute(context, model).asIntOrNull();
                            @SuppressWarnings("deprecation")
                            Integer crlCertPath = MAXIMUM_CERT_PATH_CRL.resolveModelAttribute(context, crlNode).asIntOrNull();
                            if (crlCertPath != null) {
                                ROOT_LOGGER.legacyMaximumCertPathInCrl();
                                if (maxCertPath != null) throw ROOT_LOGGER.multipleMaximumCertPathDefinitions();
                                maxCertPath = crlCertPath;
                            }
                            responderStr = RESPONDER.resolveModelAttribute(context, ocspNode).asStringOrNull();
                            responderCertAlias = RESPONDER_CERTIFICATE.resolveModelAttribute(context, ocspNode).asStringOrNull();
                            responderKeystore = RESPONDER_KEYSTORE.resolveModelAttribute(context, ocspNode).asStringOrNull();
                            preferCrls = PREFER_CRLS.resolveModelAttribute(context, ocspNode).asBoolean(false);
                        }
                    }

                    @Override
                    protected ExceptionSupplier<TrustManager, StartException> prepareServiceSupplier(OperationContext context,
                            CapabilityServiceBuilder<?> serviceBuilder) throws OperationFailedException {
                        final InjectedValue<Provider[]> providersInjector = new InjectedValue<>();
                        if (providerLoader != null) {
                            serviceBuilder.addDependency(context.getCapabilityServiceName(
                                    buildDynamicCapabilityName(PROVIDERS_CAPABILITY, providerLoader), Provider[].class),
                                    Provider[].class, providersInjector);
                        }
                        final InjectedValue<KeyStore> keyStoreInjector = new InjectedValue<>();
                        if (keyStoreName != null) {
                            serviceBuilder.addDependency(context.getCapabilityServiceName(
                                    buildDynamicCapabilityName(KEY_STORE_CAPABILITY, keyStoreName), KeyStore.class),
                                    KeyStore.class, keyStoreInjector);
                        }
                        final String algorithm = algorithmName != null ? algorithmName : TrustManagerFactory.getDefaultAlgorithm();

                        // CRL path manager wiring
                        final List<CrlFile> serviceCrlFiles = new ArrayList<>();
                        if (hasRevocation) {
                            InjectedValue<PathManager> pathManagerInjector = new InjectedValue<>();
                            if (crlNode.isDefined()) {
                                String crlPath = PATH.resolveModelAttribute(context, crlNode).asStringOrNull();
                                String crlRelativeTo = RELATIVE_TO.resolveModelAttribute(context, crlNode).asStringOrNull();
                                if (crlPath != null) {
                                    if (crlRelativeTo != null) {
                                        serviceBuilder.addDependency(PathManagerService.SERVICE_NAME, PathManager.class, pathManagerInjector);
                                        serviceBuilder.requires(pathName(crlRelativeTo));
                                    }
                                    serviceCrlFiles.add(new CrlFile(crlPath, crlRelativeTo, pathManagerInjector));
                                }
                            } else if (multipleCrlsNode.isDefined()) {
                                for (ModelNode crl : multipleCrlsNode.asList()) {
                                    String crlPath = PATH.resolveModelAttribute(context, crl).asStringOrNull();
                                    String crlRelativeTo = RELATIVE_TO.resolveModelAttribute(context, crl).asStringOrNull();
                                    pathManagerInjector = new InjectedValue<>();
                                    if (crlPath != null) {
                                        if (crlRelativeTo != null) {
                                            serviceBuilder.addDependency(PathManagerService.SERVICE_NAME, PathManager.class, pathManagerInjector);
                                            serviceBuilder.requires(pathName(crlRelativeTo));
                                        }
                                        serviceCrlFiles.add(new CrlFile(crlPath, crlRelativeTo, pathManagerInjector));
                                    }
                                }
                            }
                        }

                        // Responder key-store wiring
                        final InjectedValue<KeyStore> responderStoreInjector = (responderKeystore != null) ? new InjectedValue<>() : keyStoreInjector;
                        if (responderKeystore != null) {
                            serviceBuilder.addDependency(context.getCapabilityServiceName(
                                    buildDynamicCapabilityName(KEY_STORE_CAPABILITY, responderKeystore), KeyStore.class),
                                    KeyStore.class, responderStoreInjector);
                        }

                        return () -> buildTrustManager(
                                providersInjector.getOptionalValue(),
                                keyStoreInjector.getOptionalValue(),
                                responderStoreInjector.getOptionalValue(),
                                algorithm,
                                serviceCrlFiles,
                                null /* PathManager resolved via CrlFile injectors */,
                                delegatingTrustManager);
                    }

                    @Override
                    protected TrustManager createImmediately(OperationContext foreignContext) throws OperationFailedException {
                        // Resolve providers via providers-api.
                        Provider[] providers = null;
                        if (providerLoader != null) {
                            @SuppressWarnings("unchecked")
                            ExceptionFunction<OperationContext, Provider[], OperationFailedException> providerApi =
                                    foreignContext.getCapabilityRuntimeAPI(PROVIDERS_API_CAPABILITY, providerLoader, ExceptionFunction.class);
                            providers = providerApi.apply(foreignContext);
                        }

                        // Resolve key-store via key-store-api.
                        KeyStore keyStore = null;
                        if (keyStoreName != null) {
                            @SuppressWarnings("unchecked")
                            ExceptionFunction<OperationContext, KeyStore, OperationFailedException> ksApi =
                                    foreignContext.getCapabilityRuntimeAPI(KEY_STORE_API_CAPABILITY, keyStoreName, ExceptionFunction.class);
                            keyStore = ksApi.apply(foreignContext);
                        }

                        // Resolve responder key-store via key-store-api.
                        KeyStore responderStore = keyStore;
                        if (responderKeystore != null) {
                            @SuppressWarnings("unchecked")
                            ExceptionFunction<OperationContext, KeyStore, OperationFailedException> ksApi =
                                    foreignContext.getCapabilityRuntimeAPI(KEY_STORE_API_CAPABILITY, responderKeystore, ExceptionFunction.class);
                            responderStore = ksApi.apply(foreignContext);
                        }

                        final String algorithm = algorithmName != null ? algorithmName : TrustManagerFactory.getDefaultAlgorithm();

                        // Match the service path: PathManager is needed only for a CRL with relative-to.
                        List<CrlFile> resolvedCrlFiles = new ArrayList<>();
                        if (hasRevocation && crlNode != null) {
                            PathManager pathManager = null;
                            if (crlNode.isDefined()) {
                                String crlPath = PATH.resolveModelAttribute(foreignContext, crlNode).asStringOrNull();
                                String crlRelativeTo = RELATIVE_TO.resolveModelAttribute(foreignContext, crlNode).asStringOrNull();
                                if (crlPath != null) {
                                    InjectedValue<PathManager> pmInjector = new InjectedValue<>();
                                    if (crlRelativeTo != null) {
                                        pathManager = (PathManager) foreignContext.getServiceRegistry(false)
                                                .getRequiredService(PathManagerService.SERVICE_NAME).getValue();
                                        pmInjector.inject(pathManager);
                                    }
                                    resolvedCrlFiles.add(new CrlFile(crlPath, crlRelativeTo, pmInjector));
                                }
                            } else if (multipleCrlsNode != null && multipleCrlsNode.isDefined()) {
                                for (ModelNode crl : multipleCrlsNode.asList()) {
                                    String crlPath = PATH.resolveModelAttribute(foreignContext, crl).asStringOrNull();
                                    String crlRelativeTo = RELATIVE_TO.resolveModelAttribute(foreignContext, crl).asStringOrNull();
                                    if (crlPath != null) {
                                        InjectedValue<PathManager> pmInjector = new InjectedValue<>();
                                        if (crlRelativeTo != null) {
                                            if (pathManager == null) {
                                                pathManager = (PathManager) foreignContext.getServiceRegistry(false)
                                                        .getRequiredService(PathManagerService.SERVICE_NAME).getValue();
                                            }
                                            pmInjector.inject(pathManager);
                                        }
                                        resolvedCrlFiles.add(new CrlFile(crlPath, crlRelativeTo, pmInjector));
                                    }
                                }
                            }
                        }

                        try {
                            TrustManager tm = buildTrustManager(providers, keyStore, responderStore, algorithm,
                                    resolvedCrlFiles, null, delegatingTrustManager);
                            setValue(tm);
                            return tm;
                        } catch (StartException e) {
                            throw new OperationFailedException(e);
                        }
                    }

                    /**
                     * Shared construction logic used by both the service path (via {@link #prepareServiceSupplier}) and
                     * the early-access path (via {@link #createImmediately}).
                     */
                    private TrustManager buildTrustManager(Provider[] providers, KeyStore keyStore, KeyStore responderStore,
                            String algorithm, List<CrlFile> crlFileList, PathManager unusedPathManager,
                            DelegatingTrustManager preAllocatedDelegate) throws StartException {

                        TrustManagerFactory trustManagerFactory = createTrustManagerFactory(providers, providerName, algorithm);

                        KeyStore filteredKeyStore = keyStore;
                        try {
                            if (aliasFilter != null && filteredKeyStore != null) {
                                filteredKeyStore = FilteringKeyStore.filteringKeyStore(filteredKeyStore, AliasFilter.fromString(aliasFilter));
                            }
                        } catch (Exception e) {
                            throw new StartException(e);
                        }

                        if (!hasRevocation) {
                            // Simple (non-revocation) path — reuse pre-allocated delegate if provided (for init/reload identity)
                            DelegatingTrustManager delegatingTrustManager = preAllocatedDelegate != null ? preAllocatedDelegate : new DelegatingTrustManager();
                            try {
                                if (ROOT_LOGGER.isTraceEnabled()) {
                                    ROOT_LOGGER.tracef(
                                            "TrustManager supplying:  providers = %s  provider = %s  algorithm = %s  trustManagerFactory = %s  keyStoreName = %s  keyStore = %s  aliasFilter = %s  keyStoreSize = %d",
                                            Arrays.toString(providers), providerName, algorithm, trustManagerFactory, keyStoreName, filteredKeyStore, aliasFilter, filteredKeyStore != null ? filteredKeyStore.size() : 0);
                                }
                                // Preserve the existing service behavior for the simple trust manager:
                                // alias-filter was parsed, but the factory received the original store.
                                trustManagerFactory.init(keyStore);
                            } catch (Exception e) {
                                throw new StartException(e);
                            }
                            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
                            for (TrustManager tm : trustManagers) {
                                if (tm instanceof X509ExtendedTrustManager) {
                                    delegatingTrustManager.setTrustManager((X509ExtendedTrustManager) tm);
                                    return delegatingTrustManager;
                                }
                            }
                            throw ROOT_LOGGER.noTypeFound(X509ExtendedKeyManager.class.getSimpleName());
                        }

                        // Revocation path
                        URI responderUri;
                        try {
                            responderUri = responderStr == null ? null : new URI(responderStr);
                        } catch (Exception e) {
                            throw new StartException(e);
                        }

                        X509RevocationTrustManager.Builder builder = X509RevocationTrustManager.builder();
                        builder.setResponderURI(responderUri);
                        builder.setSoftFail(softFail);
                        builder.setOnlyEndEntity(onlyLeafCert);
                        if (maxCertPath != null) builder.setMaxCertPath(maxCertPath.intValue());

                        boolean hasCrl = (crlNode != null && crlNode.isDefined()) || (multipleCrlsNode != null && multipleCrlsNode.isDefined());
                        boolean hasOcsp = ocspNode != null && ocspNode.isDefined();
                        if (hasCrl && !hasOcsp) {
                            builder.setPreferCrls(true);
                            builder.setNoFallback(true);
                        }
                        if (hasOcsp) {
                            builder.setResponderURI(responderUri);
                            if (!hasCrl) {
                                builder.setPreferCrls(false);
                                builder.setNoFallback(true);
                            } else {
                                builder.setPreferCrls(preferCrls);
                            }
                        }

                        if (responderCertAlias != null && responderStore != null) {
                            try {
                                builder.setOcspResponderCert((X509Certificate) responderStore.getCertificate(responderCertAlias));
                            } catch (KeyStoreException e) {
                                throw ElytronSubsystemMessages.ROOT_LOGGER.failedToLoadResponderCert(responderCertAlias, e);
                            }
                        }

                        builder.setTrustStore(filteredKeyStore);
                        builder.setTrustManagerFactory(trustManagerFactory);

                        if (!crlFileList.isEmpty()) {
                            List<InputStream> crlStreams = getCrlStreams(crlFileList);
                            builder.setCrlStreams(crlStreams);
                            return createReloadableX509CRLTrustManager(crlFileList, builder);
                        }
                        return builder.build();
                    }

                    private List<InputStream> getCrlStreams(List<CrlFile> crlFiles) throws StartException {
                        List<InputStream> crlStreams = new ArrayList<>();
                        for (CrlFile crl : crlFiles) {
                            try {
                                crlStreams.add(new FileInputStream(resolveFileLocation(crl.getCrlPath(), crl.getRelativeTo(), crl.getPathManagerInjector())));
                            } catch (FileNotFoundException e) {
                                throw ROOT_LOGGER.unableToAccessCRL(e);
                            }
                        }
                        return crlStreams;
                    }

                    private TrustManager createReloadableX509CRLTrustManager(final List<CrlFile> crlFiles, final X509RevocationTrustManager.Builder builder) {
                        return new ReloadableX509ExtendedTrustManager() {
                            private volatile X509ExtendedTrustManager delegate = builder.build();
                            private AtomicBoolean reloading = new AtomicBoolean();

                            @Override
                            void reload() {
                                if (reloading.compareAndSet(false, true)) {
                                    try {
                                        builder.setCrlStreams(getCrlStreams(crlFiles));
                                        delegate = builder.build();
                                    } catch (StartException cause) {
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
                        if (relativeTo != null) {
                            return new File(pathManagerInjector.getValue().resolveRelativePathEntry(path, relativeTo));
                        } else {
                            return new File(path);
                        }
                    }

                    private TrustManagerFactory createTrustManagerFactory(Provider[] providers, String provName, String algorithm) throws StartException {
                        if (providers != null) {
                            for (Provider current : providers) {
                                if (provName == null || provName.equals(current.getName())) {
                                    try {
                                        return TrustManagerFactory.getInstance(algorithm, current);
                                    } catch (NoSuchAlgorithmException ignored) {
                                    }
                                }
                            }
                            throw ROOT_LOGGER.unableToCreateManagerFactory(TrustManagerFactory.class.getSimpleName(), algorithm);
                        }
                        try {
                            return TrustManagerFactory.getInstance(algorithm);
                        } catch (NoSuchAlgorithmException e) {
                            throw new StartException(e);
                        }
                    }
                };
            }
        };

        ResourceDescriptionResolver resolver = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.TRUST_MANAGER);
        final ServiceUtil<TrustManager> TRUST_MANAGER_UTIL = ServiceUtil.newInstance(TRUST_MANAGER_RUNTIME_CAPABILITY, ElytronDescriptionConstants.TRUST_MANAGER, TrustManager.class);
        return TrivialResourceDefinition.builder()
                .setPathKey(ElytronDescriptionConstants.TRUST_MANAGER)
                .setResourceDescriptionResolver(resolver)
                .setAddHandler(add)
                .setRemoveHandler(add.createRemoveHandler(TRUST_MANAGER_RUNTIME_CAPABILITY))
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
                        .build(), init(TRUST_MANAGER_UTIL, TRUST_MANAGER_API_CAPABILITY))
                .build();
    }

    private static OperationStepHandler init(ServiceUtil<?> managerUtil) {
        return init(managerUtil, null);
    }

    private static OperationStepHandler init(ServiceUtil<?> managerUtil, String apiCapabilityName) {
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
                    } else {
                        throw ROOT_LOGGER.invalidServiceNameParent(serviceName.getParent().getCanonicalName());
                    }
                    // If backed by a doohickey, reset its cached value so stop+start rebuilds fresh.
                    if (apiCapabilityName != null) {
                        @SuppressWarnings("unchecked")
                        ElytronDoohickey<?> doohickey = (ElytronDoohickey<?>) context.getCapabilityRuntimeAPI(
                                apiCapabilityName, context.getCurrentAddressValue(), ExceptionFunction.class);
                        doohickey.reset();
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

    static class LazyDelegatingKeyManager extends DelegatingKeyManager {
        private final Supplier<KeyStoreService> keyStoreServiceSupplier;
        private final KeyStoreDefinition.KeyStoreDoohickey keyStoreDoohickey;
        private final char[] password;
        private final KeyManagerFactory keyManagerFactory;
        private final String generateSelfSignedCertificateHostName;
        private final String aliasFilter;
        private volatile boolean init = false;

        LazyDelegatingKeyManager(Supplier<KeyStoreService> keyStoreServiceSupplier,
                                         KeyStoreDefinition.KeyStoreDoohickey keyStoreDoohickey,
                                         char[] password, KeyManagerFactory keyManagerFactory,
                                         String generateSelfSignedCertificateHostName, String aliasFilter) {
            this.keyStoreServiceSupplier = keyStoreServiceSupplier;
            this.keyStoreDoohickey = keyStoreDoohickey;
            this.password = password;
            this.keyManagerFactory = keyManagerFactory;
            this.generateSelfSignedCertificateHostName = generateSelfSignedCertificateHostName;
            this.aliasFilter = aliasFilter;
        }

        private void doInit() {
            if (!init) {
                try {
                    DoohickeySimultaneity.withLockForLifecycle(() -> {
                        if (init) {
                            return null;
                        }
                        KeyStoreService ks = keyStoreServiceSupplier.get();
                        KeyStore keyStore;
                        if (ks != null && ks.getValue() != null) {
                            if (ks.shouldAutoGenerateSelfSignedCertificate(generateSelfSignedCertificateHostName)) {
                                ROOT_LOGGER.selfSignedCertificateWillBeCreated(
                                        ks.getResolvedAbsolutePath(), generateSelfSignedCertificateHostName);
                            }
                            ks.generateAndSaveSelfSignedCertificate(generateSelfSignedCertificateHostName, password);
                            keyStore = ks.getValue();
                        } else if (keyStoreDoohickey != null) {
                            if (keyStoreDoohickey.shouldAutoGenerateSelfSignedCertificate(generateSelfSignedCertificateHostName)) {
                                ROOT_LOGGER.selfSignedCertificateWillBeCreated(
                                        keyStoreDoohickey.getCachedResolvedAbsolutePath(), generateSelfSignedCertificateHostName);
                            }
                            keyStoreDoohickey.generateAndSaveSelfSignedCertificate(generateSelfSignedCertificateHostName, password);
                            keyStore = keyStoreDoohickey.cachedValue();
                        } else {
                            throw new IllegalStateException("Key-store value is not available for self-signed certificate generation");
                        }
                        if (keyStore == null) {
                            throw new IllegalStateException("Key-store value is no longer available for self-signed certificate generation");
                        }
                        if (!initKeyManagerFactory(keyStore, this, aliasFilter, password, keyManagerFactory)) {
                            throw ROOT_LOGGER.noTypeFoundForLazyInitKeyManager(X509ExtendedKeyManager.class.getSimpleName());
                        }
                        init = true;
                        return null;
                    });
                } catch (Exception e) {
                    throw ROOT_LOGGER.failedToLazilyInitKeyManager(e);
                }
            }
        }

        @Override
        public String[] getClientAliases(String s, Principal[] principals) {
            doInit();
            return super.getClientAliases(s, principals);
        }

        @Override
        public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
            doInit();
            return super.chooseClientAlias(strings, principals, socket);
        }

        @Override
        public String[] getServerAliases(String s, Principal[] principals) {
            doInit();
            return super.getServerAliases(s, principals);
        }

        @Override
        public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
            doInit();
            return super.chooseServerAlias(s, principals, socket);
        }

        @Override
        public X509Certificate[] getCertificateChain(String s) {
            doInit();
            return super.getCertificateChain(s);
        }

        @Override
        public PrivateKey getPrivateKey(String s) {
            doInit();
            return super.getPrivateKey(s);
        }

        @Override
        public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
            doInit();
            return super.chooseEngineClientAlias(keyType, issuers, engine);
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            doInit();
            return super.chooseEngineServerAlias(keyType, issuers, engine);
        }

    }

    static boolean initKeyManagerFactory(KeyStore keyStore, DelegatingKeyManager delegating, String aliasFilter,
                                         char[] password, KeyManagerFactory keyManagerFactory) throws Exception {
        if (aliasFilter != null) {
            keyStore = FilteringKeyStore.filteringKeyStore(keyStore, AliasFilter.fromString(aliasFilter));
        }
        keyManagerFactory.init(keyStore, password);
        KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();
        boolean keyManagerTypeFound = false;
        for (KeyManager keyManager : keyManagers) {
            if (keyManager instanceof X509ExtendedKeyManager) {
                delegating.setKeyManager((X509ExtendedKeyManager) keyManager);
                keyManagerTypeFound = true;
                break;
            }
        }
        return keyManagerTypeFound;
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
        return createSSLContextDefinition(pathKey, server, addHandler, null, attributes, serverOrHostController, Stability.DEFAULT);
    }

    private static ResourceDefinition createSSLContextDefinition(String pathKey, boolean server, AbstractAddStepHandler addHandler, AbstractRemoveStepHandler removeHandler, AttributeDefinition[] attributes, boolean serverOrHostController) {
        return createSSLContextDefinition(pathKey, server, addHandler, removeHandler, attributes, serverOrHostController, Stability.DEFAULT);
    }

    private static ResourceDefinition createSSLContextDefinition(String pathKey, boolean server, AbstractAddStepHandler addHandler, AttributeDefinition[] attributes, boolean serverOrHostController, Stability stability) {
        return createSSLContextDefinition(pathKey, server, addHandler, null, attributes, serverOrHostController, stability, null);
    }

    private static ResourceDefinition createSSLContextDefinition(String pathKey, boolean server, AbstractAddStepHandler addHandler, AbstractRemoveStepHandler removeHandler, AttributeDefinition[] attributes, boolean serverOrHostController, Stability stability) {
        return createSSLContextDefinition(pathKey, server, addHandler, removeHandler, attributes, serverOrHostController, stability, null);
    }

    private static ResourceDefinition createSSLContextDefinition(String pathKey, boolean server, AbstractAddStepHandler addHandler, AttributeDefinition[] attributes, boolean serverOrHostController, Stability stability, String dependencyPackageName) {
        return createSSLContextDefinition(pathKey, server, addHandler, null, attributes, serverOrHostController, stability, dependencyPackageName);
    }

    private static ResourceDefinition createSSLContextDefinition(String pathKey, boolean server, AbstractAddStepHandler addHandler, AbstractRemoveStepHandler removeHandler, AttributeDefinition[] attributes, boolean serverOrHostController, Stability stability, String dependencyPackageName) {

        Builder builder = TrivialResourceDefinition.builder()
                .setPathKey(pathKey)
                .setAddHandler(addHandler)
                .setAttributes(attributes)
                .setRuntimeCapabilities(SSL_CONTEXT_RUNTIME_CAPABILITY)
                .setStability(stability)
                .setDependencyPackageName(dependencyPackageName);

        if (removeHandler != null) {
            builder.setRemoveHandler(removeHandler);
        }

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

        AbstractAddStepHandler add = new TrivialAddHandler<SSLContext>(SSLContext.class, ServiceController.Mode.ACTIVE, ServiceController.Mode.PASSIVE, SSL_CONTEXT_RUNTIME_CAPABILITY) {

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
                final String cipherSuiteFilter = CIPHER_SUITE_FILTER.resolveModelAttribute(context, model).asString();
                final String cipherSuiteNames = CIPHER_SUITE_NAMES.resolveModelAttribute(context, model).asStringOrNull();
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

        AbstractAddStepHandler add = new TrivialAddHandler<SSLContext>(SSLContext.class, SSL_CONTEXT_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<SSLContext> getValueSupplier(ServiceBuilder<SSLContext> serviceBuilder,
                                                                 OperationContext context, ModelNode model) throws OperationFailedException {

                final InjectedValue<SSLContext> defaultContext = new InjectedValue<>();

                ModelNode defaultContextName = DEFAULT_SSL_CONTEXT.resolveModelAttribute(context, model);
                serviceBuilder.addDependency(SSL_CONTEXT_RUNTIME_CAPABILITY.getCapabilityServiceName(defaultContextName.asString()), SSLContext.class, defaultContext);

                ModelNode hostContextMap = HOST_CONTEXT_MAP.resolveModelAttribute(context, model);

                Set<String> keys;
                if (hostContextMap.isDefined() && !(keys = hostContextMap.keys()).isEmpty()) {
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

        DoohickeyAddHandler<SSLContext> add = new DoohickeyAddHandler<SSLContext>(SSL_CONTEXT_RUNTIME_CAPABILITY, CLIENT_SSL_CONTEXT_API_CAPABILITY) {

            @Override
            protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource)
                    throws OperationFailedException {
                super.recordCapabilitiesAndRequirements(context, operation, resource);
                if (requiresRuntime(context)) {
                    context.registerCapability(CLIENT_SSL_CONTEXT_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue()));
                }
            }

            @Override
            protected ServiceName[] getAdditionalServiceAliases(OperationContext context) {
                return new ServiceName[] {
                    CLIENT_SSL_CONTEXT_RUNTIME_CAPABILITY.getCapabilityServiceName(context.getCurrentAddressValue())
                };
            }

            @Override
            protected String[] getAdditionalDynamicCapabilityNames() {
                return new String[] { CLIENT_SSL_CONTEXT_CAPABILITY };
            }

            @Override
            protected Resource createResourceForAdd(OperationContext context) {
                SSLContextResource resource = new SSLContextResource(Resource.Factory.create(), false);
                context.addResource(PathAddress.EMPTY_ADDRESS, resource);
                return resource;
            }

            @Override
            protected void installedForResource(ServiceController<SSLContext> serviceController, Resource resource) {
                ((SSLContextResource) resource).setSSLContextServiceController(serviceController);
            }

            @Override
            protected ElytronDoohickey<SSLContext> createDoohickey(PathAddress resourceAddress) {
                return new ElytronDoohickey<SSLContext>(resourceAddress) {

                    private volatile String providerName;
                    private volatile String providerLoader;
                    private volatile String keyManagerName;
                    private volatile String trustManagerName;
                    private volatile List<String> protocols;
                    private volatile String cipherSuiteFilter;
                    private volatile String cipherSuiteNames;

                    @Override
                    protected void resolveRuntime(ModelNode model, OperationContext context) throws OperationFailedException {
                        providerName = PROVIDER_NAME.resolveModelAttribute(context, model).asStringOrNull();
                        providerLoader = providersDefinition.resolveModelAttribute(context, model).asStringOrNull();
                        keyManagerName = KEY_MANAGER.resolveModelAttribute(context, model).asStringOrNull();
                        trustManagerName = TRUST_MANAGER.resolveModelAttribute(context, model).asStringOrNull();
                        protocols = PROTOCOLS.unwrap(context, model);
                        cipherSuiteFilter = CIPHER_SUITE_FILTER.resolveModelAttribute(context, model).asString();
                        cipherSuiteNames = CIPHER_SUITE_NAMES.resolveModelAttribute(context, model).asStringOrNull();
                    }

                    @Override
                    protected ExceptionSupplier<SSLContext, StartException> prepareServiceSupplier(OperationContext context,
                            CapabilityServiceBuilder<?> serviceBuilder) throws OperationFailedException {
                        final InjectedValue<KeyManager> keyManagerInjector = new InjectedValue<>();
                        if (keyManagerName != null) {
                            serviceBuilder.addDependency(context.getCapabilityServiceName(
                                    buildDynamicCapabilityName(KEY_MANAGER_CAPABILITY, keyManagerName), KeyManager.class),
                                    KeyManager.class, keyManagerInjector);
                        }
                        final InjectedValue<TrustManager> trustManagerInjector = new InjectedValue<>();
                        if (trustManagerName != null) {
                            serviceBuilder.addDependency(context.getCapabilityServiceName(
                                    buildDynamicCapabilityName(TRUST_MANAGER_CAPABILITY, trustManagerName), TrustManager.class),
                                    TrustManager.class, trustManagerInjector);
                        }
                        final InjectedValue<Provider[]> providersInjector = new InjectedValue<>();
                        if (providerLoader != null) {
                            serviceBuilder.addDependency(context.getCapabilityServiceName(
                                    buildDynamicCapabilityName(PROVIDERS_CAPABILITY, providerLoader), Provider[].class),
                                    Provider[].class, providersInjector);
                        }
                        return () -> buildSSLContext(
                                keyManagerInjector.getOptionalValue(),
                                trustManagerInjector.getOptionalValue(),
                                providersInjector.getOptionalValue());
                    }

                    @Override
                    protected SSLContext createImmediately(OperationContext foreignContext) throws OperationFailedException {
                        KeyManager keyManager = null;
                        if (keyManagerName != null) {
                            @SuppressWarnings("unchecked")
                            ExceptionFunction<OperationContext, KeyManager, OperationFailedException> kmApi =
                                    foreignContext.getCapabilityRuntimeAPI(KEY_MANAGER_API_CAPABILITY, keyManagerName, ExceptionFunction.class);
                            keyManager = kmApi.apply(foreignContext);
                        }
                        TrustManager trustManager = null;
                        if (trustManagerName != null) {
                            @SuppressWarnings("unchecked")
                            ExceptionFunction<OperationContext, TrustManager, OperationFailedException> tmApi =
                                    foreignContext.getCapabilityRuntimeAPI(TRUST_MANAGER_API_CAPABILITY, trustManagerName, ExceptionFunction.class);
                            trustManager = tmApi.apply(foreignContext);
                        }
                        Provider[] providers = null;
                        if (providerLoader != null) {
                            @SuppressWarnings("unchecked")
                            ExceptionFunction<OperationContext, Provider[], OperationFailedException> provApi =
                                    foreignContext.getCapabilityRuntimeAPI(PROVIDERS_API_CAPABILITY, providerLoader, ExceptionFunction.class);
                            providers = provApi.apply(foreignContext);
                        }
                        try {
                            SSLContext ctx = buildSSLContext(keyManager, trustManager, providers);
                            setValue(ctx);
                            return ctx;
                        } catch (StartException e) {
                            throw new OperationFailedException(e);
                        }
                    }

                    private SSLContext buildSSLContext(KeyManager keyManager, TrustManager trustManager, Provider[] providers) throws StartException {
                        X509ExtendedKeyManager x509KeyManager = getX509KeyManager(keyManager);
                        X509ExtendedTrustManager x509TrustManager = getX509TrustManager(trustManager);
                        Provider[] filteredProviders = filterProviders(providers, providerName);

                        SSLContextBuilder builder = new SSLContextBuilder();
                        if (x509KeyManager != null) builder.setKeyManager(x509KeyManager);
                        if (x509TrustManager != null) builder.setTrustManager(x509TrustManager);
                        if (filteredProviders != null) builder.setProviderSupplier(() -> filteredProviders);
                        builder.setCipherSuiteSelector(CipherSuiteSelector.aggregate(
                                cipherSuiteNames != null ? CipherSuiteSelector.fromNamesString(cipherSuiteNames) : null,
                                CipherSuiteSelector.fromString(cipherSuiteFilter)));
                        if (!protocols.isEmpty()) {
                            List<Protocol> list = new ArrayList<>();
                            for (String protocol : protocols) {
                                list.add(Protocol.forName(protocol));
                            }
                            builder.setProtocolSelector(ProtocolSelector.empty().add(EnumSet.copyOf(list)));
                        }
                        builder.setClientMode(true).setWrap(false);

                        if (ROOT_LOGGER.isTraceEnabled()) {
                            ROOT_LOGGER.tracef(
                                    "ClientSSLContext supplying:  keyManager = %s  trustManager = %s  providers = %s  " +
                                            "cipherSuiteFilter = %s cipherSuiteNames = %s protocols = %s",
                                    x509KeyManager, x509TrustManager, Arrays.toString(filteredProviders),
                                    cipherSuiteFilter, cipherSuiteNames, Arrays.toString(protocols.toArray()));
                        }

                        try {
                            return builder.build().create();
                        } catch (GeneralSecurityException e) {
                            throw new StartException(e);
                        }
                    }
                };
            }
        };

        return createSSLContextDefinition(ElytronDescriptionConstants.CLIENT_SSL_CONTEXT, false, add,
                add.createRemoveHandler(SSL_CONTEXT_RUNTIME_CAPABILITY, CLIENT_SSL_CONTEXT_RUNTIME_CAPABILITY), attributes, serverOrHostController);
    }

    static ResourceDefinition getDynamicClientSSLContextDefinition() {

        AttributeDefinition[] attributes = new AttributeDefinition[]{AUTHENTICATION_CONTEXT_ATTRIBUTE};
        AbstractAddStepHandler add = new TrivialAddHandler<SSLContext>(SSLContext.class, SSL_CONTEXT_RUNTIME_CAPABILITY) {
            @Override
            protected ValueSupplier<SSLContext> getValueSupplier(ServiceBuilder<SSLContext> serviceBuilder, OperationContext context, ModelNode model) throws OperationFailedException {
                final String authenticationContextName = AUTHENTICATION_CONTEXT_ATTRIBUTE.resolveModelAttribute(context, model).asString();
                String authenticationContextCapability = buildDynamicCapabilityName(AUTHENTICATION_CONTEXT_CAPABILITY, authenticationContextName);
                ServiceName acServiceName = context.getCapabilityServiceName(authenticationContextCapability, AuthenticationContext.class);
                Supplier<AuthenticationContext> authenticationContextSupplier = serviceBuilder.requires(acServiceName);

                return () -> DynamicSSLContextHelper.getDynamicSSLContextInstance(authenticationContextSupplier.get());
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

        return createSSLContextDefinition(ElytronDescriptionConstants.DYNAMIC_CLIENT_SSL_CONTEXT, false, add, attributes, false, Stability.DEFAULT, ORG_WILDFLY_SECURITY_ELYTRON_DYNAMIC_SSL);
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

            Object isFips;
            try {
                isFips = isFipsMethod.invoke(null, new Object[0]);
                if ((isFips instanceof Boolean)) {
                    return () -> (boolean) isFips;
                } else {
                    return () -> false;
                }
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                ROOT_LOGGER.trace("Unable to invoke com.sun.net.ssl.internal.ssl.Provider.isFIPS() method.", e);
                return () -> false;
            }
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException e) {
            ROOT_LOGGER.trace("Unable to find com.sun.net.ssl.internal.ssl.Provider.isFIPS() method.", e);
        }

        return () -> new SecureRandom().getProvider().getName().toLowerCase(Locale.ENGLISH).contains("fips");
    }

    static ModifiableKeyStoreService getModifiableKeyStoreService(OperationContext context, String keyStoreName) {
        ServiceRegistry serviceRegistry = context.getServiceRegistry(true);
        RuntimeCapability<Void> runtimeCapability = KEY_STORE_RUNTIME_CAPABILITY.fromBaseCapability(keyStoreName);
        ServiceName serviceName = runtimeCapability.getCapabilityServiceName();
        ServiceController<KeyStore> serviceContainer = getRequiredService(serviceRegistry, serviceName, KeyStore.class);
        return (ModifiableKeyStoreService) serviceContainer.getService();
    }

    /**
     * CrlFile contains the necessary information to create a
     * CRL File Input Stream
     */
    static class CrlFile {
        private String crlPath = null;
        private String relativeTo = null;
        private InjectedValue<PathManager> pathManagerInjector = null;

        public CrlFile(final String crlPath, final String relativeTo, InjectedValue<PathManager> pathManagerInjector) {
            this.crlPath = crlPath;
            this.relativeTo = relativeTo;
            this.pathManagerInjector = pathManagerInjector;
        }

        public String getCrlPath() {
            return crlPath;
        }

        public String getRelativeTo() {
            return relativeTo;
        }

        public InjectedValue<PathManager> getPathManagerInjector() {
            return pathManagerInjector;
        }
    }

}
