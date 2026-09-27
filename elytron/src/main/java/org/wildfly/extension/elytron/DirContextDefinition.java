/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;
import static org.wildfly.extension.elytron.Capabilities.AUTHENTICATION_CONTEXT_API_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.AUTHENTICATION_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.DIR_CONTEXT_API_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.DIR_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.DIR_CONTEXT_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SSL_CONTEXT_API_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SSL_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.CommonAttributes.PROPERTIES;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.util.Locale;
import java.util.Properties;

import javax.net.ssl.SSLContext;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.ModuleIdentifierUtil;
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
import org.jboss.modules.ModuleLoadException;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.common.function.ExceptionFunction;
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
            .setDefaultValue(new ModelNode(ReferralMode.IGNORE.toString()))
            .setAllowedValues(ReferralMode.FOLLOW.toString(), ReferralMode.IGNORE.toString(), ReferralMode.THROW.toString())
            .setValidator(EnumValidator.create(ReferralMode.class))
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
        for (AttributeDefinition current : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, ElytronReloadRequiredWriteAttributeHandler.INSTANCE);
        }
    }

    /**
     * Builds a {@link DirContextSupplier} from resolved model values and the supplied optional dependencies.
     * Used by both the service path (via injectors) and the early path (via directly resolved values).
     */
    private static DirContextSupplier buildDirContextSupplier(String url, String authenticationLevel,
            String principal, Module module, Properties connectionProperties,
            ModelNode connectionTimeout, ModelNode readTimeout, ReferralMode referralMode,
            CredentialSource credentialSource, AuthenticationContext authenticationContext,
            SSLContext sslContext) {
        SimpleDirContextFactoryBuilder builder = SimpleDirContextFactoryBuilder.builder()
                .setProviderUrl(url)
                .setSecurityAuthentication(authenticationLevel)
                .setConnectionProperties(connectionProperties);

        if (principal != null) builder.setSecurityPrincipal(principal);
        if (credentialSource != null) builder.setCredentialSource(credentialSource);
        if (authenticationContext != null) builder.setAuthenticationContext(authenticationContext);
        if (sslContext != null) builder.setSocketFactory(sslContext.getSocketFactory());
        if (connectionTimeout.isDefined()) builder.setConnectTimeout(connectionTimeout.asInt());
        if (readTimeout.isDefined()) builder.setReadTimeout(readTimeout.asInt());
        if (module != null) builder.setModule(module);

        DirContextFactory dirContextFactory = builder.build();
        return (DirContextSupplier) () -> dirContextFactory.obtainDirContext(referralMode);
    }

    private static final DoohickeyAddHandler<DirContextSupplier> ADD = new DoohickeyAddHandler<DirContextSupplier>(DIR_CONTEXT_RUNTIME_CAPABILITY, DIR_CONTEXT_API_CAPABILITY) {

        @Override
        protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {
            super.populateModel(context, operation, resource);
            handleCredentialReferenceUpdate(context, resource.getModel());
        }

        @Override
        protected ElytronDoohickey<DirContextSupplier> createDoohickey(PathAddress resourceAddress) {
            return new ElytronDoohickey<DirContextSupplier>(resourceAddress) {

                // Model values captured in resolveRuntime
                private volatile String url;
                private volatile String authenticationLevel;
                private volatile String principal;
                private volatile Module module;
                private volatile Properties connectionProperties;
                private volatile ModelNode connectionTimeout;
                private volatile ModelNode readTimeout;
                private volatile ReferralMode referralMode;
                private volatile ModelNode credentialReferenceModel; // full attribute model for service path
                private volatile String authenticationContextName;
                private volatile String sslContextName;

                @Override
                protected void resolveRuntime(ModelNode model, OperationContext context) throws OperationFailedException {
                    url = URL.resolveModelAttribute(context, model).asString();
                    authenticationLevel = AUTHENTICATION_LEVEL.resolveModelAttribute(context, model).asString();
                    principal = PRINCIPAL.resolveModelAttribute(context, model).asStringOrNull();

                    String moduleName = model.hasDefined(MODULE.getName())
                            ? MODULE.resolveModelAttribute(context, model).asString() : null;
                    if (moduleName != null && !moduleName.isEmpty()) {
                        try {
                            Module cm = Module.getCallerModule();
                            module = cm.getModule(ModuleIdentifierUtil.parseCanonicalModuleIdentifier(moduleName));
                        } catch (ModuleLoadException e) {
                            throw ElytronSubsystemMessages.ROOT_LOGGER.unableToLoadModule(moduleName, e);
                        }
                    }

                    connectionProperties = new Properties();
                    ModelNode enableConnectionPoolingNode = ENABLE_CONNECTION_POOLING.resolveModelAttribute(context, model);
                    connectionProperties.put(CONNECTION_POOLING_PROPERTY, enableConnectionPoolingNode.asString());
                    ModelNode properties = PROPERTIES.resolveModelAttribute(context, model);
                    if (properties.isDefined()) {
                        for (Property property : properties.asPropertyList()) {
                            connectionProperties.put(property.getName(), property.getValue().asString());
                        }
                    }

                    connectionTimeout = CONNECTION_TIMEOUT.resolveModelAttribute(context, model);
                    readTimeout = READ_TIMEOUT.resolveModelAttribute(context, model);
                    referralMode = ReferralMode.valueOf(REFERRAL_MODE.resolveModelAttribute(context, model).asString().toUpperCase(Locale.ENGLISH));

                    credentialReferenceModel = CREDENTIAL_REFERENCE.resolveModelAttribute(context, model);
                    authenticationContextName = AUTHENTICATION_CONTEXT.resolveModelAttribute(context, model).asStringOrNull();
                    sslContextName = SSL_CONTEXT.resolveModelAttribute(context, model).asStringOrNull();
                }

                @Override
                protected ExceptionSupplier<DirContextSupplier, StartException> prepareServiceSupplier(
                        OperationContext context, CapabilityServiceBuilder<?> serviceBuilder) throws OperationFailedException {

                    final InjectedValue<ExceptionSupplier<CredentialSource, Exception>> credentialSourceSupplierInjector = new InjectedValue<>();
                    final InjectedValue<AuthenticationContext> authenticationContextInjector = new InjectedValue<>();
                    final InjectedValue<SSLContext> sslContextInjector = new InjectedValue<>();

                    // getCredentialSourceSupplier resolves the attribute against the resource model itself.
                    ModelNode resourceModel = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
                    if (credentialReferenceModel.isDefined()) {
                        credentialSourceSupplierInjector.inject(
                                CredentialReference.getCredentialSourceSupplier(context, CREDENTIAL_REFERENCE,
                                        resourceModel, serviceBuilder));
                    }

                    if (sslContextName != null) {
                        String sslCapability = RuntimeCapability.buildDynamicCapabilityName(SSL_CONTEXT_CAPABILITY, sslContextName);
                        ServiceName sslServiceName = context.getCapabilityServiceName(sslCapability, SSLContext.class);
                        serviceBuilder.addDependency(sslServiceName, SSLContext.class, sslContextInjector);
                    }

                    if (authenticationContextName != null) {
                        String acCapability = RuntimeCapability.buildDynamicCapabilityName(AUTHENTICATION_CONTEXT_CAPABILITY, authenticationContextName);
                        ServiceName acServiceName = context.getCapabilityServiceName(acCapability, AuthenticationContext.class);
                        serviceBuilder.addDependency(acServiceName, AuthenticationContext.class, authenticationContextInjector);
                    }

                    return () -> {
                        CredentialSource credentialSource = null;
                        ExceptionSupplier<CredentialSource, Exception> csSupplier = credentialSourceSupplierInjector.getOptionalValue();
                        if (csSupplier != null) {
                            try {
                                credentialSource = csSupplier.get();
                            } catch (Exception e) {
                                throw new StartException(ROOT_LOGGER.dirContextPasswordCannotBeResolved(e));
                            }
                        }
                        return buildDirContextSupplier(url, authenticationLevel, principal, module,
                                connectionProperties, connectionTimeout, readTimeout, referralMode,
                                credentialSource, authenticationContextInjector.getOptionalValue(),
                                sslContextInjector.getOptionalValue());
                    };
                }

                @Override
                protected DirContextSupplier createImmediately(OperationContext foreignContext) throws OperationFailedException {
                    // Read the dir-context resource's own model via the root address.
                    ModelNode resourceModel = foreignContext.readResourceFromRoot(resourceAddress).getModel();

                    CredentialSource credentialSource = null;
                    if (credentialReferenceModel.isDefined()) {
                        credentialSource = CredentialReference.getCredentialSource(foreignContext, CREDENTIAL_REFERENCE, resourceModel);
                    }

                    AuthenticationContext authenticationContext = null;
                    if (authenticationContextName != null) {
                        @SuppressWarnings("unchecked")
                        ExceptionFunction<OperationContext, AuthenticationContext, OperationFailedException> acApi =
                                foreignContext.getCapabilityRuntimeAPI(AUTHENTICATION_CONTEXT_API_CAPABILITY,
                                        authenticationContextName, ExceptionFunction.class);
                        authenticationContext = acApi.apply(foreignContext);
                    }

                    SSLContext sslContext = null;
                    if (sslContextName != null) {
                        @SuppressWarnings("unchecked")
                        ExceptionFunction<OperationContext, SSLContext, OperationFailedException> sslApi =
                                foreignContext.getCapabilityRuntimeAPI(SSL_CONTEXT_API_CAPABILITY,
                                        sslContextName, ExceptionFunction.class);
                        sslContext = sslApi.apply(foreignContext);
                    }

                    DirContextSupplier supplier = buildDirContextSupplier(url, authenticationLevel, principal,
                            module, connectionProperties, connectionTimeout, readTimeout, referralMode,
                            credentialSource, authenticationContext, sslContext);
                    setValue(supplier);
                    return supplier;
                }
            };
        }

        @Override
        protected void rollbackRuntime(OperationContext context, final ModelNode operation, final Resource resource) {
            rollbackCredentialStoreUpdate(CREDENTIAL_REFERENCE, context, resource);
        }
    };

    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, DIR_CONTEXT_RUNTIME_CAPABILITY);
}
