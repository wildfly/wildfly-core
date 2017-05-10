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

import static org.wildfly.common.Assert.checkNotNullParam;
import static org.wildfly.extension.elytron.Capabilities.AUTHENTICATION_CONFIGURATION_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.AUTHENTICATION_CONFIGURATION_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.AUTHENTICATION_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.AUTHENTICATION_CONTEXT_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_DOMAIN_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_FACTORY_CREDENTIAL_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SSL_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronExtension.asIntIfDefined;
import static org.wildfly.extension.elytron.ElytronExtension.asStringIfDefined;
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
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.AttributeAccess;
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
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilityReference(AUTHENTICATION_CONFIGURATION_CAPABILITY, AUTHENTICATION_CONFIGURATION_RUNTIME_CAPABILITY)
            .build();

    static final SimpleAttributeDefinition ANONYMOUS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ANONYMOUS, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(false))
            .setAlternatives(ElytronDescriptionConstants.AUTHENTICATION_NAME, ElytronDescriptionConstants.SECURITY_DOMAIN, ElytronDescriptionConstants.KERBEROS_SECURITY_FACTORY)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition AUTHENTICATION_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.AUTHENTICATION_NAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .setAlternatives(ElytronDescriptionConstants.ANONYMOUS, ElytronDescriptionConstants.SECURITY_DOMAIN, ElytronDescriptionConstants.KERBEROS_SECURITY_FACTORY)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition AUTHORIZATION_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.AUTHORIZATION_NAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition HOST = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.HOST, ModelType.STRING, true)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition PROTOCOL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PROTOCOL, ModelType.STRING, true)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition PORT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PORT, ModelType.INT, true)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition REALM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REALM, ModelType.STRING, true)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition SECURITY_DOMAIN = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SECURITY_DOMAIN, ModelType.STRING, true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setAlternatives(ElytronDescriptionConstants.ANONYMOUS, ElytronDescriptionConstants.AUTHENTICATION_NAME, ElytronDescriptionConstants.KERBEROS_SECURITY_FACTORY)
            .setCapabilityReference(SECURITY_DOMAIN_CAPABILITY, AUTHENTICATION_CONFIGURATION_RUNTIME_CAPABILITY)
            .build();

    static final SimpleAttributeDefinition ALLOW_ALL_MECHANISMS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALLOW_ALL_MECHANISMS, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(false))
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setAlternatives(ElytronDescriptionConstants.ALLOW_SASL_MECHANISMS)
            .build();

    static final StringListAttributeDefinition ALLOW_SASL_MECHANISMS = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.ALLOW_SASL_MECHANISMS)
            .setMinSize(0)
            .setRequired(false)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setAlternatives(ElytronDescriptionConstants.ALLOW_ALL_MECHANISMS)
            .build();

    static final StringListAttributeDefinition FORBID_SASL_MECHANISMS = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.FORBID_SASL_MECHANISMS)
            .setMinSize(0)
            .setRequired(false)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleMapAttributeDefinition MECHANISM_PROPERTIES = new SimpleMapAttributeDefinition.Builder(CommonAttributes.PROPERTIES)
            .setName(ElytronDescriptionConstants.MECHANISM_PROPERTIES)
            .setXmlName(ElytronDescriptionConstants.MECHANISM_PROPERTIES)
            .build();

    static final ObjectTypeAttributeDefinition CREDENTIAL_REFERENCE = CredentialReference.getAttributeBuilder(true, true)
            .build();

    static final SimpleAttributeDefinition KERBEROS_SECURITY_FACTORY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KERBEROS_SECURITY_FACTORY, ModelType.STRING, true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setAlternatives(ElytronDescriptionConstants.ANONYMOUS, ElytronDescriptionConstants.AUTHENTICATION_NAME, ElytronDescriptionConstants.SECURITY_DOMAIN)
            .setCapabilityReference(SECURITY_FACTORY_CREDENTIAL_CAPABILITY, AUTHENTICATION_CONFIGURATION_RUNTIME_CAPABILITY)
            .build();

    static final AttributeDefinition[] AUTHENTICATION_CONFIGURATION_SIMPLE_ATTRIBUTES = new AttributeDefinition[] { CONFIGURATION_EXTENDS, ANONYMOUS, AUTHENTICATION_NAME, AUTHORIZATION_NAME, HOST, PROTOCOL,
            PORT, REALM, SECURITY_DOMAIN, ALLOW_ALL_MECHANISMS, ALLOW_SASL_MECHANISMS, FORBID_SASL_MECHANISMS, KERBEROS_SECURITY_FACTORY };

    static final AttributeDefinition[] AUTHENTICATION_CONFIGURATION_ALL_ATTRIBUTES = new AttributeDefinition[] { CONFIGURATION_EXTENDS, ANONYMOUS, AUTHENTICATION_NAME, AUTHORIZATION_NAME, HOST, PROTOCOL,
            PORT, REALM, SECURITY_DOMAIN, ALLOW_ALL_MECHANISMS, ALLOW_SASL_MECHANISMS, FORBID_SASL_MECHANISMS, KERBEROS_SECURITY_FACTORY, MECHANISM_PROPERTIES, CREDENTIAL_REFERENCE };

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
            .setDefaultValue(new ModelNode(false))
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

    static final SimpleAttributeDefinition MATCH_PURPOSE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MATCH_PURPOSE, ModelType.STRING, true)
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
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
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
            MATCH_ABSTRACT_TYPE, MATCH_ABSTRACT_TYPE_AUTHORITY, MATCH_HOST, MATCH_LOCAL_SECURITY_DOMAIN, MATCH_NO_USER, MATCH_PATH, MATCH_PORT, MATCH_PROTOCOL, MATCH_PURPOSE, MATCH_URN, MATCH_USER,
            AUTHENTICATION_CONFIGURATION, SSL_CONTEXT).build();

    static final ObjectListAttributeDefinition MATCH_RULES = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.MATCH_RULES, MATCH_RULE)
            .setRequired(false)
            .build();

    static ResourceDefinition getAuthenticationClientDefinition() {

        TrivialAddHandler<AuthenticationConfiguration> add = new TrivialAddHandler<AuthenticationConfiguration>(AuthenticationConfiguration.class, AUTHENTICATION_CONFIGURATION_ALL_ATTRIBUTES,
                AUTHENTICATION_CONFIGURATION_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<AuthenticationConfiguration> getValueSupplier(
                    ServiceBuilder<AuthenticationConfiguration> serviceBuilder, OperationContext context, ModelNode model)
                    throws OperationFailedException {
                String parent = asStringIfDefined(context, CONFIGURATION_EXTENDS, model);
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

                String authenticationName = asStringIfDefined(context, AUTHENTICATION_NAME, model);
                configuration = authenticationName != null ? configuration.andThen(c -> c.useName(authenticationName)) : configuration;

                String authorizationName = asStringIfDefined(context, AUTHORIZATION_NAME, model);
                configuration = authorizationName != null ? configuration.andThen(c -> c.useAuthorizationName(authorizationName)) : configuration;

                String host = asStringIfDefined(context, HOST, model);
                configuration = host != null ? configuration.andThen(c -> c.useHost(host)) : configuration;

                String protocol = asStringIfDefined(context, PROTOCOL, model);
                configuration = protocol != null ? configuration.andThen(c -> c.useProtocol(protocol)) : configuration;

                int port = asIntIfDefined(context, PORT, model);
                configuration = port > 0 ? configuration.andThen(c -> c.usePort(port)) : configuration;

                String realm = asStringIfDefined(context, REALM, model);
                configuration = realm != null ? configuration.andThen(c -> c.useRealm(realm)) : configuration;

                String securityDomain = asStringIfDefined(context, SECURITY_DOMAIN, model);
                if (securityDomain != null) {
                    InjectedValue<SecurityDomain> securityDomainInjector = new InjectedValue<>();
                    serviceBuilder.addDependency(context.getCapabilityServiceName(SECURITY_DOMAIN_CAPABILITY, securityDomain, SecurityDomain.class), SecurityDomain.class, securityDomainInjector);
                    configuration = configuration.andThen(c -> c.useForwardedIdentity(securityDomainInjector.getValue()));
                }

                boolean allowAllMechanisms = ALLOW_ALL_MECHANISMS.resolveModelAttribute(context, model).asBoolean();
                configuration = allowAllMechanisms ? configuration.andThen(c -> c.allowAllSaslMechanisms()) : configuration;

                List<String> allowedMechanisms = ALLOW_SASL_MECHANISMS.unwrap(context, model);
                configuration = allowedMechanisms.size() > 0 ? configuration.andThen(c -> c.allowSaslMechanisms(allowedMechanisms.toArray(new String[allowedMechanisms.size()]))) : configuration;

                List<String> forbiddenMechanisms = FORBID_SASL_MECHANISMS.unwrap(context, model);
                configuration = forbiddenMechanisms.size() > 0 ? configuration.andThen(c -> c.forbidSaslMechanisms(forbiddenMechanisms.toArray(new String[forbiddenMechanisms.size()]))) : configuration;

                String kerberosSecurityFactory = asStringIfDefined(context, KERBEROS_SECURITY_FACTORY, model);
                if (kerberosSecurityFactory != null) {
                    InjectedValue<CredentialSecurityFactory> kerberosFactoryInjector = new InjectedValue<>();
                    serviceBuilder.addDependency(context.getCapabilityServiceName(SECURITY_FACTORY_CREDENTIAL_CAPABILITY, kerberosSecurityFactory, CredentialSecurityFactory.class),
                            CredentialSecurityFactory.class, kerberosFactoryInjector);
                    configuration = configuration.andThen(c -> c.useKerberosSecurityFactory(kerberosFactoryInjector.getValue()));
                }

                ModelNode properties = MECHANISM_PROPERTIES.resolveModelAttribute(context, model);
                if (properties.isDefined()) {
                    Map<String, String> propertiesMap = new HashMap<String, String>();
                    properties.keys().forEach((String s) -> propertiesMap.put(s, properties.require(s).asString()));
                    configuration = configuration.andThen(c -> c.useMechanismProperties(propertiesMap));
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
                                return c.usePassword(cs.getCredential(PasswordCredential.class).getPassword());
                            } else {
                                throw ROOT_LOGGER.credentialCannotBeResolved();
                            }
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    });
                }

                final Function<AuthenticationConfiguration, AuthenticationConfiguration> finalConfiguration = configuration;
                return () -> finalConfiguration.apply(null);
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
                String parent = asStringIfDefined(context, CONTEXT_EXTENDS, model);
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
                        String authenticationConfiguration = asStringIfDefined(context, AUTHENTICATION_CONFIGURATION, current);
                        String sslContext = asStringIfDefined(context, SSL_CONTEXT, current);
                        if (authenticationConfiguration == null && sslContext == null) {
                            continue;
                        }

                        Function<MatchRule, MatchRule> matchRule = ignored -> MatchRule.ALL;

                        String abstractType = asStringIfDefined(context, MATCH_ABSTRACT_TYPE, current);
                        String abstractTypeAuthority = asStringIfDefined(context, MATCH_ABSTRACT_TYPE_AUTHORITY, current);
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

                        ModelNode purpose = MATCH_PURPOSE.resolveModelAttribute(context, current);
                        matchRule = purpose.isDefined() ? matchRule.andThen(m -> m.matchPurpose(purpose.asString())) : matchRule;

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
