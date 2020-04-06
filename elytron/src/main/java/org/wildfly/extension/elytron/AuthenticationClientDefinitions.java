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

import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;
import static org.wildfly.common.Assert.checkNotNullParam;
import static org.wildfly.extension.elytron.Capabilities.AUTHENTICATION_CONFIGURATION_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.AUTHENTICATION_CONFIGURATION_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.AUTHENTICATION_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.AUTHENTICATION_CONTEXT_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_DOMAIN_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_FACTORY_CREDENTIAL_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SSL_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
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
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;
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

    static final PropertiesAttributeDefinition MECHANISM_PROPERTIES = new PropertiesAttributeDefinition.Builder(ElytronDescriptionConstants.PROPERTIES, true)
            .setName(ElytronDescriptionConstants.MECHANISM_PROPERTIES)
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

        TrivialAddHandler<AuthenticationConfiguration> add = new TrivialAddHandler<AuthenticationConfiguration>(AuthenticationConfiguration.class, AUTHENTICATION_CONFIGURATION_ALL_ATTRIBUTES,
                AUTHENTICATION_CONFIGURATION_RUNTIME_CAPABILITY) {

            @Override
            protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {
                super.populateModel(context, operation, resource);
                handleCredentialReferenceUpdate(context, resource.getModel());
            }

            @Override
            protected ValueSupplier<AuthenticationConfiguration> getValueSupplier(
                    ServiceBuilder<AuthenticationConfiguration> serviceBuilder, OperationContext context, ModelNode model)
                    throws OperationFailedException {
                String parent = CONFIGURATION_EXTENDS.resolveModelAttribute(context, model).asStringOrNull();
                Supplier<AuthenticationConfiguration> parentSupplier;
                if (parent != null) {
                    InjectedValue<AuthenticationConfiguration> parentInjector = new InjectedValue<>();

                    serviceBuilder.addDependency(context.getCapabilityServiceName(
                            RuntimeCapability.buildDynamicCapabilityName(AUTHENTICATION_CONFIGURATION_CAPABILITY, parent), AuthenticationConfiguration.class),
                            AuthenticationConfiguration.class, parentInjector);

                    parentSupplier = parentInjector::getValue;
                } else {
                    parentSupplier = () -> AuthenticationConfiguration.EMPTY;
                }

                Function<AuthenticationConfiguration, AuthenticationConfiguration> configuration = ignored -> parentSupplier.get();

                boolean anonymous = ANONYMOUS.resolveModelAttribute(context, model).asBoolean();
                configuration = anonymous ? configuration.andThen(c -> c.useAnonymous()) : configuration;

                String authenticationName = AUTHENTICATION_NAME.resolveModelAttribute(context, model).asStringOrNull();
                configuration = authenticationName != null ? configuration.andThen(c -> c.useName(authenticationName)) : configuration;

                String authorizationName = AUTHORIZATION_NAME.resolveModelAttribute(context, model).asStringOrNull();
                configuration = authorizationName != null ? configuration.andThen(c -> c.useAuthorizationName(authorizationName)) : configuration;

                String host = HOST.resolveModelAttribute(context, model).asStringOrNull();
                configuration = host != null ? configuration.andThen(c -> c.useHost(host)) : configuration;

                String protocol = PROTOCOL.resolveModelAttribute(context, model).asStringOrNull();
                configuration = protocol != null ? configuration.andThen(c -> c.useProtocol(protocol)) : configuration;

                int port = PORT.resolveModelAttribute(context, model).asInt(-1);
                configuration = port > 0 ? configuration.andThen(c -> c.usePort(port)) : configuration;

                String realm = REALM.resolveModelAttribute(context, model).asStringOrNull();
                configuration = realm != null ? configuration.andThen(c -> c.useRealm(realm)) : configuration;

                String securityDomain = SECURITY_DOMAIN.resolveModelAttribute(context, model).asStringOrNull();
                String forwardAuth = FORWARDING_MODE.resolveModelAttribute(context, model).asStringOrNull();

                if (securityDomain != null) {
                    InjectedValue<SecurityDomain> securityDomainInjector = getSecurityDomain(serviceBuilder, context, securityDomain);
                    if (ElytronDescriptionConstants.AUTHORIZATION.equals(forwardAuth)) {
                        configuration = configuration.andThen(c -> c.useForwardedAuthorizationIdentity(securityDomainInjector.getValue()));
                    } else {
                        configuration = configuration.andThen(c -> c.useForwardedIdentity(securityDomainInjector.getValue()));
                    }
                }

                String saslMechanismSelector = SASL_MECHANISM_SELECTOR.resolveModelAttribute(context, model).asStringOrNull();
                if (saslMechanismSelector != null) {
                    SaslMechanismSelector selector = SaslMechanismSelector.fromString(saslMechanismSelector);
                    configuration = selector != null ? configuration.andThen(c -> c.setSaslMechanismSelector(selector)) : configuration;
                }

                String kerberosSecurityFactory = KERBEROS_SECURITY_FACTORY.resolveModelAttribute(context, model).asStringOrNull();
                if (kerberosSecurityFactory != null) {
                    InjectedValue<CredentialSecurityFactory> kerberosFactoryInjector = new InjectedValue<>();
                    serviceBuilder.addDependency(context.getCapabilityServiceName(SECURITY_FACTORY_CREDENTIAL_CAPABILITY, kerberosSecurityFactory, CredentialSecurityFactory.class),
                            CredentialSecurityFactory.class, kerberosFactoryInjector);
                    configuration = configuration.andThen(c -> c.useKerberosSecurityFactory(kerberosFactoryInjector.getValue()));
                }

                ModelNode properties = MECHANISM_PROPERTIES.resolveModelAttribute(context, model);
                if (properties.isDefined()) {
                    Map<String, String> propertiesMap = new HashMap<String, String>();
                    for (String s : properties.keys()) {
                        propertiesMap.put(s, properties.require(s).asString());
                    }
                    configuration = configuration.andThen(c -> c.useMechanismProperties(propertiesMap, parent == null));
                }

                ModelNode credentialReference = CREDENTIAL_REFERENCE.resolveModelAttribute(context, model);
                if (credentialReference.isDefined()) {
                    final InjectedValue<ExceptionSupplier<CredentialSource, Exception>> credentialSourceSupplierInjector = new InjectedValue<>();
                    credentialSourceSupplierInjector.inject(CredentialReference.getCredentialSourceSupplier(context, CREDENTIAL_REFERENCE, model, serviceBuilder));
                    configuration = configuration.andThen(c -> {
                        ExceptionSupplier<CredentialSource, Exception> sourceSupplier = credentialSourceSupplierInjector
                                .getValue();
                        try {
                            CredentialSource cs = sourceSupplier.get();
                            if (cs != null) {
                                PasswordCredential passCredential = cs.getCredential(PasswordCredential.class);
                                String alias = credentialReference.hasDefined(CredentialReference.ALIAS) ? credentialReference.get(CredentialReference.ALIAS).asString() : null;
                                if (passCredential == null) {
                                    if (alias != null && alias.length() > 0) {
                                        throw ROOT_LOGGER.credentialDoesNotExist(alias, PasswordCredential.class.getName());
                                    }
                                    throw ROOT_LOGGER.credentialCannotBeResolved();
                                }
                                return c.usePassword(passCredential.getPassword());
                            } else {
                                throw ROOT_LOGGER.credentialCannotBeResolved();
                            }
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    });
                }

                ModelNode webServices = WEBSERVICES.resolveModelAttribute(context, model);
                if (webServices.isDefined()) {
                    Map<String, Object> wsMap = new HashMap<String, Object>();
                    for (String s : webServices.keys()) {
                        wsMap.put(s, webServices.require(s));
                    }
                    configuration = wsMap.isEmpty() ? configuration : configuration.andThen(c -> c.useWebServices(wsMap));
                }

                final Function<AuthenticationConfiguration, AuthenticationConfiguration> finalConfiguration = configuration;
                return () -> {
                    try {
                        return finalConfiguration.apply(null);
                    } catch (IllegalStateException e) {
                        if (e.getCause() != null) {
                            throw ROOT_LOGGER.unableToStartService((Exception)e.getCause());
                        }
                        throw ROOT_LOGGER.unableToStartService(e);
                    }
                };
            }

            @Override
            protected void rollbackRuntime(OperationContext context, final ModelNode operation, final Resource resource) {
                rollbackCredentialStoreUpdate(CREDENTIAL_REFERENCE, context, resource);
            }

            private InjectedValue<SecurityDomain> getSecurityDomain(ServiceBuilder<AuthenticationConfiguration> serviceBuilder, OperationContext context, String securityDomain) {
                InjectedValue<SecurityDomain> securityDomainInjector = new InjectedValue<>();
                serviceBuilder.addDependency(context.getCapabilityServiceName(SECURITY_DOMAIN_CAPABILITY, securityDomain, SecurityDomain.class), SecurityDomain.class, securityDomainInjector);
                return securityDomainInjector;
            }
        };
        return new TrivialResourceDefinition(ElytronDescriptionConstants.AUTHENTICATION_CONFIGURATION, add, AUTHENTICATION_CONFIGURATION_ALL_ATTRIBUTES, AUTHENTICATION_CONFIGURATION_RUNTIME_CAPABILITY);
    }

    static ResourceDefinition getAuthenticationContextDefinition() {
        AttributeDefinition[] attributes = new AttributeDefinition[] { CONTEXT_EXTENDS, MATCH_RULES };

        TrivialAddHandler<AuthenticationContext> add = new TrivialAddHandler<AuthenticationContext>(AuthenticationContext.class, attributes, AUTHENTICATION_CONTEXT_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<AuthenticationContext> getValueSupplier(ServiceBuilder<AuthenticationContext> serviceBuilder, OperationContext context, ModelNode model)
                    throws OperationFailedException {
                String parent = CONTEXT_EXTENDS.resolveModelAttribute(context, model).asStringOrNull();
                Supplier<AuthenticationContext> parentSupplier;
                if (parent != null) {
                    InjectedValue<AuthenticationContext> parentInjector = new InjectedValue<>();

                    serviceBuilder.addDependency(context.getCapabilityServiceName(
                            RuntimeCapability.buildDynamicCapabilityName(AUTHENTICATION_CONTEXT_CAPABILITY, parent), AuthenticationContext.class),
                            AuthenticationContext.class, parentInjector);

                    parentSupplier = parentInjector::getValue;
                } else {
                    parentSupplier = AuthenticationContext::empty;
                }

                Function<AuthenticationContext, AuthenticationContext> authContext = Function.identity();

                if (model.hasDefined(ElytronDescriptionConstants.MATCH_RULES)) {
                    List<ModelNode> nodes = model.require(ElytronDescriptionConstants.MATCH_RULES).asList();
                    for (ModelNode current : nodes) {
                        String authenticationConfiguration = AUTHENTICATION_CONFIGURATION.resolveModelAttribute(context, current).asStringOrNull();
                        String sslContext = SSL_CONTEXT.resolveModelAttribute(context, current).asStringOrNull();
                        if (authenticationConfiguration == null && sslContext == null) {
                            continue;
                        }

                        Function<MatchRule, MatchRule> matchRule = ignored -> MatchRule.ALL;

                        String abstractType = MATCH_ABSTRACT_TYPE.resolveModelAttribute(context, current).asStringOrNull();
                        String abstractTypeAuthority = MATCH_ABSTRACT_TYPE_AUTHORITY.resolveModelAttribute(context, current).asStringOrNull();
                        matchRule = abstractType != null || abstractTypeAuthority != null ? matchRule.andThen(m -> m.matchAbstractType(abstractType, abstractTypeAuthority))  : matchRule;

                        ModelNode host = MATCH_HOST.resolveModelAttribute(context, current);
                        matchRule = host.isDefined() ? matchRule.andThen(m -> m.matchHost(host.asString())) : matchRule;

                        ModelNode localSecurityDomain = MATCH_LOCAL_SECURITY_DOMAIN.resolveModelAttribute(context, current);
                        matchRule = localSecurityDomain.isDefined() ? matchRule.andThen(m -> m.matchLocalSecurityDomain(localSecurityDomain.asString())) : matchRule;

                        ModelNode matchNoUser = MATCH_NO_USER.resolveModelAttribute(context, current);
                        matchRule = matchNoUser.asBoolean() ? matchRule.andThen(m -> m.matchNoUser()) : matchRule;

                        ModelNode path = MATCH_PATH.resolveModelAttribute(context, current);
                        matchRule = path.isDefined() ? matchRule.andThen(m -> m.matchPath(path.asString())) : matchRule;

                        ModelNode port = MATCH_PORT.resolveModelAttribute(context, current);
                        matchRule = port.isDefined() ? matchRule.andThen(m -> m.matchPort(port.asInt())) : matchRule;

                        ModelNode protocol = MATCH_PROTOCOL.resolveModelAttribute(context, current);
                        matchRule = protocol.isDefined() ? matchRule.andThen(m -> m.matchProtocol(protocol.asString())) : matchRule;

                        ModelNode urn = MATCH_URN.resolveModelAttribute(context, current);
                        matchRule = urn.isDefined() ? matchRule.andThen(m -> m.matchUrnName(urn.asString())) : matchRule;

                        ModelNode user = MATCH_USER.resolveModelAttribute(context, current);
                        matchRule = user.isDefined() ? matchRule.andThen(m -> m.matchUser(user.asString())) : matchRule;

                        final Function<MatchRule, MatchRule> finalMatchRule = matchRule;
                        Supplier<MatchRule> matchRuleSuppler = new OneTimeSupplier<>(() -> finalMatchRule.apply(null));

                        if (authenticationConfiguration != null) {
                            InjectedValue<AuthenticationConfiguration> authenticationConfigurationInjector = new InjectedValue<>();

                            serviceBuilder.addDependency(context.getCapabilityServiceName(
                                    RuntimeCapability.buildDynamicCapabilityName(AUTHENTICATION_CONFIGURATION_CAPABILITY, authenticationConfiguration), AuthenticationConfiguration.class),
                                    AuthenticationConfiguration.class, authenticationConfigurationInjector);

                            authContext = authContext.andThen(a -> a.with(matchRuleSuppler.get(), authenticationConfigurationInjector.getValue()));
                        }

                        if (sslContext != null) {
                            InjectedValue<SSLContext> sslContextInjector = new InjectedValue<>();

                            serviceBuilder.addDependency(context.getCapabilityServiceName(
                                    RuntimeCapability.buildDynamicCapabilityName(SSL_CONTEXT_CAPABILITY, sslContext), SSLContext.class),
                                    SSLContext.class, sslContextInjector);

                            authContext = authContext.andThen(a -> a.withSsl(matchRuleSuppler.get(), sslContextInjector::getValue));
                        }
                    }
                }

                final Function<AuthenticationContext, AuthenticationContext> finalContext = authContext;
                return () -> finalContext.apply(parentSupplier.get());
            }

        };

        return new TrivialResourceDefinition(ElytronDescriptionConstants.AUTHENTICATION_CONTEXT, add, attributes,
                AUTHENTICATION_CONTEXT_RUNTIME_CAPABILITY);
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
