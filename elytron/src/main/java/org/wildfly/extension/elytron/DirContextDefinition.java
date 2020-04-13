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
import static org.wildfly.extension.elytron.Capabilities.AUTHENTICATION_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.DIR_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.DIR_CONTEXT_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SSL_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.CommonAttributes.PROPERTIES;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.util.Properties;

import javax.net.ssl.SSLContext;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.extension.elytron._private.ElytronSubsystemMessages;
import org.wildfly.extension.elytron.capabilities._private.DirContextSupplier;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.realm.ldap.DirContextFactory;
import org.wildfly.security.auth.realm.ldap.DirContextFactory.ReferralMode;
import org.wildfly.security.auth.realm.ldap.SimpleDirContextFactoryBuilder;
import org.wildfly.security.credential.source.CredentialSource;

/**
 * A {@link ResourceDefinition} for a {@link javax.naming.directory.DirContext}.
 *
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
class DirContextDefinition extends SimpleResourceDefinition {

    public static final String CONNECTION_POOLING_PROPERTY = "com.sun.jndi.ldap.connect.pool";

    static final SimpleAttributeDefinition URL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.URL, ModelType.STRING, false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition AUTHENTICATION_LEVEL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.AUTHENTICATION_LEVEL, ModelType.STRING, true)
            .setDefaultValue(new ModelNode("simple"))
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition PRINCIPAL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PRINCIPAL, ModelType.STRING, true)
            .setAllowExpression(true)
            .setAlternatives(ElytronDescriptionConstants.AUTHENTICATION_CONTEXT)
            .setRestartAllServices()
            .build();

    static final ObjectTypeAttributeDefinition CREDENTIAL_REFERENCE =
            CredentialReference.getAttributeBuilder(true, true)
                    .setAlternatives(ElytronDescriptionConstants.AUTHENTICATION_CONTEXT)
                    .build();

    static final SimpleAttributeDefinition ENABLE_CONNECTION_POOLING = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ENABLE_CONNECTION_POOLING, ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.FALSE)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition REFERRAL_MODE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REFERRAL_MODE, ModelType.STRING, true)
            .setDefaultValue(new ModelNode(ReferralMode.IGNORE.name()))
            .setAllowedValues(ReferralMode.FOLLOW.name(), ReferralMode.IGNORE.name(), ReferralMode.THROW.name())
            .setValidator(EnumValidator.create(ReferralMode.class, true, true))
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition AUTHENTICATION_CONTEXT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.AUTHENTICATION_CONTEXT, ModelType.STRING, true)
            .setAllowExpression(false)
            .setRestartAllServices()
            .setCapabilityReference(AUTHENTICATION_CONTEXT_CAPABILITY, DIR_CONTEXT_CAPABILITY)
            .setAlternatives(CredentialReference.CREDENTIAL_REFERENCE, ElytronDescriptionConstants.SSL_CONTEXT,ElytronDescriptionConstants.PRINCIPAL)
            .build();

    static final SimpleAttributeDefinition SSL_CONTEXT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SSL_CONTEXT, ModelType.STRING, true)
            .setAllowExpression(false)
            .setRestartAllServices()
            .setCapabilityReference(SSL_CONTEXT_CAPABILITY, DIR_CONTEXT_CAPABILITY)
            .setAlternatives(ElytronDescriptionConstants.AUTHENTICATION_CONTEXT)
            .build();

    static final SimpleAttributeDefinition CONNECTION_TIMEOUT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CONNECTION_TIMEOUT, ModelType.INT, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition READ_TIMEOUT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.READ_TIMEOUT, ModelType.INT, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition MODULE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MODULE, ModelType.STRING, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {URL, AUTHENTICATION_LEVEL, PRINCIPAL, CREDENTIAL_REFERENCE, ENABLE_CONNECTION_POOLING, REFERRAL_MODE, AUTHENTICATION_CONTEXT, SSL_CONTEXT, CONNECTION_TIMEOUT, READ_TIMEOUT, PROPERTIES, MODULE};

    DirContextDefinition() {
        super(new SimpleResourceDefinition.Parameters(PathElement.pathElement(ElytronDescriptionConstants.DIR_CONTEXT), ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.DIR_CONTEXT))
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(DIR_CONTEXT_RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        AbstractWriteAttributeHandler handler = new ElytronReloadRequiredWriteAttributeHandler(ATTRIBUTES);
        for (AttributeDefinition current : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, handler);
        }
    }

    private static TrivialService.ValueSupplier<DirContextSupplier> obtainDirContextSupplier(final OperationContext context, final ModelNode model, final InjectedValue<ExceptionSupplier<CredentialSource, Exception>> credentialSourceSupplierInjector, final InjectedValue<AuthenticationContext> authenticationContextInjector, final InjectedValue<SSLContext> sslContextInjector) throws OperationFailedException {

        String url = URL.resolveModelAttribute(context, model).asString();
        String authenticationLevel = AUTHENTICATION_LEVEL.resolveModelAttribute(context, model).asString();
        String principal = PRINCIPAL.resolveModelAttribute(context, model).asStringOrNull();
        String moduleName = null;
        if(model.hasDefined(MODULE.getName()))
            moduleName = MODULE.resolveModelAttribute(context, model).asString();

        Module module = null;
        if(moduleName != null && !"".equals(moduleName)){
            try {
                Module cm = Module.getCallerModule();
                ModuleIdentifier mi = ModuleIdentifier.create(moduleName);
                //module = Module.getCallerModule().getModule(ModuleIdentifier.create(moduleName));
                module = cm.getModule(mi);
            } catch (ModuleLoadException e) {
                throw ElytronSubsystemMessages.ROOT_LOGGER.unableToLoadModule(moduleName, e);
            }
        }
        final Module finalModule = module;

        Properties connectionProperties = new Properties();
        ModelNode enableConnectionPoolingNode = ENABLE_CONNECTION_POOLING.resolveModelAttribute(context, model);
        connectionProperties.put(CONNECTION_POOLING_PROPERTY, enableConnectionPoolingNode.asString());
        ModelNode properties = PROPERTIES.resolveModelAttribute(context, model);
        if (properties.isDefined()) {
            for (Property property : properties.asPropertyList()) {
                connectionProperties.put(property.getName(), property.getValue().asString());
            }
        }
        ModelNode connectionTimeout = CONNECTION_TIMEOUT.resolveModelAttribute(context, model);
        ModelNode readTimeout = READ_TIMEOUT.resolveModelAttribute(context, model);
        ReferralMode referralMode = ReferralMode.valueOf(REFERRAL_MODE.resolveModelAttribute(context, model).asString().toUpperCase());

        return () -> {
            SimpleDirContextFactoryBuilder builder = SimpleDirContextFactoryBuilder.builder()
                    .setProviderUrl(url)
                    .setSecurityAuthentication(authenticationLevel)
                    .setConnectionProperties(connectionProperties);

            if (principal != null) builder.setSecurityPrincipal(principal);

            ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier = credentialSourceSupplierInjector.getOptionalValue();
            if (credentialSourceSupplier != null) {
                try {
                    builder.setCredentialSource(credentialSourceSupplier.get());
                } catch (Exception e) {
                    throw ROOT_LOGGER.dirContextPasswordCannotBeResolved(e);
                }
            }

            AuthenticationContext authenticationContext = authenticationContextInjector.getOptionalValue();
            if (authenticationContext != null) builder.setAuthenticationContext(authenticationContext);

            SSLContext sslContext = sslContextInjector.getOptionalValue();
            if (sslContext != null) builder.setSocketFactory(sslContext.getSocketFactory());

            if (connectionTimeout.isDefined()) builder.setConnectTimeout(connectionTimeout.asInt());
            if (readTimeout.isDefined()) builder.setReadTimeout(readTimeout.asInt());

            if(finalModule!=null) builder.setModule(finalModule);

            DirContextFactory dirContextFactory = builder.build();
            return () -> dirContextFactory.obtainDirContext(referralMode);
        };
    }

    private static final AbstractAddStepHandler ADD = new BaseAddHandler(DIR_CONTEXT_RUNTIME_CAPABILITY, ATTRIBUTES) {
        protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws  OperationFailedException {
            super.populateModel(context, operation, resource);
            handleCredentialReferenceUpdate(context, resource.getModel());
        }

        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {

            RuntimeCapability<Void> runtimeCapability = DIR_CONTEXT_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName serviceName = runtimeCapability.getCapabilityServiceName(DirContextSupplier.class);

            final InjectedValue<ExceptionSupplier<CredentialSource, Exception>> credentialSourceSupplierInjector = new InjectedValue<>();
            final InjectedValue<AuthenticationContext> authenticationContextInjector = new InjectedValue<>();
            final InjectedValue<SSLContext> sslContextInjector = new InjectedValue<>();

            TrivialService<DirContextSupplier> service = new TrivialService<>(obtainDirContextSupplier(context, model, credentialSourceSupplierInjector, authenticationContextInjector, sslContextInjector));
            ServiceBuilder<DirContextSupplier> serviceBuilder = context.getServiceTarget().addService(serviceName, service);

            String sslContextName = SSL_CONTEXT.resolveModelAttribute(context, model).asStringOrNull();
            if (sslContextName != null) {
                String sslCapability = RuntimeCapability.buildDynamicCapabilityName(SSL_CONTEXT_CAPABILITY, sslContextName);
                ServiceName sslServiceName = context.getCapabilityServiceName(sslCapability, SSLContext.class);
                serviceBuilder.addDependency(sslServiceName, SSLContext.class, sslContextInjector);
            }

            if (CREDENTIAL_REFERENCE.resolveModelAttribute(context, model).isDefined()) {
                credentialSourceSupplierInjector.inject(CredentialReference.getCredentialSourceSupplier(context, CREDENTIAL_REFERENCE, model, serviceBuilder));
            }

            String authenticationContextName = AUTHENTICATION_CONTEXT.resolveModelAttribute(context, model).asStringOrNull();
            if (authenticationContextName != null) {
                String acCapability = RuntimeCapability.buildDynamicCapabilityName(AUTHENTICATION_CONTEXT_CAPABILITY, authenticationContextName);
                ServiceName acServiceName = context.getCapabilityServiceName(acCapability, AuthenticationContext.class);
                serviceBuilder.addDependency(acServiceName, AuthenticationContext.class, authenticationContextInjector);
            }

            serviceBuilder
                    .setInitialMode(ServiceController.Mode.ACTIVE)
                    .install();
        }

        protected void rollbackRuntime(OperationContext context, final ModelNode operation, final Resource resource) {
            rollbackCredentialStoreUpdate(CREDENTIAL_REFERENCE, context, resource);
        }
    };

    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, DIR_CONTEXT_RUNTIME_CAPABILITY);
}
