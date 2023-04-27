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
import static org.wildfly.extension.elytron.ElytronCommonCapabilities.KEY_STORE_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronCommonCapabilities.MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronCommonCapabilities.SECURITY_REALM_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronCommonCapabilities.SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronCommonCapabilities.SSL_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronCommonConstants.JWT;
import static org.wildfly.extension.elytron.ElytronCommonConstants.OAUTH2_INTROSPECTION;
import static org.wildfly.extension.elytron.TokenRealmDefinition.JwtValidatorAttributes.AUDIENCE;
import static org.wildfly.extension.elytron.TokenRealmDefinition.JwtValidatorAttributes.CERTIFICATE;
import static org.wildfly.extension.elytron.TokenRealmDefinition.JwtValidatorAttributes.ISSUER;
import static org.wildfly.extension.elytron.TokenRealmDefinition.JwtValidatorAttributes.KEY_MAP;
import static org.wildfly.extension.elytron.TokenRealmDefinition.JwtValidatorAttributes.KEY_STORE;
import static org.wildfly.extension.elytron.TokenRealmDefinition.JwtValidatorAttributes.PUBLIC_KEY;
import static org.wildfly.extension.elytron._private.ElytronCommonMessages.ROOT_LOGGER;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshallers;
import org.jboss.as.controller.AttributeParsers;
import org.jboss.as.controller.MapAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.parsing.ParseUtils;
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
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.wildfly.common.iteration.CodePointIterator;
import org.wildfly.extension.elytron.TokenRealmDefinition.OAuth2IntrospectionValidatorAttributes.HostnameVerificationPolicy;
import org.wildfly.security.auth.realm.token.TokenSecurityRealm;
import org.wildfly.security.auth.realm.token.validator.JwtValidator;
import org.wildfly.security.auth.realm.token.validator.OAuth2IntrospectValidator;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.pem.Pem;
import org.wildfly.security.pem.PemEntry;

/**
 * A {@link ResourceDefinition} for a {@link SecurityRealm} capable of validating and extracting identities from security tokens.
 *
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
class TokenRealmDefinition extends SimpleResourceDefinition {

    static final SimpleAttributeDefinition PRINCIPAL_CLAIM = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.PRINCIPAL_CLAIM, ModelType.STRING, true)
            .setDefaultValue(new ModelNode("username"))
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    protected static final SimpleAttributeDefinition SSL_CONTEXT = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.CLIENT_SSL_CONTEXT, ModelType.STRING, true)
            .setCapabilityReference(SSL_CONTEXT_CAPABILITY, SECURITY_REALM_CAPABILITY)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setValidator(new StringLengthValidator(1))
            .build();

    static final SimpleAttributeDefinition HOSTNAME_VERIFICATION_POLICY = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.HOST_NAME_VERIFICATION_POLICY, ModelType.STRING, true)
            .setValidator(EnumValidator.create(HostnameVerificationPolicy.class))
            .setAllowExpression(true)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static class JwtValidatorAttributes {

        static final StringListAttributeDefinition ISSUER = new StringListAttributeDefinition.Builder(ElytronCommonConstants.ISSUER)
                .setAllowExpression(true)
                .setRequired(false)
                .setMinSize(1)
                .build();

        static final StringListAttributeDefinition AUDIENCE = new StringListAttributeDefinition.Builder(ElytronCommonConstants.AUDIENCE)
                .setAllowExpression(true)
                .setRequired(false)
                .setMinSize(1)
                .build();

        static final SimpleAttributeDefinition PUBLIC_KEY = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.PUBLIC_KEY, ModelType.STRING, true)
                .setAlternatives(ElytronCommonConstants.KEY_STORE, ElytronCommonConstants.CERTIFICATE)
                .setAllowExpression(true)
                .setMinSize(1)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition KEY_STORE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.KEY_STORE, ModelType.STRING, true)
                .setAlternatives(ElytronCommonConstants.PUBLIC_KEY)
                .setRequires(ElytronCommonConstants.CERTIFICATE)
                .setMinSize(1)
                .setCapabilityReference(KEY_STORE_CAPABILITY, SECURITY_REALM_CAPABILITY)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .setAllowExpression(false)
                .build();

        static final SimpleAttributeDefinition CERTIFICATE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.CERTIFICATE, ModelType.STRING, true)
                .setAlternatives(ElytronCommonConstants.PUBLIC_KEY)
                .setRequires(KEY_STORE.getName())
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .setMinSize(1)
                .build();

        static final PropertiesAttributeDefinition KEY_MAP = new PropertiesAttributeDefinition.Builder(ElytronCommonConstants.KEY_MAP, true)
                .setAllowExpression(true)
                .setMinSize(1)
                .setAttributeParser(new AttributeParsers.PropertiesParser(null, ElytronCommonConstants.KEY, false) {
                    @Override
                    public void parseSingleElement(MapAttributeDefinition attribute, XMLExtendedStreamReader reader, ModelNode operation) throws XMLStreamException {
                        final String[] array = ParseUtils.requireAttributes(reader, ElytronCommonConstants.KID, ElytronCommonConstants.PUBLIC_KEY);
                        ModelNode paramVal = operation.get(attribute.getName()).get(array[0]);
                        paramVal.set(array[1]);
                        ParseUtils.requireNoContent(reader);
                    }
                })
                .setAttributeMarshaller(new AttributeMarshallers.PropertiesAttributeMarshaller(null, null, false) {
                    @Override
                    public void marshallSingleElement(AttributeDefinition attribute, ModelNode property, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
                        writer.writeEmptyElement(ElytronCommonConstants.KEY);
                        writer.writeAttribute(ElytronCommonConstants.KID, property.asProperty().getName());
                        writer.writeAttribute(ElytronCommonConstants.PUBLIC_KEY, property.asProperty().getValue().asString());
                    }
                })
                .setRestartAllServices()
                .build();

        static final ObjectTypeAttributeDefinition JWT_VALIDATOR = new ObjectTypeAttributeDefinition.Builder(JWT, ISSUER, AUDIENCE, PUBLIC_KEY, KEY_STORE, CERTIFICATE, SSL_CONTEXT, HOSTNAME_VERIFICATION_POLICY, KEY_MAP)
                .setRequired(false)
                .setRestartAllServices()
                .build();
    }

    static class OAuth2IntrospectionValidatorAttributes {

        static final SimpleAttributeDefinition CLIENT_ID = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.CLIENT_ID, ModelType.STRING, false)
                .setAllowExpression(true)
                .setMinSize(1)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition CLIENT_SECRET = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.CLIENT_SECRET, ModelType.STRING, false)
                .setAllowExpression(true)
                .setMinSize(1)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition INTROSPECTION_URL = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.INTROSPECTION_URL, ModelType.STRING, false)
                .setAllowExpression(true)
                .setValidator(new URLValidator())
                .setMinSize(1)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{CLIENT_ID, CLIENT_SECRET, INTROSPECTION_URL, SSL_CONTEXT, HOSTNAME_VERIFICATION_POLICY};

        static final ObjectTypeAttributeDefinition OAUTH2_INTROSPECTION_VALIDATOR = new ObjectTypeAttributeDefinition.Builder(OAUTH2_INTROSPECTION, CLIENT_ID, CLIENT_SECRET, INTROSPECTION_URL, SSL_CONTEXT, HOSTNAME_VERIFICATION_POLICY)
                .setRequired(false)
                .setRestartAllServices()
                .build();

        enum HostnameVerificationPolicy {
            ANY((s, sslSession) -> true),
            DEFAULT(HttpsURLConnection.getDefaultHostnameVerifier());

            private final HostnameVerifier verifier;

            HostnameVerificationPolicy(HostnameVerifier verifier) {
                this.verifier = verifier;
            }

            HostnameVerifier getVerifier() {
                return verifier;
            }
        }
    }

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{PRINCIPAL_CLAIM, JwtValidatorAttributes.JWT_VALIDATOR, OAuth2IntrospectionValidatorAttributes.OAUTH2_INTROSPECTION_VALIDATOR};

    private static final AbstractAddStepHandler ADD = new RealmAddHandler();
    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY, SECURITY_REALM_RUNTIME_CAPABILITY);

    TokenRealmDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronCommonConstants.TOKEN_REALM),
                                    ElytronExtension.getResourceDescriptionResolver(ElytronCommonConstants.TOKEN_REALM))
                      .setAddHandler(ADD)
                      .setRemoveHandler(REMOVE)
                      .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                      .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                      .setCapabilities(MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY, SECURITY_REALM_RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        OperationStepHandler handler = new ElytronReloadRequiredWriteAttributeHandler(ATTRIBUTES);
        for (AttributeDefinition attr : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(attr, null, handler);
        }
    }

    private static class RealmAddHandler extends ElytronCommonBaseAddHandler {

        private RealmAddHandler() {
            super(new HashSet<>(Arrays.asList(MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY, SECURITY_REALM_RUNTIME_CAPABILITY)), ATTRIBUTES);
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
                String publicKey = PUBLIC_KEY.resolveModelAttribute(context, jwtValidatorNode).asStringOrNull();
                InjectedValue<KeyStore> keyStoreInjector = new InjectedValue<>();
                String keyStoreName = KEY_STORE.resolveModelAttribute(context, jwtValidatorNode).asStringOrNull();
                String certificateAlias = CERTIFICATE.resolveModelAttribute(context, jwtValidatorNode).asStringOrNull();
                String sslContextRef = SSL_CONTEXT.resolveModelAttribute(context, jwtValidatorNode).asStringOrNull();
                String hostNameVerificationPolicy = HOSTNAME_VERIFICATION_POLICY.resolveModelAttribute(context, jwtValidatorNode).asStringOrNull();
                InjectedValue<SSLContext> sslContextInjector = new InjectedValue<>();
                ModelNode keyMap = KEY_MAP.resolveModelAttribute(context, jwtValidatorNode);
                Map<String, PublicKey> namedKeys = new LinkedHashMap<>();
                if (keyMap.isDefined()) {
                    Set<String> kids = keyMap.keys();

                    if (!kids.isEmpty()) {
                        for (String kid : kids) {
                            byte[] pemKey = keyMap.get(kid).asString().getBytes(StandardCharsets.UTF_8);

                            Iterator<PemEntry<?>> pemEntryIterator = Pem.parsePemContent(CodePointIterator.ofUtf8Bytes(pemKey));
                            PublicKey namedPublicKey = null;
                            try {
                                namedPublicKey = pemEntryIterator.next().tryCast(PublicKey.class);
                            } catch (Exception e) {
                                ROOT_LOGGER.debug(e);
                                throw ROOT_LOGGER.failedToParsePEMPublicKey(kid);
                            }
                            if (namedPublicKey == null) {
                                throw ROOT_LOGGER.failedToParsePEMPublicKey(kid);
                            }

                            namedKeys.put(kid, namedPublicKey);
                        }
                    }

                }

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
                        if (sslContextRef != null) {
                            jwtValidatorBuilder.useSslContext(sslContextInjector.getOptionalValue());
                        }
                        if (hostNameVerificationPolicy != null) {
                            jwtValidatorBuilder.useSslHostnameVerifier(HostnameVerificationPolicy.valueOf(hostNameVerificationPolicy).getVerifier());
                        }
                        if (namedKeys.size() > 0) {
                            jwtValidatorBuilder.publicKeys(namedKeys);
                        }
                        KeyStore keyStore = keyStoreInjector.getOptionalValue();

                        if (keyStore != null) {
                            try {
                                Certificate certificate = keyStore.getCertificate(certificateAlias);

                                if (certificate == null) {
                                    throw ROOT_LOGGER.unableToAccessEntryFromKeyStore(certificateAlias, keyStoreName);
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
                String keyStore = KEY_STORE.resolveModelAttribute(context, jwtValidatorNode).asStringOrNull();

                if (keyStore != null) {
                    serviceBuilder.addDependency(context.getCapabilityServiceName(
                            buildDynamicCapabilityName(KEY_STORE_CAPABILITY, keyStore), KeyStore.class),
                            KeyStore.class, keyStoreInjector);
                }

                if (sslContextRef != null) {
                    String runtimeCapability = RuntimeCapability.buildDynamicCapabilityName(SSL_CONTEXT_CAPABILITY, sslContextRef);
                    serviceBuilder.addDependency(context.getCapabilityServiceName(runtimeCapability, SSLContext.class), SSLContext.class, sslContextInjector);
                }

                serviceBuilder.addAliases(aliasServiceName).install();
            } else if (operation.hasDefined(OAUTH2_INTROSPECTION)) {
                ModelNode oAuth2IntrospectionNode = OAuth2IntrospectionValidatorAttributes.OAUTH2_INTROSPECTION_VALIDATOR.resolveModelAttribute(context, operation);
                String clientId = OAuth2IntrospectionValidatorAttributes.CLIENT_ID.resolveModelAttribute(context, oAuth2IntrospectionNode).asString();
                String clientSecret = OAuth2IntrospectionValidatorAttributes.CLIENT_SECRET.resolveModelAttribute(context, oAuth2IntrospectionNode).asString();
                String introspectionUrl = OAuth2IntrospectionValidatorAttributes.INTROSPECTION_URL.resolveModelAttribute(context, oAuth2IntrospectionNode).asString();
                String sslContextRef = SSL_CONTEXT.resolveModelAttribute(context, oAuth2IntrospectionNode).asStringOrNull();
                String hostNameVerificationPolicy = HOSTNAME_VERIFICATION_POLICY.resolveModelAttribute(context, oAuth2IntrospectionNode).asStringOrNull();
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
