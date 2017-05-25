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
import static org.wildfly.extension.elytron.Capabilities.MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PERMISSION_MAPPER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PRINCIPAL_DECODER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PROVIDERS_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.REALM_MAPPER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.ROLE_DECODER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.ROLE_MAPPER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_FACTORY_CREDENTIAL_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronExtension.asStringIfDefined;

import java.security.Provider;
import java.util.List;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.AttachmentKey;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
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
import org.wildfly.security.Version;
import org.wildfly.security.auth.server.ModifiableSecurityRealm;
import org.wildfly.security.auth.server.PrincipalDecoder;
import org.wildfly.security.auth.server.RealmMapper;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.authz.PermissionMapper;
import org.wildfly.security.authz.RoleDecoder;
import org.wildfly.security.authz.RoleMapper;

/**
 * Top level {@link ResourceDefinition} for the Elytron subsystem.
 *
 * @author <a href="mailto:tcerar@redhat.com">Tomaz Cerar</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ElytronDefinition extends SimpleResourceDefinition {

    private static final AttachmentKey<SecurityPropertyService> SECURITY_PROPERTY_SERVICE_KEY = AttachmentKey.create(SecurityPropertyService.class);

    private static final AuthenticationContextDependencyProcessor AUTHENITCATION_CONTEXT_PROCESSOR = new AuthenticationContextDependencyProcessor();

    static final SimpleAttributeDefinition DEFAULT_AUTHENTICATION_CONTEXT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DEFAULT_AUTHENTICATION_CONTEXT, ModelType.STRING, true)
            .setCapabilityReference(AUTHENTICATION_CONTEXT_CAPABILITY, ELYTRON_RUNTIME_CAPABILITY)
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
            .setAttributeParser(AttributeParser.COMMA_DELIMITED_STRING_LIST)
            .setAttributeMarshaller(AttributeMarshaller.COMMA_STRING_LIST)
            .setRestartJVM()
            .setElementValidator(new StringLengthValidator(1))
            .setAllowExpression(true)
            .build();

    public static final ElytronDefinition INSTANCE = new ElytronDefinition();

    private ElytronDefinition() {
        super(new Parameters(ElytronExtension.SUBSYSTEM_PATH, ElytronExtension.getResourceDescriptionResolver())
                .setAddHandler(new ElytronAdd())
                .setRemoveHandler(new ElytronRemove())
                .setCapabilities(ELYTRON_RUNTIME_CAPABILITY)
                .addAccessConstraints(new SensitiveTargetAccessConstraintDefinition(new SensitivityClassification(ElytronExtension.SUBSYSTEM_NAME, ElytronDescriptionConstants.ELYTRON_SECURITY, true, true, true)),
                new ApplicationTypeAccessConstraintDefinition(new ApplicationTypeConfig(ElytronExtension.SUBSYSTEM_NAME, ElytronDescriptionConstants.ELYTRON_SECURITY, false))));
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        // Security Properties
        resourceRegistration.registerSubModel(new SecurityPropertyResourceDefinition());

        // Provider Loader
        resourceRegistration.registerSubModel(ProviderDefinitions.getAggregateProvidersDefinition());
        resourceRegistration.registerSubModel(ProviderDefinitions.getProviderLoaderDefinition());

        // Audit
        resourceRegistration.registerSubModel(AuditResourceDefinitions.getAggregateSecurityEventListenerDefinition());
        resourceRegistration.registerSubModel(AuditResourceDefinitions.getFileAuditLogResourceDefinition());
        resourceRegistration.registerSubModel(AuditResourceDefinitions.getRotatingFileAuditLogResourceDefinition());
        resourceRegistration.registerSubModel(AuditResourceDefinitions.getSyslogAuditLogResourceDefinition());

        // Security Domain SASL / HTTP Configurations
        resourceRegistration.registerSubModel(AuthenticationFactoryDefinitions.getSaslAuthenticationFactory());
        resourceRegistration.registerSubModel(AuthenticationFactoryDefinitions.getHttpAuthenticationFactory());

        // Domain
        resourceRegistration.registerSubModel(new DomainDefinition());

        // Security Realms
        resourceRegistration.registerSubModel(new AggregateRealmDefinition());
        resourceRegistration.registerSubModel(new CustomComponentDefinition<SecurityRealm>(SecurityRealm.class, ElytronDescriptionConstants.CUSTOM_REALM, SECURITY_REALM_RUNTIME_CAPABILITY));
        resourceRegistration.registerSubModel(ModifiableRealmDecorator.wrap(new CustomComponentDefinition<ModifiableSecurityRealm>(
                ModifiableSecurityRealm.class, ElytronDescriptionConstants.CUSTOM_MODIFIABLE_REALM,
                MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY, SECURITY_REALM_RUNTIME_CAPABILITY)));
        resourceRegistration.registerSubModel(RealmDefinitions.getIdentityRealmDefinition());
        resourceRegistration.registerSubModel(new JdbcRealmDefinition());
        resourceRegistration.registerSubModel(new KeyStoreRealmDefinition());
        resourceRegistration.registerSubModel(new PropertiesRealmDefinition());
        resourceRegistration.registerSubModel(new TokenRealmDefinition());
        resourceRegistration.registerSubModel(ModifiableRealmDecorator.wrap(new LdapRealmDefinition()));
        resourceRegistration.registerSubModel(ModifiableRealmDecorator.wrap(new FileSystemRealmDefinition()));
        resourceRegistration.registerSubModel(new CachingRealmDefinition());

        // Security Factories
        resourceRegistration.registerSubModel(new CustomComponentDefinition<CredentialSecurityFactory>(CredentialSecurityFactory.class, ElytronDescriptionConstants.CUSTOM_CREDENTIAL_SECURITY_FACTORY, SECURITY_FACTORY_CREDENTIAL_RUNTIME_CAPABILITY));
        resourceRegistration.registerSubModel(KerberosSecurityFactoryDefinition.getKerberosSecurityFactoryDefinition());

        // Permission Mappers
        resourceRegistration.registerSubModel(new CustomComponentDefinition<PermissionMapper>(PermissionMapper.class, ElytronDescriptionConstants.CUSTOM_PERMISSION_MAPPER, PERMISSION_MAPPER_RUNTIME_CAPABILITY));
        resourceRegistration.registerSubModel(PermissionMapperDefinitions.getLogicalPermissionMapper());
        resourceRegistration.registerSubModel(PermissionMapperDefinitions.getSimplePermissionMapper());
        resourceRegistration.registerSubModel(PermissionMapperDefinitions.getConstantPermissionMapper());

        // Principal Decoders
        resourceRegistration.registerSubModel(PrincipalDecoderDefinitions.getAggregatePrincipalDecoderDefinition());
        resourceRegistration.registerSubModel(PrincipalDecoderDefinitions.getConcatenatingPrincipalDecoder());
        resourceRegistration.registerSubModel(PrincipalDecoderDefinitions.getConstantPrincipalDecoder());
        resourceRegistration.registerSubModel(new CustomComponentDefinition<PrincipalDecoder>(PrincipalDecoder.class, ElytronDescriptionConstants.CUSTOM_PRINCIPAL_DECODER, PRINCIPAL_DECODER_RUNTIME_CAPABILITY));
        resourceRegistration.registerSubModel(PrincipalDecoderDefinitions.getX500AttributePrincipalDecoder());

        // Principal Transformers
        resourceRegistration.registerSubModel(PrincipalTransformerDefinitions.getAggregatePrincipalTransformerDefinition());
        resourceRegistration.registerSubModel(PrincipalTransformerDefinitions.getChainedPrincipalTransformerDefinition());
        resourceRegistration.registerSubModel(PrincipalTransformerDefinitions.getConstantPrincipalTransformerDefinition());
        resourceRegistration.registerSubModel(new CustomComponentDefinition<PrincipalTransformer>(PrincipalTransformer.class, ElytronDescriptionConstants.CUSTOM_PRINCIPAL_TRANSFORMER, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY));
        resourceRegistration.registerSubModel(PrincipalTransformerDefinitions.getRegexPrincipalTransformerDefinition());
        resourceRegistration.registerSubModel(PrincipalTransformerDefinitions.getRegexValidatingPrincipalTransformerDefinition());

        // Realm Mappers
        resourceRegistration.registerSubModel(RealmMapperDefinitions.getConstantRealmMapper());
        resourceRegistration.registerSubModel(new CustomComponentDefinition<RealmMapper>(RealmMapper.class, ElytronDescriptionConstants.CUSTOM_REALM_MAPPER, REALM_MAPPER_RUNTIME_CAPABILITY));
        resourceRegistration.registerSubModel(RealmMapperDefinitions.getMappedRegexRealmMapper());
        resourceRegistration.registerSubModel(RealmMapperDefinitions.getSimpleRegexRealmMapperDefinition());

        // Role Decoders
        resourceRegistration.registerSubModel(new CustomComponentDefinition<RoleDecoder>(RoleDecoder.class, ElytronDescriptionConstants.CUSTOM_ROLE_DECODER, ROLE_DECODER_RUNTIME_CAPABILITY));
        resourceRegistration.registerSubModel(RoleDecoderDefinitions.getSimpleRoleDecoderDefinition());

        // Role Mappers
        resourceRegistration.registerSubModel(RoleMapperDefinitions.getAddSuffixRoleMapperDefinition());
        resourceRegistration.registerSubModel(RoleMapperDefinitions.getAddPrefixRoleMapperDefinition());
        resourceRegistration.registerSubModel(RoleMapperDefinitions.getAggregateRoleMapperDefinition());
        resourceRegistration.registerSubModel(RoleMapperDefinitions.getConstantRoleMapperDefinition());
        resourceRegistration.registerSubModel(new CustomComponentDefinition<RoleMapper>(RoleMapper.class, ElytronDescriptionConstants.CUSTOM_ROLE_MAPPER, ROLE_MAPPER_RUNTIME_CAPABILITY));
        resourceRegistration.registerSubModel(RoleMapperDefinitions.getLogicalRoleMapperDefinition());

        // HTTP Mechanisms
        resourceRegistration.registerSubModel(HttpServerDefinitions.getAggregateHttpServerFactoryDefinition());
        resourceRegistration.registerSubModel(HttpServerDefinitions.getConfigurableHttpServerMechanismFactoryDefinition());
        resourceRegistration.registerSubModel(HttpServerDefinitions.getProviderHttpServerMechanismFactoryDefinition());
        resourceRegistration.registerSubModel(HttpServerDefinitions.getServiceLoaderServerMechanismFactoryDefinition());

        // SASL Mechanisms
        resourceRegistration.registerSubModel(SaslServerDefinitions.getAggregateSaslServerFactoryDefinition());
        resourceRegistration.registerSubModel(SaslServerDefinitions.getConfigurableSaslServerFactoryDefinition());
        resourceRegistration.registerSubModel(SaslServerDefinitions.getMechanismProviderFilteringSaslServerFactory());
        resourceRegistration.registerSubModel(SaslServerDefinitions.getProviderSaslServerFactoryDefinition());
        resourceRegistration.registerSubModel(SaslServerDefinitions.getServiceLoaderSaslServerFactoryDefinition());

        // TLS Building Blocks
        resourceRegistration.registerSubModel(ModifiableKeyStoreDecorator.wrap(new KeyStoreDefinition()));
        resourceRegistration.registerSubModel(ModifiableKeyStoreDecorator.wrap(new LdapKeyStoreDefinition()));
        resourceRegistration.registerSubModel(ModifiableKeyStoreDecorator.wrap(new FilteringKeyStoreDefinition()));
        resourceRegistration.registerSubModel(SSLDefinitions.getKeyManagerDefinition());
        resourceRegistration.registerSubModel(SSLDefinitions.getTrustManagerDefinition());
        resourceRegistration.registerSubModel(SSLDefinitions.getServerSSLContextDefinition());
        resourceRegistration.registerSubModel(SSLDefinitions.getClientSSLContextDefinition());

        // Credential Store Block
        resourceRegistration.registerSubModel(new CredentialStoreResourceDefinition());

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
        OperationStepHandler writeHandler = new ReloadRequiredWriteAttributeHandler(INITIAL_PROVIDERS, FINAL_PROVIDERS, DISALLOWED_PROVIDERS);
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
    }

    static <T> ServiceBuilder<T>  commonDependencies(ServiceBuilder<T> serviceBuilder) {
        return commonDependencies(serviceBuilder, true, true);
    }

    static <T> ServiceBuilder<T>  commonDependencies(ServiceBuilder<T> serviceBuilder, boolean dependOnProperties, boolean dependOnProviderRegistration) {
        if (dependOnProperties) serviceBuilder.addDependencies(SecurityPropertyService.SERVICE_NAME);
        if (dependOnProviderRegistration) serviceBuilder.addDependencies(ProviderRegistrationService.SERVICE_NAME);
        return serviceBuilder;
    }

    private static void installService(ServiceName serviceName, Service<?> service, ServiceTarget serviceTarget) {
        serviceTarget.addService(serviceName, service)
            .setInitialMode(Mode.ACTIVE)
            .install();
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
            super(ELYTRON_RUNTIME_CAPABILITY, DEFAULT_AUTHENTICATION_CONTEXT, INITIAL_PROVIDERS, FINAL_PROVIDERS, DISALLOWED_PROVIDERS);
        }

        @Override
        protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
            Version.getVersion();
            super.populateModel(operation, model);
        }

        @Override
        protected void performBoottime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            ModelNode model = resource.getModel();
            ModelNode defaultAuthenticationContext = DEFAULT_AUTHENTICATION_CONTEXT.resolveModelAttribute(context, model);
            AUTHENITCATION_CONTEXT_PROCESSOR.setDefaultAuthenticationContext(defaultAuthenticationContext.isDefined() ? defaultAuthenticationContext.asString() : null);

            ServiceTarget target = context.getServiceTarget();
            installService(SecurityPropertyService.SERVICE_NAME, new SecurityPropertyService(), target);

            List<String> providers = DISALLOWED_PROVIDERS.unwrap(context, operation);

            ProviderRegistrationService prs = new ProviderRegistrationService(providers);
            ServiceBuilder<Void> builder = target.addService(ProviderRegistrationService.SERVICE_NAME, prs)
                .setInitialMode(Mode.ACTIVE);

            String initialProviders = asStringIfDefined(context, INITIAL_PROVIDERS, model);
            if (initialProviders != null) {
                builder.addDependency(
                        context.getCapabilityServiceName(PROVIDERS_CAPABILITY, initialProviders, Provider[].class),
                        Provider[].class, prs.getInitialProivders());
            }

            String finalProviders = asStringIfDefined(context, FINAL_PROVIDERS, model);
            if (finalProviders != null) {
                builder.addDependency(
                        context.getCapabilityServiceName(PROVIDERS_CAPABILITY, finalProviders, Provider[].class),
                        Provider[].class, prs.getFinalProviders());
            }

            builder.install();

            if (context.isNormalServer()) {
                context.addStep(new AbstractDeploymentChainStep() {
                    @Override
                    protected void execute(DeploymentProcessorTarget processorTarget) {
                        // TODO Remove hard coded Phase ID once a suitable core is available with Phase.DEPENDENCIES_ELYTRON defined.
                        processorTarget.addDeploymentProcessor(ElytronExtension.SUBSYSTEM_NAME, Phase.DEPENDENCIES, 0x0C51, new DependencyProcessor());
                        processorTarget.addDeploymentProcessor(ElytronExtension.SUBSYSTEM_NAME, Phase.CONFIGURE_MODULE, Phase.CONFIGURE_AUTHENTICATION_CONTEXT, AUTHENITCATION_CONTEXT_PROCESSOR);
                        processorTarget.addDeploymentProcessor(ElytronExtension.SUBSYSTEM_NAME, Phase.FIRST_MODULE_USE, Phase.FIRST_MODULE_USE_AUTHENTICATION_CONTEXT, new AuthenticationContextAssociationProcessor());
                    }
                }, Stage.RUNTIME);
            }
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
}
