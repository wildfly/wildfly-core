/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
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


import static org.wildfly.extension.elytron.Capabilities.AUTHENTICATION_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.ELYTRON_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.EVIDENCE_DECODER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PERMISSION_MAPPER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PRINCIPAL_DECODER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PROVIDERS_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.REALM_MAPPER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.ROLE_DECODER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.ROLE_MAPPER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_EVENT_LISTENER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_FACTORY_CREDENTIAL_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SSL_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronExtension.isServerOrHostController;
import static org.wildfly.extension.elytron.SecurityActions.doPrivileged;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.security.PrivilegedAction;
import java.security.Provider;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;
import javax.security.auth.message.config.AuthConfigFactory;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.ExpressionResolverExtension;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.AttachmentKey;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.access.constraint.ApplicationTypeConfig;
import org.jboss.as.controller.access.constraint.SensitivityClassification;
import org.jboss.as.controller.access.management.ApplicationTypeAccessConstraintDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.registry.RuntimePackageDependency;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.extension.elytron.capabilities.CredentialSecurityFactory;
import org.wildfly.extension.elytron.capabilities.PrincipalTransformer;
import org.wildfly.extension.elytron.capabilities._private.SecurityEventListener;
import org.wildfly.extension.elytron.expression.DeploymentExpressionResolverProcessor;
import org.wildfly.security.Version;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.jaspi.DelegatingAuthConfigFactory;
import org.wildfly.security.auth.jaspi.ElytronAuthConfigFactory;
import org.wildfly.security.auth.server.EvidenceDecoder;
import org.wildfly.security.auth.server.ModifiableSecurityRealm;
import org.wildfly.security.auth.server.PrincipalDecoder;
import org.wildfly.security.auth.server.RealmMapper;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.authz.PermissionMapper;
import org.wildfly.security.authz.RoleDecoder;
import org.wildfly.security.authz.RoleMapper;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.wildfly.security.manager.action.ReadPropertyAction;

/**
 * Top level {@link ResourceDefinition} for the Elytron subsystem.
 *
 * @author <a href="mailto:tcerar@redhat.com">Tomaz Cerar</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ElytronDefinition extends SimpleResourceDefinition {

    /**
     * System property which if set to {@code true} will cause the JVM wide default {@link SSLContext} to be restored when the subsystem shuts down.
     *
     * This property is only for use by test cases.
     */
    static final String RESTORE_DEFAULT_SSL_CONTEXT = ElytronDefinition.class.getPackage().getName() + ".restore-default-ssl-context";

    private static final AttachmentKey<SecurityPropertyService> SECURITY_PROPERTY_SERVICE_KEY = AttachmentKey.create(SecurityPropertyService.class);

    private static final AuthenticationContextDependencyProcessor AUTHENITCATION_CONTEXT_PROCESSOR = new AuthenticationContextDependencyProcessor();

    static final SimpleAttributeDefinition DEFAULT_AUTHENTICATION_CONTEXT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DEFAULT_AUTHENTICATION_CONTEXT, ModelType.STRING, true)
            .setCapabilityReference(AUTHENTICATION_CONTEXT_CAPABILITY, ELYTRON_RUNTIME_CAPABILITY)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition DEFAULT_SSL_CONTEXT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DEFAULT_SSL_CONTEXT, ModelType.STRING, true)
            .setCapabilityReference(SSL_CONTEXT_CAPABILITY, ELYTRON_RUNTIME_CAPABILITY)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition INITIAL_PROVIDERS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.INITIAL_PROVIDERS, ModelType.STRING, true)
            .setCapabilityReference(PROVIDERS_CAPABILITY, ELYTRON_RUNTIME_CAPABILITY)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition FINAL_PROVIDERS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.FINAL_PROVIDERS, ModelType.STRING, true)
            .setCapabilityReference(PROVIDERS_CAPABILITY, ELYTRON_RUNTIME_CAPABILITY)
            .setRestartAllServices()
            .build();

    static final StringListAttributeDefinition DISALLOWED_PROVIDERS = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.DISALLOWED_PROVIDERS)
            .setRequired(false)
            .setAttributeParser(AttributeParser.STRING_LIST)
            .setAttributeMarshaller(AttributeMarshaller.STRING_LIST)
            .setRestartJVM()
            .setElementValidator(new StringLengthValidator(1))
            .setAllowExpression(true)
            .build();

    static final SimpleAttributeDefinition REGISTER_JASPI_FACTORY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REGISTER_JASPI_FACTORY, ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.TRUE)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final PropertiesAttributeDefinition SECURITY_PROPERTIES = new PropertiesAttributeDefinition.Builder("security-properties", true)
            .build();

    private final AtomicReference<ExpressionResolverExtension> resolverReference;

    ElytronDefinition(AtomicReference<ExpressionResolverExtension> resolverReference) {
        super(new Parameters(ElytronExtension.SUBSYSTEM_PATH, ElytronExtension.getResourceDescriptionResolver())
                .setAddHandler(new ElytronAdd())
                .setRemoveHandler(new ElytronRemove())
                .setCapabilities(ELYTRON_RUNTIME_CAPABILITY)
                .addAccessConstraints(new SensitiveTargetAccessConstraintDefinition(new SensitivityClassification(ElytronExtension.SUBSYSTEM_NAME, ElytronDescriptionConstants.ELYTRON_SECURITY, true, true, true)),
                new ApplicationTypeAccessConstraintDefinition(new ApplicationTypeConfig(ElytronExtension.SUBSYSTEM_NAME, ElytronDescriptionConstants.ELYTRON_SECURITY, false))));
        this.resolverReference = resolverReference;
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        final boolean serverOrHostController = isServerOrHostController(resourceRegistration);

        // Expression Resolver
        resourceRegistration.registerSubModel(ExpressionResolverResourceDefinition.getExpressionResolverDefinition(resourceRegistration.getPathAddress(), resolverReference));

        // Provider Loader
        resourceRegistration.registerSubModel(ProviderDefinitions.getAggregateProvidersDefinition());
        resourceRegistration.registerSubModel(ProviderDefinitions.getProviderLoaderDefinition(serverOrHostController));

        // Audit
        resourceRegistration.registerSubModel(AuditResourceDefinitions.getAggregateSecurityEventListenerDefinition());
        resourceRegistration.registerSubModel(new CustomComponentDefinition<>(Consumer.class, SecurityEventListener::from,
                ElytronDescriptionConstants.CUSTOM_SECURITY_EVENT_LISTENER, SECURITY_EVENT_LISTENER_RUNTIME_CAPABILITY));
        resourceRegistration.registerSubModel(AuditResourceDefinitions.getFileAuditLogResourceDefinition());
        resourceRegistration.registerSubModel(AuditResourceDefinitions.getPeriodicRotatingFileAuditLogResourceDefinition());
        resourceRegistration.registerSubModel(AuditResourceDefinitions.getSizeRotatingFileAuditLogResourceDefinition());
        resourceRegistration.registerSubModel(AuditResourceDefinitions.getSyslogAuditLogResourceDefinition());

        // Security Domain SASL / HTTP Configurations
        resourceRegistration.registerSubModel(AuthenticationFactoryDefinitions.getSaslAuthenticationFactory());
        resourceRegistration.registerSubModel(AuthenticationFactoryDefinitions.getHttpAuthenticationFactory());

        // Domain
        resourceRegistration.registerSubModel(new DomainDefinition());

        // Security Realms
        resourceRegistration.registerSubModel(new AggregateRealmDefinition());
        resourceRegistration.registerSubModel(new CustomComponentDefinition<>(SecurityRealm.class, Function.identity(), ElytronDescriptionConstants.CUSTOM_REALM, SECURITY_REALM_RUNTIME_CAPABILITY));
        resourceRegistration.registerSubModel(ModifiableRealmDecorator.wrap(new CustomComponentDefinition<>(
                ModifiableSecurityRealm.class, Function.identity(), ElytronDescriptionConstants.CUSTOM_MODIFIABLE_REALM,
                MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY, SECURITY_REALM_RUNTIME_CAPABILITY)));
        resourceRegistration.registerSubModel(RealmDefinitions.getIdentityRealmDefinition());
        resourceRegistration.registerSubModel(new JdbcRealmDefinition());
        resourceRegistration.registerSubModel(new KeyStoreRealmDefinition());
        resourceRegistration.registerSubModel(PropertiesRealmDefinition.create(serverOrHostController));
        resourceRegistration.registerSubModel(new TokenRealmDefinition());
        resourceRegistration.registerSubModel(ModifiableRealmDecorator.wrap(new LdapRealmDefinition()));
        resourceRegistration.registerSubModel(ModifiableRealmDecorator.wrap(new FileSystemRealmDefinition()));
        resourceRegistration.registerSubModel(new CachingRealmDefinition());
        resourceRegistration.registerSubModel(new DistributedRealmDefinition());
        resourceRegistration.registerSubModel(new FailoverRealmDefinition());
        resourceRegistration.registerSubModel(new JaasRealmDefinition());

        // Security Factories
        resourceRegistration.registerSubModel(new CustomComponentDefinition<>(CredentialSecurityFactory.class, Function.identity(), ElytronDescriptionConstants.CUSTOM_CREDENTIAL_SECURITY_FACTORY, SECURITY_FACTORY_CREDENTIAL_RUNTIME_CAPABILITY));
        resourceRegistration.registerSubModel(KerberosSecurityFactoryDefinition.getKerberosSecurityFactoryDefinition());

        // Permission Mappers
        resourceRegistration.registerSubModel(new CustomComponentDefinition<>(PermissionMapper.class, Function.identity(), ElytronDescriptionConstants.CUSTOM_PERMISSION_MAPPER, PERMISSION_MAPPER_RUNTIME_CAPABILITY));
        resourceRegistration.registerSubModel(PermissionMapperDefinitions.getLogicalPermissionMapper());
        resourceRegistration.registerSubModel(PermissionMapperDefinitions.getSimplePermissionMapper());
        resourceRegistration.registerSubModel(PermissionMapperDefinitions.getConstantPermissionMapper());

        // Permission Sets
        resourceRegistration.registerSubModel(PermissionSetDefinition.getPermissionSet());

        // Principal Decoders
        resourceRegistration.registerSubModel(PrincipalDecoderDefinitions.getAggregatePrincipalDecoderDefinition());
        resourceRegistration.registerSubModel(PrincipalDecoderDefinitions.getConcatenatingPrincipalDecoder());
        resourceRegistration.registerSubModel(PrincipalDecoderDefinitions.getConstantPrincipalDecoder());
        resourceRegistration.registerSubModel(new CustomComponentDefinition<>(PrincipalDecoder.class, Function.identity(), ElytronDescriptionConstants.CUSTOM_PRINCIPAL_DECODER, PRINCIPAL_DECODER_RUNTIME_CAPABILITY));
        resourceRegistration.registerSubModel(PrincipalDecoderDefinitions.getX500AttributePrincipalDecoder());

        // Principal Transformers
        resourceRegistration.registerSubModel(PrincipalTransformerDefinitions.getAggregatePrincipalTransformerDefinition());
        resourceRegistration.registerSubModel(PrincipalTransformerDefinitions.getChainedPrincipalTransformerDefinition());
        resourceRegistration.registerSubModel(PrincipalTransformerDefinitions.getConstantPrincipalTransformerDefinition());
        resourceRegistration.registerSubModel(new CustomComponentDefinition<>(PrincipalTransformer.class, Function.identity(), ElytronDescriptionConstants.CUSTOM_PRINCIPAL_TRANSFORMER, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY));
        resourceRegistration.registerSubModel(PrincipalTransformerDefinitions.getRegexPrincipalTransformerDefinition());
        resourceRegistration.registerSubModel(PrincipalTransformerDefinitions.getRegexValidatingPrincipalTransformerDefinition());
        resourceRegistration.registerSubModel(PrincipalTransformerDefinitions.getCasePrincipalTransformerDefinition());

        // Realm Mappers
        resourceRegistration.registerSubModel(RealmMapperDefinitions.getConstantRealmMapper());
        resourceRegistration.registerSubModel(new CustomComponentDefinition<>(RealmMapper.class, Function.identity(), ElytronDescriptionConstants.CUSTOM_REALM_MAPPER, REALM_MAPPER_RUNTIME_CAPABILITY));
        resourceRegistration.registerSubModel(RealmMapperDefinitions.getMappedRegexRealmMapper());
        resourceRegistration.registerSubModel(RealmMapperDefinitions.getSimpleRegexRealmMapperDefinition());

        // Role Decoders
        resourceRegistration.registerSubModel(new CustomComponentDefinition<>(RoleDecoder.class, Function.identity(), ElytronDescriptionConstants.CUSTOM_ROLE_DECODER, ROLE_DECODER_RUNTIME_CAPABILITY));
        resourceRegistration.registerSubModel(RoleDecoderDefinitions.getSimpleRoleDecoderDefinition());
        resourceRegistration.registerSubModel(RoleDecoderDefinitions.getSourceAddressRoleDecoderDefinition());
        resourceRegistration.registerSubModel(RoleDecoderDefinitions.getAggregateRoleDecoderDefinition());

        // Role Mappers
        resourceRegistration.registerSubModel(RoleMapperDefinitions.getAddSuffixRoleMapperDefinition());
        resourceRegistration.registerSubModel(RoleMapperDefinitions.getAddPrefixRoleMapperDefinition());
        resourceRegistration.registerSubModel(RoleMapperDefinitions.getAggregateRoleMapperDefinition());
        resourceRegistration.registerSubModel(RoleMapperDefinitions.getConstantRoleMapperDefinition());
        resourceRegistration.registerSubModel(new CustomComponentDefinition<>(RoleMapper.class, Function.identity(), ElytronDescriptionConstants.CUSTOM_ROLE_MAPPER, ROLE_MAPPER_RUNTIME_CAPABILITY));
        resourceRegistration.registerSubModel(RoleMapperDefinitions.getLogicalRoleMapperDefinition());
        resourceRegistration.registerSubModel(RoleMapperDefinitions.getMappedRoleMapperDefinition());
        resourceRegistration.registerSubModel(RoleMapperDefinitions.getRegexRoleMapperDefinition());

        // Evidence Decoders
        resourceRegistration.registerSubModel(EvidenceDecoderDefinitions.getX500SubjectEvidenceDecoderDefinition());
        resourceRegistration.registerSubModel(EvidenceDecoderDefinitions.getX509SubjectAltNameEvidenceDecoderDefinition());
        resourceRegistration.registerSubModel(new CustomComponentDefinition<>(EvidenceDecoder.class, Function.identity(), ElytronDescriptionConstants.CUSTOM_EVIDENCE_DECODER, EVIDENCE_DECODER_RUNTIME_CAPABILITY));
        resourceRegistration.registerSubModel(EvidenceDecoderDefinitions.getAggregateEvidenceDecoderDefinition());

        // HTTP Mechanisms
        resourceRegistration.registerSubModel(HttpServerDefinitions.getAggregateHttpServerFactoryDefinition());
        resourceRegistration.registerSubModel(HttpServerDefinitions.getConfigurableHttpServerMechanismFactoryDefinition());
        resourceRegistration.registerSubModel(HttpServerDefinitions.getProviderHttpServerMechanismFactoryDefinition());
        resourceRegistration.registerSubModel(HttpServerDefinitions.getServiceLoaderServerMechanismFactoryDefinition());

        // JSR-196 JASPI
        resourceRegistration.registerSubModel(JaspiDefinition.getJaspiServletConfigurationDefinition());

        // SASL Mechanisms
        resourceRegistration.registerSubModel(SaslServerDefinitions.getAggregateSaslServerFactoryDefinition());
        resourceRegistration.registerSubModel(SaslServerDefinitions.getConfigurableSaslServerFactoryDefinition());
        resourceRegistration.registerSubModel(SaslServerDefinitions.getMechanismProviderFilteringSaslServerFactory());
        resourceRegistration.registerSubModel(SaslServerDefinitions.getProviderSaslServerFactoryDefinition());
        resourceRegistration.registerSubModel(SaslServerDefinitions.getServiceLoaderSaslServerFactoryDefinition());

        // TLS Building Blocks
        resourceRegistration.registerSubModel(AdvancedModifiableKeyStoreDecorator.wrap(new KeyStoreDefinition()));
        resourceRegistration.registerSubModel(ModifiableKeyStoreDecorator.wrap(new LdapKeyStoreDefinition()));
        resourceRegistration.registerSubModel(ModifiableKeyStoreDecorator.wrap(new FilteringKeyStoreDefinition()));
        resourceRegistration.registerSubModel(SSLDefinitions.getKeyManagerDefinition());
        resourceRegistration.registerSubModel(SSLDefinitions.getTrustManagerDefinition());
        resourceRegistration.registerSubModel(SSLDefinitions.getServerSSLContextDefinition(serverOrHostController));
        resourceRegistration.registerSubModel(SSLDefinitions.getClientSSLContextDefinition(serverOrHostController));
        resourceRegistration.registerSubModel(SSLDefinitions.getServerSNISSLContextDefinition());
        resourceRegistration.registerSubModel(new CertificateAuthorityDefinition());
        resourceRegistration.registerSubModel(new CertificateAuthorityAccountDefinition());

        // Credential Store Block
        resourceRegistration.registerSubModel(new CredentialStoreResourceDefinition());
        resourceRegistration.registerSubModel(new SecretKeyCredentialStoreDefinition());

        // Dir-Context
        resourceRegistration.registerSubModel(new DirContextDefinition());

        // Authentication Configuration
        resourceRegistration.registerSubModel(AuthenticationClientDefinitions.getAuthenticationClientDefinition());
        resourceRegistration.registerSubModel(AuthenticationClientDefinitions.getAuthenticationContextDefinition());

        // Policy
        resourceRegistration.registerSubModel(PolicyDefinitions.getPolicy());
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        OperationStepHandler writeHandler = new ReloadRequiredWriteAttributeHandler(INITIAL_PROVIDERS, FINAL_PROVIDERS, DISALLOWED_PROVIDERS, REGISTER_JASPI_FACTORY);
        resourceRegistration.registerReadWriteAttribute(INITIAL_PROVIDERS, null, writeHandler);
        resourceRegistration.registerReadWriteAttribute(FINAL_PROVIDERS, null, writeHandler);
        resourceRegistration.registerReadWriteAttribute(DISALLOWED_PROVIDERS, null, writeHandler);
        resourceRegistration.registerReadWriteAttribute(DEFAULT_AUTHENTICATION_CONTEXT, null, new ElytronWriteAttributeHandler<Void>(DEFAULT_AUTHENTICATION_CONTEXT) {

            @Override
            protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                    ModelNode resolvedValue, ModelNode currentValue,
                    HandbackHolder<Void> handbackHolder)
                    throws OperationFailedException {
                AUTHENITCATION_CONTEXT_PROCESSOR.setDefaultAuthenticationContext(resolvedValue.isDefined() ? resolvedValue.asString() : null);
                return !context.isBooting();
            }

            @Override
            protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                    ModelNode valueToRestore, ModelNode valueToRevert, Void handback)
                    throws OperationFailedException {
                AUTHENITCATION_CONTEXT_PROCESSOR.setDefaultAuthenticationContext(valueToRestore.isDefined() ? valueToRestore.asString() : null);
            }

        });
        resourceRegistration.registerReadWriteAttribute(SECURITY_PROPERTIES, null, new SecurityPropertiesWriteHandler(SECURITY_PROPERTIES));
        resourceRegistration.registerReadWriteAttribute(REGISTER_JASPI_FACTORY, null, writeHandler);
        resourceRegistration.registerReadWriteAttribute(DEFAULT_SSL_CONTEXT, null, new ElytronWriteAttributeHandler<Void>(DEFAULT_SSL_CONTEXT) {

            @Override
            protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                    ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> handbackHolder)
                    throws OperationFailedException {
                        if (!resolvedValue.isDefined() && currentValue.isDefined()) {
                            // We can not capture the existing default as by doing so we would trigger it's initialisation which
                            // could fail in a variety of ways as well as the wasted initialisation, if the attribute is being
                            // changed from defined to undefined the only option is to completely restart the process.
                            context.restartRequired();
                            return false;
                        }

                return true;
            }

            @Override
            protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                    ModelNode valueToRestore, ModelNode valueToRevert, Void handback) throws OperationFailedException {}
        });
    }

    @Override
    public void registerAdditionalRuntimePackages(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerAdditionalRuntimePackages(RuntimePackageDependency.required("org.wildfly.security.elytron"));
    }

    @Deprecated
    static <T> ServiceBuilder<T>  commonDependencies(ServiceBuilder<T> serviceBuilder) {
        return commonDependencies(serviceBuilder, true, true);
    }

    @Deprecated
    static <T> ServiceBuilder<T>  commonDependencies(ServiceBuilder<T> serviceBuilder, boolean dependOnProperties, boolean dependOnProviderRegistration) {
        if (dependOnProperties) serviceBuilder.requires(SecurityPropertyService.SERVICE_NAME);
        if (dependOnProviderRegistration) serviceBuilder.requires(ProviderRegistrationService.SERVICE_NAME);
        return serviceBuilder;
    }

    static <T> ServiceBuilder<T>  commonRequirements(ServiceBuilder<T> serviceBuilder) {
        return commonRequirements(serviceBuilder, true, true);
    }

    static <T> ServiceBuilder<T>  commonRequirements(ServiceBuilder<T> serviceBuilder, boolean dependOnProperties, boolean dependOnProviderRegistration) {
        if (dependOnProperties) serviceBuilder.requires(SecurityPropertyService.SERVICE_NAME);
        if (dependOnProviderRegistration) serviceBuilder.requires(ProviderRegistrationService.SERVICE_NAME);
        return serviceBuilder;
    }

    private static void installService(ServiceName serviceName, Service<?> service, ServiceTarget serviceTarget) {
        serviceTarget.addService(serviceName, service)
            .setInitialMode(Mode.ACTIVE)
            .install();
    }

    private static void registerAuthConfigFactory(final AuthConfigFactory authConfigFactory) {
        doPrivileged((PrivilegedAction<Void>) () -> {
            AuthConfigFactory.setFactory(authConfigFactory);
            return null;
        });
    }

    private static AuthConfigFactory getAuthConfigFactory() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(ElytronDefinition.class.getClassLoader());
            return AuthConfigFactory.getFactory();
        } catch (Exception e) {
            ROOT_LOGGER.trace("Unable to load default AuthConfigFactory.", e);
            return null;
        } finally {
            Thread.currentThread().setContextClassLoader(classLoader);
        }
    }

    private static SecurityPropertyService uninstallSecurityPropertyService(OperationContext context) {
        ServiceRegistry serviceRegistry = context.getServiceRegistry(true);

        ServiceController<?> service = serviceRegistry.getService(SecurityPropertyService.SERVICE_NAME);
        if (service != null) {
            Object serviceImplementation = service.getService();
            context.removeService(service);
            if (serviceImplementation != null && serviceImplementation instanceof SecurityPropertyService) {
                return (SecurityPropertyService) serviceImplementation;
            }
        }

        return null;
    }

    private static class ElytronAdd extends AbstractBoottimeAddStepHandler implements ElytronOperationStepHandler {

        private ElytronAdd() {
            super(ELYTRON_RUNTIME_CAPABILITY, DEFAULT_AUTHENTICATION_CONTEXT, INITIAL_PROVIDERS, FINAL_PROVIDERS, DISALLOWED_PROVIDERS, SECURITY_PROPERTIES, REGISTER_JASPI_FACTORY, DEFAULT_SSL_CONTEXT);
        }

        @Override
        protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
            Version.getVersion();
            super.populateModel(operation, model);
        }

        @Override
        protected void performBoottime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {

            ModelNode model = resource.getModel();
            final String defaultAuthenticationContext = DEFAULT_AUTHENTICATION_CONTEXT.resolveModelAttribute(context, model).asStringOrNull();
            AUTHENITCATION_CONTEXT_PROCESSOR.setDefaultAuthenticationContext(defaultAuthenticationContext);
            Map<String,String> securityProperties = SECURITY_PROPERTIES.unwrap(context, model);
            final String defaultSSLContext = DEFAULT_SSL_CONTEXT.resolveModelAttribute(context, model).asStringOrNull();

            ServiceTarget target = context.getServiceTarget();
            installService(SecurityPropertyService.SERVICE_NAME, new SecurityPropertyService(securityProperties), target);

            List<String> providers = DISALLOWED_PROVIDERS.unwrap(context, operation);

            ProviderRegistrationService prs = new ProviderRegistrationService(providers);
            ServiceBuilder<Void> builder = target.addService(ProviderRegistrationService.SERVICE_NAME, prs)
                .setInitialMode(Mode.ACTIVE);

            String initialProviders = INITIAL_PROVIDERS.resolveModelAttribute(context, model).asStringOrNull();
            if (initialProviders != null) {
                builder.addDependency(
                        context.getCapabilityServiceName(PROVIDERS_CAPABILITY, initialProviders, Provider[].class),
                        Provider[].class, prs.getInitialProivders());
            }

            String finalProviders = FINAL_PROVIDERS.resolveModelAttribute(context, model).asStringOrNull();
            if (finalProviders != null) {
                builder.addDependency(
                        context.getCapabilityServiceName(PROVIDERS_CAPABILITY, finalProviders, Provider[].class),
                        Provider[].class, prs.getFinalProviders());
            }
            builder.install();

            if (defaultAuthenticationContext != null) {
                ServiceBuilder<?> serviceBuilder = target
                        .addService(DefaultAuthenticationContextService.SERVICE_NAME)
                        .setInitialMode(Mode.ACTIVE);
                Supplier<AuthenticationContext> defaultAuthenticationContextSupplier = serviceBuilder.requires(context.getCapabilityServiceName(AUTHENTICATION_CONTEXT_CAPABILITY, defaultAuthenticationContext, AuthenticationContext.class));
                Consumer<AuthenticationContext> valueConsumer = serviceBuilder.provides(DefaultAuthenticationContextService.SERVICE_NAME);

                DefaultAuthenticationContextService defaultAuthenticationContextService = new DefaultAuthenticationContextService(defaultAuthenticationContextSupplier, valueConsumer);
                serviceBuilder.setInstance(defaultAuthenticationContextService)
                        .install();
            }

            if (defaultSSLContext != null) {
                ServiceBuilder<?> serviceBuilder = target
                        .addService(DefaultSSLContextService.SERVICE_NAME)
                        .setInitialMode(Mode.ACTIVE);
                Supplier<SSLContext> defaultSSLContextSupplier = serviceBuilder.requires(context.getCapabilityServiceName(SSL_CONTEXT_CAPABILITY, defaultSSLContext, SSLContext.class));
                Consumer<SSLContext> valueConsumer = serviceBuilder.provides(DefaultSSLContextService.SERVICE_NAME);

                DefaultSSLContextService defaultSSLContextService = new DefaultSSLContextService(defaultSSLContextSupplier, valueConsumer);
                serviceBuilder.setInstance(defaultSSLContextService)
                        .install();
            }

            if (registerJaspiFactory(context, model)) {
                final AuthConfigFactory authConfigFactory = doPrivileged((PrivilegedAction<AuthConfigFactory>) ElytronDefinition::getAuthConfigFactory);
                if (authConfigFactory != null) {
                    // TODO This wrapping is only temporary to allow us to delegate to the PicketBox impl, at a later point there really should only
                    // be one AuthConfigFactory at a time.
                    registerAuthConfigFactory(new DelegatingAuthConfigFactory(new ElytronAuthConfigFactory(), authConfigFactory, ALLOW_DELEGATION));
                } else {
                    registerAuthConfigFactory(new ElytronAuthConfigFactory());
                }
            }

            if (context.isNormalServer()) {
                context.addStep(new AbstractDeploymentChainStep() {
                    @Override
                    protected void execute(DeploymentProcessorTarget processorTarget) {
                        processorTarget.addDeploymentProcessor(ElytronExtension.SUBSYSTEM_NAME, Phase.STRUCTURE, Phase.STRUCTURE_ELYTRON_EXPRESSION_RESOLVER, new DeploymentExpressionResolverProcessor());
                        processorTarget.addDeploymentProcessor(ElytronExtension.SUBSYSTEM_NAME, Phase.STRUCTURE,  Phase.STRUCTURE_SECURITY_METADATA, new SecurityMetaDataProcessor());
                        processorTarget.addDeploymentProcessor(ElytronExtension.SUBSYSTEM_NAME, Phase.PARSE, Phase.PARSE_DEFINE_VIRTUAL_DOMAIN_NAME, new VirtualSecurityDomainNameProcessor());
                        processorTarget.addDeploymentProcessor(ElytronExtension.SUBSYSTEM_NAME, Phase.DEPENDENCIES, Phase.DEPENDENCIES_ELYTRON, new DependencyProcessor());
                        processorTarget.addDeploymentProcessor(ElytronExtension.SUBSYSTEM_NAME, Phase.DEPENDENCIES, Phase.DEPENDENCIES_ELYTRON_EE_SECURITY, new EESecurityDependencyProcessor());
                        processorTarget.addDeploymentProcessor(ElytronExtension.SUBSYSTEM_NAME, Phase.CONFIGURE_MODULE, Phase.CONFIGURE_AUTHENTICATION_CONTEXT, AUTHENITCATION_CONTEXT_PROCESSOR);
                        if (defaultSSLContext != null) {
                            processorTarget.addDeploymentProcessor(ElytronExtension.SUBSYSTEM_NAME, Phase.CONFIGURE_MODULE, Phase.CONFIGURE_DEFAULT_SSL_CONTEXT, new SSLContextDependencyProcessor());
                        }
                        processorTarget.addDeploymentProcessor(ElytronExtension.SUBSYSTEM_NAME, Phase.FIRST_MODULE_USE, Phase.FIRST_MODULE_USE_AUTHENTICATION_CONTEXT, new AuthenticationContextAssociationProcessor());
                        processorTarget.addDeploymentProcessor(ElytronExtension.SUBSYSTEM_NAME, Phase.INSTALL, Phase.INSTALL_VIRTUAL_SECURITY_DOMAIN, new VirtualSecurityDomainProcessor());
                    }
                }, Stage.RUNTIME);
            }
        }

        /*
         * Test if we should register our own AuthConfigFactory.
         *
         * If a System property has been set it will automatically be loaded on the first use so we don't need to register it.
         */
        private boolean registerJaspiFactory(final OperationContext context, final ModelNode model) throws OperationFailedException {
            String jaspiFactory = doPrivileged(new ReadPropertyAction(AuthConfigFactory.DEFAULT_FACTORY_SECURITY_PROPERTY));

            return jaspiFactory == null && REGISTER_JASPI_FACTORY.resolveModelAttribute(context, model).asBoolean();
        }

        @Override
        protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {
            uninstallSecurityPropertyService(context);
            context.removeService(ProviderRegistrationService.SERVICE_NAME);
            AUTHENITCATION_CONTEXT_PROCESSOR.setDefaultAuthenticationContext(null);
        }

        @Override
        protected boolean requiresRuntime(final OperationContext context) {
            return isServerOrHostController(context);
        }
    }

    private static class ElytronRemove extends ElytronRemoveStepHandler {

        private ElytronRemove() {
            super(ELYTRON_RUNTIME_CAPABILITY);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            if (context.isResourceServiceRestartAllowed()) {
                registerAuthConfigFactory(null);
                SecurityPropertyService securityPropertyService = uninstallSecurityPropertyService(context);
                if (securityPropertyService != null) {
                    context.attach(SECURITY_PROPERTY_SERVICE_KEY, securityPropertyService);
                }
                context.removeService(ProviderRegistrationService.SERVICE_NAME);
            } else {
                context.reloadRequired();
            }
        }

        @Override
        protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            ServiceTarget target = context.getServiceTarget();
            SecurityPropertyService securityPropertyService = context.getAttachment(SECURITY_PROPERTY_SERVICE_KEY);
            if (securityPropertyService != null) {
                installService(SecurityPropertyService.SERVICE_NAME, securityPropertyService, target);
            }
            List<String> providers = DISALLOWED_PROVIDERS.unwrap(context, model);
            installService(ProviderRegistrationService.SERVICE_NAME, new ProviderRegistrationService(providers), target);
        }

    }

    private static final Supplier<Boolean> ALLOW_DELEGATION = new Supplier<Boolean>() {

        @Override
        public Boolean get() {
            if (WildFlySecurityManager.isChecking()) {
                return doPrivileged((PrivilegedAction<Boolean>) () -> SecurityDomain.getCurrent() == null);
            } else {
                return SecurityDomain.getCurrent() == null;
            }
        }
    };
}
