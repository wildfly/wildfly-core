/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;
import static org.wildfly.common.Assert.checkNotNullParam;
import static org.wildfly.extension.elytron.Capabilities.AUTHENTICATION_CONFIGURATION_API_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.AUTHENTICATION_CONFIGURATION_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.AUTHENTICATION_CONFIGURATION_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.AUTHENTICATION_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.AUTHENTICATION_CONTEXT_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_DOMAIN_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_FACTORY_CREDENTIAL_API_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_FACTORY_CREDENTIAL_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SSL_CONTEXT_API_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SSL_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.operations.validation.StringAllowedValuesValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.extension.elytron.capabilities.CredentialSecurityFactory;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.MatchRule;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.sasl.SaslMechanismSelector;

/**
 * Resource definitions for Elytron authentication client configuration.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class AuthenticationClientDefinitions {

    /* *************************************** */
    /* Authentication Configuration Attributes */
    /* *************************************** */

    static final SimpleAttributeDefinition CONFIGURATION_EXTENDS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.EXTENDS, ModelType.STRING, true)
            .setRestartAllServices()
            .setCapabilityReference(AUTHENTICATION_CONFIGURATION_CAPABILITY, AUTHENTICATION_CONFIGURATION_RUNTIME_CAPABILITY)
            .build();

    static final SimpleAttributeDefinition ANONYMOUS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ANONYMOUS, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .setAlternatives(ElytronDescriptionConstants.AUTHENTICATION_NAME, ElytronDescriptionConstants.KERBEROS_SECURITY_FACTORY)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition AUTHENTICATION_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.AUTHENTICATION_NAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .setAlternatives(ElytronDescriptionConstants.ANONYMOUS, ElytronDescriptionConstants.KERBEROS_SECURITY_FACTORY)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition AUTHORIZATION_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.AUTHORIZATION_NAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition HOST = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.HOST, ModelType.STRING, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition PROTOCOL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PROTOCOL, ModelType.STRING, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition PORT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PORT, ModelType.INT, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition REALM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REALM, ModelType.STRING, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition SECURITY_DOMAIN = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SECURITY_DOMAIN, ModelType.STRING, true)
            .setAllowExpression(false)
            .setRestartAllServices()
            .setCapabilityReference(SECURITY_DOMAIN_CAPABILITY, AUTHENTICATION_CONFIGURATION_RUNTIME_CAPABILITY)
            .build();

    static final SimpleAttributeDefinition FORWARDING_MODE =
            new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.FORWARDING_MODE, ModelType.STRING, true)
                .setAllowExpression(true)
                .setRestartAllServices()
                .setAllowedValues(ElytronDescriptionConstants.AUTHENTICATION, ElytronDescriptionConstants.AUTHORIZATION)
                .setValidator(new StringAllowedValuesValidator(ElytronDescriptionConstants.AUTHENTICATION, ElytronDescriptionConstants.AUTHORIZATION))
                .setDefaultValue(new ModelNode(ElytronDescriptionConstants.AUTHENTICATION))
                .build();

    static final SimpleAttributeDefinition SASL_MECHANISM_SELECTOR = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SASL_MECHANISM_SELECTOR, ModelType.STRING, true)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final PropertiesAttributeDefinition MECHANISM_PROPERTIES = new PropertiesAttributeDefinition.Builder(ElytronDescriptionConstants.MECHANISM_PROPERTIES, true)
            .setXmlName(ElytronDescriptionConstants.MECHANISM_PROPERTIES)
            .setRestartAllServices()
            .build();

    static final ObjectTypeAttributeDefinition CREDENTIAL_REFERENCE = CredentialReference.getAttributeBuilder(true, true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition KERBEROS_SECURITY_FACTORY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KERBEROS_SECURITY_FACTORY, ModelType.STRING, true)
            .setRestartAllServices()
            .setAlternatives(ElytronDescriptionConstants.ANONYMOUS, ElytronDescriptionConstants.AUTHENTICATION_NAME)
            .setCapabilityReference(SECURITY_FACTORY_CREDENTIAL_CAPABILITY, AUTHENTICATION_CONFIGURATION_RUNTIME_CAPABILITY)
            .build();


    static final SimpleAttributeDefinition HTTP_MECHANISM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.HTTP_MECHANISM, ModelType.STRING, true)
            .setAllowedValues(new ModelNode("BASIC"))
            .setValidator(new StringAllowedValuesValidator("BASIC"))
            .setRequired(false)
            .build();

    static final SimpleAttributeDefinition WS_SECURITY_TYPE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.WS_SECURITY_TYPE, ModelType.STRING, true)
            .setValidator(new StringAllowedValuesValidator("UsernameToken"))
            .setRequired(false)
            .build();

    static final ObjectTypeAttributeDefinition WEBSERVICES = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.WEBSERVICES, HTTP_MECHANISM, WS_SECURITY_TYPE)
            .setRequired(false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final AttributeDefinition[] AUTHENTICATION_CONFIGURATION_SIMPLE_ATTRIBUTES = new AttributeDefinition[] { CONFIGURATION_EXTENDS, ANONYMOUS, AUTHENTICATION_NAME, AUTHORIZATION_NAME, HOST, PROTOCOL,
            PORT, REALM, SECURITY_DOMAIN, FORWARDING_MODE, SASL_MECHANISM_SELECTOR, KERBEROS_SECURITY_FACTORY };

    static final AttributeDefinition[] AUTHENTICATION_CONFIGURATION_ALL_ATTRIBUTES = new AttributeDefinition[] { CONFIGURATION_EXTENDS, ANONYMOUS, AUTHENTICATION_NAME, AUTHORIZATION_NAME, HOST, PROTOCOL,
            PORT, REALM, SECURITY_DOMAIN, FORWARDING_MODE, KERBEROS_SECURITY_FACTORY, SASL_MECHANISM_SELECTOR, MECHANISM_PROPERTIES, CREDENTIAL_REFERENCE, WEBSERVICES };

    /* *************************************** */
    /* Authentication Context Attributes */
    /* *************************************** */

    static final SimpleAttributeDefinition MATCH_USER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MATCH_USER, ModelType.STRING, true)
            .setAllowExpression(true)
            .setAlternatives(ElytronDescriptionConstants.MATCH_NO_USER)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition MATCH_NO_USER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MATCH_NO_USER, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .setAlternatives(ElytronDescriptionConstants.MATCH_USER)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition MATCH_URN = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MATCH_URN, ModelType.STRING, true)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition MATCH_LOCAL_SECURITY_DOMAIN = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MATCH_LOCAL_SECURITY_DOMAIN, ModelType.STRING, true)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition MATCH_PROTOCOL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MATCH_PROTOCOL, ModelType.STRING, true)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition MATCH_ABSTRACT_TYPE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MATCH_ABSTRACT_TYPE, ModelType.STRING, true)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition MATCH_ABSTRACT_TYPE_AUTHORITY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MATCH_ABSTRACT_TYPE_AUTHORITY, ModelType.STRING, true)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition MATCH_HOST = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MATCH_HOST, ModelType.STRING, true)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition MATCH_PATH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MATCH_PATH, ModelType.STRING, true)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition MATCH_PORT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MATCH_PORT, ModelType.INT, true)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition CONTEXT_EXTENDS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.EXTENDS, ModelType.STRING, true)
            .setRestartAllServices()
            .setCapabilityReference(AUTHENTICATION_CONTEXT_CAPABILITY, AUTHENTICATION_CONTEXT_RUNTIME_CAPABILITY)
            .build();

    static final SimpleAttributeDefinition AUTHENTICATION_CONFIGURATION = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.AUTHENTICATION_CONFIGURATION, ModelType.STRING, true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilityReference(AUTHENTICATION_CONFIGURATION_CAPABILITY, AUTHENTICATION_CONTEXT_RUNTIME_CAPABILITY)
            .build();

    static final SimpleAttributeDefinition SSL_CONTEXT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SSL_CONTEXT, ModelType.STRING, true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilityReference(SSL_CONTEXT_CAPABILITY, AUTHENTICATION_CONTEXT_RUNTIME_CAPABILITY)
            .build();

    static final ObjectTypeAttributeDefinition MATCH_RULE = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.MATCH_RULE,
            MATCH_ABSTRACT_TYPE, MATCH_ABSTRACT_TYPE_AUTHORITY, MATCH_HOST, MATCH_LOCAL_SECURITY_DOMAIN, MATCH_NO_USER, MATCH_PATH, MATCH_PORT, MATCH_PROTOCOL, MATCH_URN, MATCH_USER,
            AUTHENTICATION_CONFIGURATION, SSL_CONTEXT).build();

    static final ObjectListAttributeDefinition MATCH_RULES = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.MATCH_RULES, MATCH_RULE)
            .setRequired(false)
            .setRestartAllServices()
            .build();

    static ResourceDefinition getAuthenticationClientDefinition() {

        DoohickeyAddHandler<AuthenticationConfiguration> add = new DoohickeyAddHandler<AuthenticationConfiguration>(
                AUTHENTICATION_CONFIGURATION_RUNTIME_CAPABILITY, AUTHENTICATION_CONFIGURATION_API_CAPABILITY) {

            @Override
            protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {
                super.populateModel(context, operation, resource);
                handleCredentialReferenceUpdate(context, resource.getModel());
            }

            @Override
            protected void rollbackRuntime(OperationContext context, final ModelNode operation, final Resource resource) {
                rollbackCredentialStoreUpdate(CREDENTIAL_REFERENCE, context, resource);
            }

            @Override
            protected ElytronDoohickey<AuthenticationConfiguration> createDoohickey(PathAddress resourceAddress) {
                return new ElytronDoohickey<AuthenticationConfiguration>(resourceAddress) {

                    // Resolved model values captured in resolveRuntime
                    private volatile String parentName;
                    private volatile boolean anonymous;
                    private volatile String authenticationName;
                    private volatile String authorizationName;
                    private volatile String host;
                    private volatile String protocol;
                    private volatile int port;
                    private volatile String realm;
                    private volatile String securityDomainName;
                    private volatile String forwardAuth;
                    private volatile String saslMechanismSelector;
                    private volatile String kerberosSecurityFactoryName;
                    private volatile Map<String, String> mechanismProperties;
                    private volatile ModelNode credentialReferenceModel;
                    private volatile Map<String, Object> webServicesMap;

                    @Override
                    protected void resolveRuntime(ModelNode model, OperationContext context) throws OperationFailedException {
                        parentName = CONFIGURATION_EXTENDS.resolveModelAttribute(context, model).asStringOrNull();
                        anonymous = ANONYMOUS.resolveModelAttribute(context, model).asBoolean();
                        authenticationName = AUTHENTICATION_NAME.resolveModelAttribute(context, model).asStringOrNull();
                        authorizationName = AUTHORIZATION_NAME.resolveModelAttribute(context, model).asStringOrNull();
                        host = HOST.resolveModelAttribute(context, model).asStringOrNull();
                        protocol = PROTOCOL.resolveModelAttribute(context, model).asStringOrNull();
                        port = PORT.resolveModelAttribute(context, model).asInt(-1);
                        realm = REALM.resolveModelAttribute(context, model).asStringOrNull();
                        securityDomainName = SECURITY_DOMAIN.resolveModelAttribute(context, model).asStringOrNull();
                        forwardAuth = FORWARDING_MODE.resolveModelAttribute(context, model).asStringOrNull();
                        saslMechanismSelector = SASL_MECHANISM_SELECTOR.resolveModelAttribute(context, model).asStringOrNull();
                        kerberosSecurityFactoryName = KERBEROS_SECURITY_FACTORY.resolveModelAttribute(context, model).asStringOrNull();

                        ModelNode properties = MECHANISM_PROPERTIES.resolveModelAttribute(context, model);
                        if (properties.isDefined()) {
                            mechanismProperties = new HashMap<>();
                            for (String s : properties.keys()) {
                                mechanismProperties.put(s, properties.require(s).asString());
                            }
                        } else {
                            mechanismProperties = null;
                        }

                        credentialReferenceModel = CREDENTIAL_REFERENCE.resolveModelAttribute(context, model);

                        ModelNode webServices = WEBSERVICES.resolveModelAttribute(context, model);
                        if (webServices.isDefined()) {
                            webServicesMap = new HashMap<>();
                            for (String s : webServices.keys()) {
                                webServicesMap.put(s, webServices.require(s));
                            }
                        } else {
                            webServicesMap = null;
                        }
                    }

                    @Override
                    protected ExceptionSupplier<AuthenticationConfiguration, StartException> prepareServiceSupplier(
                            OperationContext context, CapabilityServiceBuilder<?> serviceBuilder) throws OperationFailedException {

                        Supplier<AuthenticationConfiguration> parentSupplier;
                        if (parentName != null) {
                            InjectedValue<AuthenticationConfiguration> parentInjector = new InjectedValue<>();
                            serviceBuilder.addDependency(context.getCapabilityServiceName(
                                    RuntimeCapability.buildDynamicCapabilityName(AUTHENTICATION_CONFIGURATION_CAPABILITY, parentName),
                                    AuthenticationConfiguration.class), AuthenticationConfiguration.class, parentInjector);
                            parentSupplier = parentInjector::getValue;
                        } else {
                            parentSupplier = () -> AuthenticationConfiguration.EMPTY;
                        }

                        Function<AuthenticationConfiguration, AuthenticationConfiguration> configuration = ignored -> parentSupplier.get();
                        configuration = applySimpleAttributes(configuration);

                        if (securityDomainName != null) {
                            InjectedValue<SecurityDomain> securityDomainInjector = new InjectedValue<>();
                            serviceBuilder.addDependency(context.getCapabilityServiceName(
                                    SECURITY_DOMAIN_CAPABILITY, securityDomainName, SecurityDomain.class),
                                    SecurityDomain.class, securityDomainInjector);
                            if (ElytronDescriptionConstants.AUTHORIZATION.equals(forwardAuth)) {
                                configuration = configuration.andThen(c -> c.useForwardedAuthorizationIdentity(securityDomainInjector.getValue()));
                            } else {
                                configuration = configuration.andThen(c -> c.useForwardedIdentity(securityDomainInjector.getValue()));
                            }
                        }

                        if (kerberosSecurityFactoryName != null) {
                            InjectedValue<CredentialSecurityFactory> kerberosFactoryInjector = new InjectedValue<>();
                            serviceBuilder.addDependency(context.getCapabilityServiceName(
                                    SECURITY_FACTORY_CREDENTIAL_CAPABILITY, kerberosSecurityFactoryName, CredentialSecurityFactory.class),
                                    CredentialSecurityFactory.class, kerberosFactoryInjector);
                            configuration = configuration.andThen(c -> c.useKerberosSecurityFactory(kerberosFactoryInjector.getValue()));
                        }

                        if (credentialReferenceModel.isDefined()) {
                            // Re-read the full model from the resource address for the credential supplier.
                            ModelNode fullModel = context.readResourceFromRoot(resourceAddress).getModel();
                            final InjectedValue<ExceptionSupplier<CredentialSource, Exception>> credentialSourceSupplierInjector = new InjectedValue<>();
                            credentialSourceSupplierInjector.inject(CredentialReference.getCredentialSourceSupplier(
                                    context, CREDENTIAL_REFERENCE, fullModel, serviceBuilder));
                            configuration = configuration.andThen(c -> resolvePasswordConfig(c, credentialSourceSupplierInjector.getValue()));
                        }

                        final Function<AuthenticationConfiguration, AuthenticationConfiguration> finalConfiguration = configuration;
                        return () -> {
                            try {
                                return finalConfiguration.apply(null);
                            } catch (IllegalStateException e) {
                                if (e.getCause() != null) {
                                    throw ROOT_LOGGER.unableToStartService((Exception) e.getCause());
                                }
                                throw ROOT_LOGGER.unableToStartService(e);
                            }
                        };
                    }

                    @Override
                    protected AuthenticationConfiguration createImmediately(OperationContext foreignContext) throws OperationFailedException {
                        // Re-read this resource's own model via its root address.
                        ModelNode model = foreignContext.readResourceFromRoot(resourceAddress).getModel();

                        AuthenticationConfiguration parent;
                        if (parentName != null) {
                            @SuppressWarnings("unchecked")
                            ExceptionFunction<OperationContext, AuthenticationConfiguration, OperationFailedException> parentApi =
                                    foreignContext.getCapabilityRuntimeAPI(AUTHENTICATION_CONFIGURATION_API_CAPABILITY,
                                            parentName, ExceptionFunction.class);
                            parent = parentApi.apply(foreignContext);
                        } else {
                            parent = AuthenticationConfiguration.EMPTY;
                        }

                        Function<AuthenticationConfiguration, AuthenticationConfiguration> configuration = ignored -> parent;
                        configuration = applySimpleAttributes(configuration);

                        // security-domain: not supported in early path (no SecurityDomain API capability).
                        // If configured, skip identity forwarding silently — the service path applies it.

                        if (kerberosSecurityFactoryName != null) {
                            @SuppressWarnings("unchecked")
                            ExceptionFunction<OperationContext, CredentialSecurityFactory, OperationFailedException> ksfApi =
                                    foreignContext.getCapabilityRuntimeAPI(SECURITY_FACTORY_CREDENTIAL_API_CAPABILITY,
                                            kerberosSecurityFactoryName, ExceptionFunction.class);
                            CredentialSecurityFactory ksf = ksfApi.apply(foreignContext);
                            configuration = configuration.andThen(c -> c.useKerberosSecurityFactory(ksf));
                        }

                        if (credentialReferenceModel.isDefined()) {
                            CredentialSource cs = CredentialReference.getCredentialSource(foreignContext, CREDENTIAL_REFERENCE, model);
                            configuration = configuration.andThen(c -> {
                                try {
                                    return resolvePasswordConfigImmediate(c, cs);
                                } catch (OperationFailedException e) {
                                    throw new IllegalStateException(e);
                                }
                            });
                        }

                        try {
                            AuthenticationConfiguration result = configuration.apply(null);
                            setValue(result);
                            return result;
                        } catch (IllegalStateException e) {
                            if (e.getCause() instanceof OperationFailedException) {
                                throw (OperationFailedException) e.getCause();
                            }
                            throw new OperationFailedException(e);
                        }
                    }

                    /** Applies all simple (non-injected) attribute transformations to a configuration chain. */
                    private Function<AuthenticationConfiguration, AuthenticationConfiguration> applySimpleAttributes(
                            Function<AuthenticationConfiguration, AuthenticationConfiguration> configuration) {
                        if (anonymous) configuration = configuration.andThen(c -> c.useAnonymous());
                        if (authenticationName != null) configuration = configuration.andThen(c -> c.useName(authenticationName));
                        if (authorizationName != null) configuration = configuration.andThen(c -> c.useAuthorizationName(authorizationName));
                        if (host != null) configuration = configuration.andThen(c -> c.useHost(host));
                        if (protocol != null) configuration = configuration.andThen(c -> c.useProtocol(protocol));
                        if (port > 0) configuration = configuration.andThen(c -> c.usePort(port));
                        if (realm != null) configuration = configuration.andThen(c -> c.useRealm(realm));
                        if (saslMechanismSelector != null) {
                            SaslMechanismSelector selector = SaslMechanismSelector.fromString(saslMechanismSelector);
                            if (selector != null) configuration = configuration.andThen(c -> c.setSaslMechanismSelector(selector));
                        }
                        if (mechanismProperties != null) {
                            configuration = configuration.andThen(c -> c.useMechanismProperties(mechanismProperties, parentName == null));
                        }
                        if (webServicesMap != null && !webServicesMap.isEmpty()) {
                            configuration = configuration.andThen(c -> c.useWebServices(webServicesMap));
                        }
                        return configuration;
                    }

                    private AuthenticationConfiguration resolvePasswordConfig(AuthenticationConfiguration c,
                            ExceptionSupplier<CredentialSource, Exception> sourceSupplier) {
                        try {
                            CredentialSource cs = sourceSupplier.get();
                            return resolvePasswordConfigImmediate(c, cs);
                        } catch (OperationFailedException e) {
                            throw new IllegalStateException(e);
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }

                    private AuthenticationConfiguration resolvePasswordConfigImmediate(AuthenticationConfiguration c,
                            CredentialSource cs) throws OperationFailedException {
                        if (cs == null) throw ROOT_LOGGER.credentialCannotBeResolved();
                        PasswordCredential passCredential = null;
                        try {
                            passCredential = cs.getCredential(PasswordCredential.class);
                        } catch (Exception e) {
                            throw new OperationFailedException(e);
                        }
                        String alias = credentialReferenceModel.hasDefined(CredentialReference.ALIAS)
                                ? credentialReferenceModel.get(CredentialReference.ALIAS).asString() : null;
                        if (passCredential == null) {
                            if (alias != null && alias.length() > 0) {
                                throw ROOT_LOGGER.credentialDoesNotExist(alias, PasswordCredential.class.getName());
                            }
                            throw ROOT_LOGGER.credentialCannotBeResolved();
                        }
                        return c.usePassword(passCredential.getPassword());
                    }
                };
            }
        };
        return new TrivialResourceDefinition(ElytronDescriptionConstants.AUTHENTICATION_CONFIGURATION, add, AUTHENTICATION_CONFIGURATION_ALL_ATTRIBUTES, AUTHENTICATION_CONFIGURATION_RUNTIME_CAPABILITY);
    }

    static ResourceDefinition getAuthenticationContextDefinition() {
        AttributeDefinition[] attributes = new AttributeDefinition[] { CONTEXT_EXTENDS, MATCH_RULES };

        DoohickeyAddHandler<AuthenticationContext> add = new DoohickeyAddHandler<AuthenticationContext>(
                AUTHENTICATION_CONTEXT_RUNTIME_CAPABILITY, Capabilities.AUTHENTICATION_CONTEXT_API_CAPABILITY) {

            @Override
            protected Mode getInitialMode() {
                return Mode.ON_DEMAND;
            }

            @Override
            protected ElytronDoohickey<AuthenticationContext> createDoohickey(PathAddress resourceAddress) {
                return new ElytronDoohickey<AuthenticationContext>(resourceAddress) {

                    // Resolved match-rule data (parallel lists, one entry per defined match rule)
                    private volatile List<ResolvedMatchRule> resolvedMatchRules;
                    private volatile String parentContextName;

                    @Override
                    protected void resolveRuntime(ModelNode model, OperationContext context) throws OperationFailedException {
                        parentContextName = CONTEXT_EXTENDS.resolveModelAttribute(context, model).asStringOrNull();
                        resolvedMatchRules = new java.util.ArrayList<>();
                        if (model.hasDefined(ElytronDescriptionConstants.MATCH_RULES)) {
                            for (ModelNode current : model.require(ElytronDescriptionConstants.MATCH_RULES).asList()) {
                                String authenticationConfiguration = AUTHENTICATION_CONFIGURATION.resolveModelAttribute(context, current).asStringOrNull();
                                String sslContext = SSL_CONTEXT.resolveModelAttribute(context, current).asStringOrNull();
                                if (authenticationConfiguration == null && sslContext == null) {
                                    continue;
                                }
                                resolvedMatchRules.add(new ResolvedMatchRule(
                                        MATCH_ABSTRACT_TYPE.resolveModelAttribute(context, current).asStringOrNull(),
                                        MATCH_ABSTRACT_TYPE_AUTHORITY.resolveModelAttribute(context, current).asStringOrNull(),
                                        MATCH_HOST.resolveModelAttribute(context, current),
                                        MATCH_LOCAL_SECURITY_DOMAIN.resolveModelAttribute(context, current),
                                        MATCH_NO_USER.resolveModelAttribute(context, current).asBoolean(),
                                        MATCH_PATH.resolveModelAttribute(context, current),
                                        MATCH_PORT.resolveModelAttribute(context, current),
                                        MATCH_PROTOCOL.resolveModelAttribute(context, current),
                                        MATCH_URN.resolveModelAttribute(context, current),
                                        MATCH_USER.resolveModelAttribute(context, current),
                                        authenticationConfiguration,
                                        sslContext));
                            }
                        }
                    }

                    @Override
                    protected ExceptionSupplier<AuthenticationContext, StartException> prepareServiceSupplier(
                            OperationContext context, CapabilityServiceBuilder<?> serviceBuilder)
                            throws OperationFailedException {
                        final Supplier<AuthenticationContext> parentSupplier;
                        if (parentContextName != null) {
                            InjectedValue<AuthenticationContext> parentInjector = new InjectedValue<>();
                            serviceBuilder.addDependency(context.getCapabilityServiceName(
                                    RuntimeCapability.buildDynamicCapabilityName(AUTHENTICATION_CONTEXT_CAPABILITY, parentContextName),
                                    AuthenticationContext.class), AuthenticationContext.class, parentInjector);
                            parentSupplier = parentInjector::getValue;
                        } else {
                            parentSupplier = AuthenticationContext::empty;
                        }

                        Function<AuthenticationContext, AuthenticationContext> authContextFn = Function.identity();
                        for (ResolvedMatchRule rule : resolvedMatchRules) {
                            Supplier<MatchRule> matchRuleSupplier = new OneTimeSupplier<>(rule::buildMatchRule);
                            if (rule.authenticationConfiguration != null) {
                                InjectedValue<AuthenticationConfiguration> acInjector = new InjectedValue<>();
                                serviceBuilder.addDependency(context.getCapabilityServiceName(
                                        RuntimeCapability.buildDynamicCapabilityName(AUTHENTICATION_CONFIGURATION_CAPABILITY, rule.authenticationConfiguration),
                                        AuthenticationConfiguration.class), AuthenticationConfiguration.class, acInjector);
                                authContextFn = authContextFn.andThen(a -> a.with(matchRuleSupplier.get(), acInjector.getValue()));
                            }
                            if (rule.sslContext != null) {
                                InjectedValue<SSLContext> sslInjector = new InjectedValue<>();
                                serviceBuilder.addDependency(context.getCapabilityServiceName(
                                        RuntimeCapability.buildDynamicCapabilityName(SSL_CONTEXT_CAPABILITY, rule.sslContext),
                                        SSLContext.class), SSLContext.class, sslInjector);
                                authContextFn = authContextFn.andThen(a -> a.withSsl(matchRuleSupplier.get(), sslInjector::getValue));
                            }
                        }

                        final Function<AuthenticationContext, AuthenticationContext> finalContextFn = authContextFn;
                        return () -> finalContextFn.apply(parentSupplier.get());
                    }

                    @Override
                    protected AuthenticationContext createImmediately(OperationContext foreignContext) throws OperationFailedException {
                        AuthenticationContext parent;
                        if (parentContextName != null) {
                            @SuppressWarnings("unchecked")
                            org.wildfly.common.function.ExceptionFunction<OperationContext, AuthenticationContext, OperationFailedException> parentApi =
                                    foreignContext.getCapabilityRuntimeAPI(Capabilities.AUTHENTICATION_CONTEXT_API_CAPABILITY, parentContextName,
                                            org.wildfly.common.function.ExceptionFunction.class);
                            parent = parentApi.apply(foreignContext);
                        } else {
                            parent = AuthenticationContext.empty();
                        }

                        AuthenticationContext ctx = parent;
                        for (ResolvedMatchRule rule : resolvedMatchRules) {
                            MatchRule matchRule = rule.buildMatchRule();
                            if (rule.authenticationConfiguration != null) {
                                @SuppressWarnings("unchecked")
                                ExceptionFunction<OperationContext, AuthenticationConfiguration, OperationFailedException> acApi =
                                        foreignContext.getCapabilityRuntimeAPI(AUTHENTICATION_CONFIGURATION_API_CAPABILITY,
                                                rule.authenticationConfiguration, ExceptionFunction.class);
                                AuthenticationConfiguration resolvedAc = acApi.apply(foreignContext);
                                ctx = ctx.with(matchRule, resolvedAc);
                            }
                            if (rule.sslContext != null) {
                                @SuppressWarnings("unchecked")
                                ExceptionFunction<OperationContext, SSLContext, OperationFailedException> sslApi =
                                        foreignContext.getCapabilityRuntimeAPI(SSL_CONTEXT_API_CAPABILITY, rule.sslContext,
                                                ExceptionFunction.class);
                                SSLContext resolvedSsl = sslApi.apply(foreignContext);
                                ctx = ctx.withSsl(matchRule, () -> resolvedSsl);
                            }
                        }

                        setValue(ctx);
                        return ctx;
                    }
                };
            }
        };

        return new TrivialResourceDefinition(ElytronDescriptionConstants.AUTHENTICATION_CONTEXT, add, attributes,
                AUTHENTICATION_CONTEXT_RUNTIME_CAPABILITY);
    }

    /**
     * Holds the resolved match-rule data for a single match rule entry in an {@code authentication-context}.
     * Pure data — no MSC wiring. Used by both the service path and the early-access path.
     */
    private static final class ResolvedMatchRule {
        final String abstractType;
        final String abstractTypeAuthority;
        final ModelNode host;
        final ModelNode localSecurityDomain;
        final boolean noUser;
        final ModelNode path;
        final ModelNode port;
        final ModelNode protocol;
        final ModelNode urn;
        final ModelNode user;
        final String authenticationConfiguration;
        final String sslContext;

        ResolvedMatchRule(String abstractType, String abstractTypeAuthority,
                ModelNode host, ModelNode localSecurityDomain, boolean noUser,
                ModelNode path, ModelNode port, ModelNode protocol,
                ModelNode urn, ModelNode user,
                String authenticationConfiguration, String sslContext) {
            this.abstractType = abstractType;
            this.abstractTypeAuthority = abstractTypeAuthority;
            this.host = host;
            this.localSecurityDomain = localSecurityDomain;
            this.noUser = noUser;
            this.path = path;
            this.port = port;
            this.protocol = protocol;
            this.urn = urn;
            this.user = user;
            this.authenticationConfiguration = authenticationConfiguration;
            this.sslContext = sslContext;
        }

        MatchRule buildMatchRule() {
            Function<MatchRule, MatchRule> fn = ignored -> MatchRule.ALL;
            if (abstractType != null || abstractTypeAuthority != null) fn = fn.andThen(m -> m.matchAbstractType(abstractType, abstractTypeAuthority));
            if (host.isDefined()) fn = fn.andThen(m -> m.matchHost(host.asString()));
            if (localSecurityDomain.isDefined()) fn = fn.andThen(m -> m.matchLocalSecurityDomain(localSecurityDomain.asString()));
            if (noUser) fn = fn.andThen(m -> m.matchNoUser());
            if (path.isDefined()) fn = fn.andThen(m -> m.matchPath(path.asString()));
            if (port.isDefined()) fn = fn.andThen(m -> m.matchPort(port.asInt()));
            if (protocol.isDefined()) fn = fn.andThen(m -> m.matchProtocol(protocol.asString()));
            if (urn.isDefined()) fn = fn.andThen(m -> m.matchUrnName(urn.asString()));
            if (user.isDefined()) fn = fn.andThen(m -> m.matchUser(user.asString()));
            return fn.apply(null);
        }
    }

    static final class OneTimeSupplier<T>  implements Supplier<T> {

        private final Supplier<T> supplier;
        private T value;

        OneTimeSupplier(Supplier<T> supplier) {
            checkNotNullParam("supplier", supplier);
            this.supplier = supplier;
        }

        @Override
        public T get() {
            if (value == null) {
                value = supplier.get();
            }
            return value;
        }

    }

}
