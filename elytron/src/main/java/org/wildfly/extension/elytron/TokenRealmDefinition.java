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
import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SSL_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.JWT;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.OAUTH2_INTROSPECTION;
import static org.wildfly.extension.elytron.ElytronExtension.asStringIfDefined;
import static org.wildfly.extension.elytron.RealmDefinitions.CASE_SENSITIVE;
import static org.wildfly.extension.elytron.TokenRealmDefinition.JwtValidatorAttributes.AUDIENCE;
import static org.wildfly.extension.elytron.TokenRealmDefinition.JwtValidatorAttributes.CERTIFICATE;
import static org.wildfly.extension.elytron.TokenRealmDefinition.JwtValidatorAttributes.ISSUER;
import static org.wildfly.extension.elytron.TokenRealmDefinition.JwtValidatorAttributes.KEY_STORE;
import static org.wildfly.extension.elytron.TokenRealmDefinition.JwtValidatorAttributes.PUBLIC_KEY;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.elytron.TokenRealmDefinition.OAuth2IntrospectionValidatorAttributes.HostnameVerificationPolicy;
import org.wildfly.security.auth.realm.token.TokenSecurityRealm;
import org.wildfly.security.auth.realm.token.validator.JwtValidator;
import org.wildfly.security.auth.realm.token.validator.OAuth2IntrospectValidator;
import org.wildfly.security.auth.server.SecurityRealm;

/**
 * A {@link ResourceDefinition} for a {@link SecurityRealm} capable of validating and extracting identities from security tokens.
 *
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
class TokenRealmDefinition extends SimpleResourceDefinition {

    static final SimpleAttributeDefinition PRINCIPAL_CLAIM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PRINCIPAL_CLAIM, ModelType.STRING, true)
                                                                     .setDefaultValue(new ModelNode("username"))
                                                                     .setAllowExpression(true)
                                                                     .setMinSize(1)
                                                                     .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                                                                     .build();

    static class JwtValidatorAttributes {

        static final StringListAttributeDefinition ISSUER = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.ISSUER)
                .setAllowExpression(true)
                .setRequired(false)
                .setMinSize(1)
                .build();

        static final StringListAttributeDefinition AUDIENCE = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.AUDIENCE)
                .setAllowExpression(true)
                .setRequired(false)
                .setMinSize(1)
                .build();

        static final SimpleAttributeDefinition PUBLIC_KEY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PUBLIC_KEY, ModelType.STRING, true)
                .setAlternatives(ElytronDescriptionConstants.KEY_STORE, ElytronDescriptionConstants.CERTIFICATE)
                .setAllowExpression(true)
                .setMinSize(1)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition KEY_STORE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEY_STORE, ModelType.STRING, true)
                .setAlternatives(ElytronDescriptionConstants.PUBLIC_KEY)
                .setRequires(ElytronDescriptionConstants.CERTIFICATE)
                .setMinSize(1)
                .setCapabilityReference(KEY_STORE_CAPABILITY, SECURITY_REALM_CAPABILITY, true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .setAllowExpression(false)
                .build();

        static final SimpleAttributeDefinition CERTIFICATE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CERTIFICATE, ModelType.STRING, true)
                .setAlternatives(ElytronDescriptionConstants.PUBLIC_KEY)
                .setRequires(KEY_STORE.getName())
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .setMinSize(1)
                .build();

        static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{ISSUER, AUDIENCE, PUBLIC_KEY};

        static final ObjectTypeAttributeDefinition JWT_VALIDATOR = new ObjectTypeAttributeDefinition.Builder(JWT, ISSUER, AUDIENCE, PUBLIC_KEY, KEY_STORE, CERTIFICATE)
                                                                           .setAllowNull(true)
                                                                           .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                                                                           .build();
    }

    static class OAuth2IntrospectionValidatorAttributes {

        static final SimpleAttributeDefinition CLIENT_ID = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CLIENT_ID, ModelType.STRING, false)
                .setAllowExpression(true)
                .setMinSize(1)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition CLIENT_SECRET = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CLIENT_SECRET, ModelType.STRING, false)
                .setAllowExpression(true)
                .setMinSize(1)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition INTROSPECTION_URL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.INTROSPECTION_URL, ModelType.STRING, false)
                .setAllowExpression(true)
                .setValidator(new URLValidator())
                .setMinSize(1)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        protected static final SimpleAttributeDefinition SSL_CONTEXT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CLIENT_SSL_CONTEXT, ModelType.STRING, true)
                .setCapabilityReference(SSL_CONTEXT_CAPABILITY, SECURITY_REALM_CAPABILITY, true)
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .setValidator(new StringLengthValidator(1))
                .build();

        static final SimpleAttributeDefinition HOSTNAME_VERIFICATION_POLICY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.HOST_NAME_VERIFICATION_POLICY, ModelType.STRING, true)
                .setValidator(new EnumValidator<>(HostnameVerificationPolicy.class, true, true))
                .setAllowExpression(true)
                .setMinSize(1)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{CLIENT_ID, CLIENT_SECRET, INTROSPECTION_URL, SSL_CONTEXT, HOSTNAME_VERIFICATION_POLICY};

        static final ObjectTypeAttributeDefinition OAUTH2_INTROSPECTION_VALIDATOR = new ObjectTypeAttributeDefinition.Builder(OAUTH2_INTROSPECTION, CLIENT_ID, CLIENT_SECRET, INTROSPECTION_URL, SSL_CONTEXT, HOSTNAME_VERIFICATION_POLICY)
                .setAllowNull(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        enum HostnameVerificationPolicy {
            ANY((s, sslSession) -> true);

            private final HostnameVerifier verifier;

            HostnameVerificationPolicy(HostnameVerifier verifier) {
                this.verifier = verifier;
            }

            HostnameVerifier getVerifier() {
                return verifier;
            }
        }
    }

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{PRINCIPAL_CLAIM, JwtValidatorAttributes.JWT_VALIDATOR, OAuth2IntrospectionValidatorAttributes.OAUTH2_INTROSPECTION_VALIDATOR, CASE_SENSITIVE};

    private static final AbstractAddStepHandler ADD = new RealmAddHandler();
    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY, SECURITY_REALM_RUNTIME_CAPABILITY);

    TokenRealmDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.TOKEN_REALM),
                                    ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.TOKEN_REALM))
                      .setAddHandler(ADD)
                      .setRemoveHandler(REMOVE)
                      .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                      .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                      .setCapabilities(MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY, SECURITY_REALM_RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        OperationStepHandler handler = new WriteAttributeHandler();
        for (AttributeDefinition attr : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(attr, null, handler);
        }
    }

    private static class RealmAddHandler extends BaseAddHandler {

        private RealmAddHandler() {
            super(new HashSet<>(Arrays.asList(new RuntimeCapability[]{
                    MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY, SECURITY_REALM_RUNTIME_CAPABILITY})), ATTRIBUTES);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            ServiceTarget serviceTarget = context.getServiceTarget();
            String address = context.getCurrentAddressValue();
            ServiceName mainServiceName = MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(address).getCapabilityServiceName();
            ServiceName aliasServiceName = SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(address).getCapabilityServiceName();
            ModelNode principalClaimNode = PRINCIPAL_CLAIM.resolveModelAttribute(context, operation);
            TrivialService<SecurityRealm> service;

            if (operation.hasDefined(JWT)) {
                ModelNode jwtValidatorNode = JwtValidatorAttributes.JWT_VALIDATOR.resolveModelAttribute(context, operation);
                String[] issuer = asStringArrayIfDefined(context, ISSUER, jwtValidatorNode);
                String[] audience = asStringArrayIfDefined(context, AUDIENCE, jwtValidatorNode);
                String publicKey = ElytronExtension.asStringIfDefined(context, PUBLIC_KEY, jwtValidatorNode);
                InjectedValue<KeyStore> keyStoreInjector = new InjectedValue<>();
                String keyStoreName = asStringIfDefined(context, KEY_STORE, jwtValidatorNode);
                String certificateAlias = asStringIfDefined(context, CERTIFICATE, jwtValidatorNode);

                service = new TrivialService<>(new TrivialService.ValueSupplier<SecurityRealm>() {
                    @Override
                    public SecurityRealm get() throws StartException {
                        JwtValidator.Builder jwtValidatorBuilder = JwtValidator.builder();

                        if (issuer != null) {
                            jwtValidatorBuilder.issuer(issuer);
                        }

                        if (audience != null) {
                            jwtValidatorBuilder.audience(audience);
                        }

                        if (publicKey != null) {
                            jwtValidatorBuilder.publicKey(publicKey.getBytes(StandardCharsets.UTF_8));
                        }

                        KeyStore keyStore = keyStoreInjector.getOptionalValue();

                        if (keyStore != null) {
                            try {
                                Certificate certificate = keyStore.getCertificate(certificateAlias);

                                if (certificate == null) {
                                    throw ROOT_LOGGER.unableToAccessEntryFromKeyStore(keyStoreName, certificateAlias);
                                }

                                jwtValidatorBuilder.publicKey(certificate.getPublicKey());
                            } catch (KeyStoreException cause) {
                                throw ROOT_LOGGER.unableToStartService(cause);
                            }
                        }

                        return TokenSecurityRealm.builder().principalClaimName(principalClaimNode.asString())
                                       .validator(jwtValidatorBuilder.build())
                                       .build();
                    }

                    @Override
                    public void dispose() {
                    }
                });

                ServiceBuilder<SecurityRealm> serviceBuilder = serviceTarget.addService(mainServiceName, service);
                String keyStore = asStringIfDefined(context, KEY_STORE, jwtValidatorNode);

                if (keyStore != null) {
                    serviceBuilder.addDependency(context.getCapabilityServiceName(
                            buildDynamicCapabilityName(KEY_STORE_CAPABILITY, keyStore), KeyStore.class),
                            KeyStore.class, keyStoreInjector);
                }

                serviceBuilder.addAliases(aliasServiceName).install();
            } else if (operation.hasDefined(OAUTH2_INTROSPECTION)) {
                ModelNode oAuth2IntrospectionNode = OAuth2IntrospectionValidatorAttributes.OAUTH2_INTROSPECTION_VALIDATOR.resolveModelAttribute(context, operation);
                String clientId = ElytronExtension.asStringIfDefined(context, OAuth2IntrospectionValidatorAttributes.CLIENT_ID, oAuth2IntrospectionNode);
                String clientSecret = ElytronExtension.asStringIfDefined(context, OAuth2IntrospectionValidatorAttributes.CLIENT_SECRET, oAuth2IntrospectionNode);
                String introspectionUrl = ElytronExtension.asStringIfDefined(context, OAuth2IntrospectionValidatorAttributes.INTROSPECTION_URL, oAuth2IntrospectionNode);
                String sslContextRef = ElytronExtension.asStringIfDefined(context, OAuth2IntrospectionValidatorAttributes.SSL_CONTEXT, oAuth2IntrospectionNode);
                String hostNameVerificationPolicy = ElytronExtension.asStringIfDefined(context, OAuth2IntrospectionValidatorAttributes.HOSTNAME_VERIFICATION_POLICY, oAuth2IntrospectionNode);
                InjectedValue<SSLContext> sslContextInjector = new InjectedValue<>();

                service = new TrivialService<>(new TrivialService.ValueSupplier<SecurityRealm>() {
                    @Override
                    public SecurityRealm get() throws StartException {
                        try {
                            HostnameVerifier verifier = null;
                            if (hostNameVerificationPolicy != null) {
                                verifier = HostnameVerificationPolicy.valueOf(hostNameVerificationPolicy).getVerifier();
                            }
                            OAuth2IntrospectValidator.Builder builder = OAuth2IntrospectValidator.builder().clientId(clientId).clientSecret(clientSecret)
                                    .tokenIntrospectionUrl(new URL(introspectionUrl))
                                    .useSslContext(sslContextInjector.getOptionalValue())
                                    .useSslHostnameVerifier(verifier);
                            return TokenSecurityRealm.builder().principalClaimName(principalClaimNode.asString())
                                    .validator(builder.build())
                                    .build();
                        } catch (MalformedURLException e) {
                            throw new RuntimeException("Failed to parse token introspection URL.", e);
                        }
                    }

                    @Override
                    public void dispose() {
                    }
                });

                ServiceBuilder<SecurityRealm> serviceBuilder = serviceTarget.addService(mainServiceName, service).addAliases(aliasServiceName);

                if (sslContextRef != null) {
                    String runtimeCapability = RuntimeCapability.buildDynamicCapabilityName(SSL_CONTEXT_CAPABILITY, sslContextRef);
                    serviceBuilder.addDependency(context.getCapabilityServiceName(runtimeCapability, SSLContext.class), SSLContext.class, sslContextInjector);
                }

                serviceBuilder.install();
            }
        }

        private String[] asStringArrayIfDefined(OperationContext context, StringListAttributeDefinition attributeDefinition, ModelNode model) throws OperationFailedException {
            ModelNode resolved = attributeDefinition.resolveModelAttribute(context, model);
            if (resolved.isDefined()) {
                List<ModelNode> values = resolved.asList();
                String[] response = new String[values.size()];
                for (int i = 0; i < response.length; i++) {
                    response[i] = values.get(i).asString();
                }
                return response;
            }
            return null;
        }
    }

    private static class WriteAttributeHandler extends ElytronRestartParentWriteAttributeHandler {

        WriteAttributeHandler() {
            super(ElytronDescriptionConstants.TOKEN_REALM, ATTRIBUTES);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress parentAddress) {
            final String name = parentAddress.getLastElement().getValue();
            return MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(name).getCapabilityServiceName();
        }
    }

    private static class URLValidator extends StringLengthValidator {

        private URLValidator() {
            super(1, false, false);
        }

        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            super.validateParameter(parameterName, value);

            String url = value.asString();

            try {
                new URL(url);
            } catch (MalformedURLException e) {
                throw ROOT_LOGGER.invalidURL(url, e);
            }
        }
    }
}
