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

import static java.security.AccessController.doPrivileged;
import static org.wildfly.extension.elytron.Capabilities.PERMISSION_MAPPER_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PRINCIPAL_DECODER_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PRINCIPAL_TRANSFORMER_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.REALM_MAPPER_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.ROLE_DECODER_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.ROLE_MAPPER_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_DOMAIN_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_EVENT_LISTENER_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.INITIAL;
import static org.wildfly.extension.elytron.ElytronExtension.asStringIfDefined;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.Stage;
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
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.elytron.DomainService.RealmDependency;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;
import org.wildfly.extension.elytron.capabilities.PrincipalTransformer;
import org.wildfly.extension.elytron.capabilities._private.SecurityEventListener;
import org.wildfly.security.auth.server.PrincipalDecoder;
import org.wildfly.security.auth.server.RealmMapper;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.auth.server.ServerAuthenticationContext;
import org.wildfly.security.authz.PermissionMapper;
import org.wildfly.security.authz.RoleDecoder;
import org.wildfly.security.authz.RoleMapper;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * A {@link ResourceDefinition} for a single domain.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class DomainDefinition extends SimpleResourceDefinition {

    private static final ServiceUtil<SecurityRealm> REALM_SERVICE_UTIL = ServiceUtil.newInstance(SECURITY_REALM_RUNTIME_CAPABILITY, null, SecurityRealm.class);

    static final SimpleAttributeDefinition DEFAULT_REALM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DEFAULT_REALM, ModelType.STRING, false)
         .setAllowExpression(false)
         .setMinSize(1)
         .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
         .build();

    static final SimpleAttributeDefinition PRE_REALM_PRINCIPAL_TRANSFORMER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PRE_REALM_PRINCIPAL_TRANSFORMER, ModelType.STRING, true)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setCapabilityReference(PRINCIPAL_TRANSFORMER_CAPABILITY, SECURITY_DOMAIN_CAPABILITY, true)
        .build();

    static final SimpleAttributeDefinition POST_REALM_PRINCIPAL_TRANSFORMER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.POST_REALM_PRINCIPAL_TRANSFORMER, ModelType.STRING, true)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setCapabilityReference(PRINCIPAL_TRANSFORMER_CAPABILITY, SECURITY_DOMAIN_CAPABILITY, true)
        .build();

    static final SimpleAttributeDefinition PRINCIPAL_DECODER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PRINCIPAL_DECODER, ModelType.STRING, true)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setCapabilityReference(PRINCIPAL_DECODER_CAPABILITY, SECURITY_DOMAIN_CAPABILITY, true)
        .build();

    static final SimpleAttributeDefinition PERMISSION_MAPPER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PERMISSION_MAPPER, ModelType.STRING, true)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setCapabilityReference(PERMISSION_MAPPER_CAPABILITY, SECURITY_DOMAIN_CAPABILITY, true)
        .build();

    static final SimpleAttributeDefinition REALM_MAPPER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REALM_MAPPER, ModelType.STRING, true)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setCapabilityReference(REALM_MAPPER_CAPABILITY, SECURITY_DOMAIN_CAPABILITY, true)
        .build();

    static final SimpleAttributeDefinition ROLE_MAPPER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ROLE_MAPPER, ModelType.STRING, true)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setCapabilityReference(ROLE_MAPPER_CAPABILITY, SECURITY_DOMAIN_CAPABILITY, true)
        .build();

    static final SimpleAttributeDefinition REALM_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REALM, ModelType.STRING, false)
        .setXmlName(ElytronDescriptionConstants.NAME)
        .setMinSize(1)
        .setCapabilityReference(SECURITY_REALM_CAPABILITY, SECURITY_DOMAIN_CAPABILITY, true)
        .build();

    static final SimpleAttributeDefinition REALM_PRINCIPAL_TRANSFORMER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PRINCIPAL_TRANSFORMER, ModelType.STRING, true)
        .setMinSize(1)
        .setCapabilityReference(PRINCIPAL_TRANSFORMER_CAPABILITY, SECURITY_DOMAIN_CAPABILITY, true)
        .build();

    static final SimpleAttributeDefinition REALM_ROLE_DECODER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ROLE_DECODER, ModelType.STRING, true)
        .setMinSize(1)
        .setCapabilityReference(ROLE_DECODER_CAPABILITY, SECURITY_DOMAIN_CAPABILITY, true)
        .build();

    static final ObjectTypeAttributeDefinition REALM = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.REALM, REALM_NAME, REALM_PRINCIPAL_TRANSFORMER, REALM_ROLE_DECODER, ROLE_MAPPER)
        .setAllowNull(false)
        .build();

    static final ObjectListAttributeDefinition REALMS = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.REALMS, REALM)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .build();

    static final StringListAttributeDefinition TRUSTED_SECURITY_DOMAINS = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.TRUSTED_SECURITY_DOMAINS)
            .setRequired(false)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilityReference(SECURITY_DOMAIN_CAPABILITY, SECURITY_DOMAIN_CAPABILITY, true)
            .build();

    static final SimpleAttributeDefinition OUTFLOW_ANONYMOUS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.OUTFLOW_ANONYMOUS, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(false))
            .setRequires(ElytronDescriptionConstants.OUTFLOW_SECURITY_DOMAINS)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final StringListAttributeDefinition OUTFLOW_SECURITY_DOMAINS = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.OUTFLOW_SECURITY_DOMAINS)
            .setRequired(false)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilityReference(SECURITY_DOMAIN_CAPABILITY, SECURITY_DOMAIN_CAPABILITY, true)
            .build();

    static final SimpleAttributeDefinition SECURITY_EVENT_LISTENER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SECURITY_EVENT_LISTENER, ModelType.STRING, true)
            .setAllowExpression(false)
            .setCapabilityReference(SECURITY_EVENT_LISTENER_CAPABILITY, SECURITY_DOMAIN_CAPABILITY, true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { PRE_REALM_PRINCIPAL_TRANSFORMER, POST_REALM_PRINCIPAL_TRANSFORMER, PRINCIPAL_DECODER,
            REALM_MAPPER, ROLE_MAPPER, PERMISSION_MAPPER, DEFAULT_REALM, REALMS, TRUSTED_SECURITY_DOMAINS, OUTFLOW_ANONYMOUS, OUTFLOW_SECURITY_DOMAINS, SECURITY_EVENT_LISTENER };

    private static final DomainAddHandler ADD = new DomainAddHandler();
    private static final OperationStepHandler REMOVE = new DomainRemoveHandler(ADD);
    private static final WriteAttributeHandler WRITE = new WriteAttributeHandler(ElytronDescriptionConstants.SECURITY_DOMAIN);

    DomainDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.SECURITY_DOMAIN), ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.SECURITY_DOMAIN))
            .setAddHandler(ADD)
            .setRemoveHandler(REMOVE)
            .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilities(SECURITY_DOMAIN_RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition current : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, WRITE);
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        registerIdentityManagementOperations(resourceRegistration);
    }

    private void registerIdentityManagementOperations(ManagementResourceRegistration resourceRegistration) {
        IdentityResourceDefinition.ReadSecurityDomainIdentityHandler.register(resourceRegistration, getResourceDescriptionResolver());
        IdentityResourceDefinition.AuthenticatorOperationHandler.register(resourceRegistration, getResourceDescriptionResolver());
    }

    private static ServiceController<SecurityDomain> installInitialService(OperationContext context, ServiceName initialName, ModelNode model,
            Predicate<SecurityDomain> trustedSecurityDomain, UnaryOperator<SecurityIdentity> identityOperator) throws OperationFailedException {
        ServiceTarget serviceTarget = context.getServiceTarget();

        String defaultRealm = DomainDefinition.DEFAULT_REALM.resolveModelAttribute(context, model).asString();
        List<ModelNode> realms = REALMS.resolveModelAttribute(context, model).asList();

        String preRealmPrincipalTransformer = asStringIfDefined(context, PRE_REALM_PRINCIPAL_TRANSFORMER, model);
        String postRealmPrincipalTransformer = asStringIfDefined(context, POST_REALM_PRINCIPAL_TRANSFORMER, model);
        String principalDecoder = asStringIfDefined(context, PRINCIPAL_DECODER, model);
        String permissionMapper = asStringIfDefined(context, PERMISSION_MAPPER, model);
        String realmMapper = asStringIfDefined(context, REALM_MAPPER, model);
        String roleMapper = asStringIfDefined(context, ROLE_MAPPER, model);
        String securityEventListener = asStringIfDefined(context, SECURITY_EVENT_LISTENER, model);

        DomainService domain = new DomainService(defaultRealm, trustedSecurityDomain, identityOperator);

        ServiceBuilder<SecurityDomain> domainBuilder = serviceTarget.addService(initialName, domain)
                .setInitialMode(Mode.ACTIVE);

        if (preRealmPrincipalTransformer != null) {
            injectPrincipalTransformer(preRealmPrincipalTransformer, context, domainBuilder, domain.createPreRealmPrincipalTransformerInjector(preRealmPrincipalTransformer));
        }
        if (postRealmPrincipalTransformer != null) {
            injectPrincipalTransformer(postRealmPrincipalTransformer, context, domainBuilder, domain.createPostRealmPrincipalTransformerInjector(postRealmPrincipalTransformer));
        }
        if (principalDecoder != null) {
            String runtimeCapability = RuntimeCapability.buildDynamicCapabilityName(PRINCIPAL_DECODER_CAPABILITY, principalDecoder);
            ServiceName principalDecoderServiceName = context.getCapabilityServiceName(runtimeCapability, PrincipalDecoder.class);

            domainBuilder.addDependency(principalDecoderServiceName, PrincipalDecoder.class, domain.getPrincipalDecoderInjector());
        }
        if (permissionMapper != null) {
            String runtimeCapability = RuntimeCapability.buildDynamicCapabilityName(PERMISSION_MAPPER_CAPABILITY, permissionMapper);
            ServiceName permissionMapperServiceName = context.getCapabilityServiceName(runtimeCapability, PermissionMapper.class);

            domainBuilder.addDependency(permissionMapperServiceName, PermissionMapper.class, domain.getPermissionMapperInjector());
        }
        if (realmMapper != null) {
            String runtimeCapability = RuntimeCapability.buildDynamicCapabilityName(REALM_MAPPER_CAPABILITY, realmMapper);
            ServiceName realmMapperServiceName = context.getCapabilityServiceName(runtimeCapability, RealmMapper.class);

            domainBuilder.addDependency(realmMapperServiceName, RealmMapper.class, domain.getRealmMapperInjector());
        }
        if (roleMapper != null) {
            injectRoleMapper(roleMapper, context, domainBuilder, domain.createDomainRoleMapperInjector(roleMapper));
        }

        if (securityEventListener != null) {
            domainBuilder.addDependency(
                    context.getCapabilityServiceName(SECURITY_EVENT_LISTENER_CAPABILITY, securityEventListener, SecurityEventListener.class),
                    SecurityEventListener.class, domain.getSecurityEventListenerInjector());
        }

        for (ModelNode current : realms) {
            String realmName = REALM_NAME.resolveModelAttribute(context, current).asString();
            String runtimeCapability = RuntimeCapability.buildDynamicCapabilityName(SECURITY_REALM_CAPABILITY, realmName);
            ServiceName realmServiceName = context.getCapabilityServiceName(runtimeCapability, SecurityRealm.class);

            RealmDependency realmDependency = domain.createRealmDependency(realmName);
            REALM_SERVICE_UTIL.addInjection(domainBuilder, realmDependency.getSecurityRealmInjector() , realmServiceName);

            String principalStranformer = asStringIfDefined(context, REALM_PRINCIPAL_TRANSFORMER, current);
            if (principalStranformer != null) {
                Injector<PrincipalTransformer> principalTransformerInjector = realmDependency.getPrincipalTransformerInjector(principalStranformer);
                injectPrincipalTransformer(principalStranformer, context, domainBuilder, principalTransformerInjector);
            }
            String realmRoleMapper = asStringIfDefined(context, ROLE_MAPPER, current);
            if (realmRoleMapper != null) {
                injectRoleMapper(realmRoleMapper, context, domainBuilder, realmDependency.getRoleMapperInjector(realmRoleMapper));
            }
            String realmRoleDecoder = asStringIfDefined(context, REALM_ROLE_DECODER, current);
            if (realmRoleDecoder != null) {
                injectRoleDecoder(realmRoleDecoder, context, domainBuilder, realmDependency.getRoleDecoderInjector(realmRoleDecoder));
            }
        }

        commonDependencies(domainBuilder);
        return domainBuilder.install();
    }

    private static ServiceController<SecurityDomain> installService(OperationContext context, ServiceName domainName, ModelNode model) throws OperationFailedException {
        ServiceName initialName = domainName.append(INITIAL);

        final InjectedValue<SecurityDomain> securityDomain = new InjectedValue<>();

        List<String> trustedSecurityDomainNames = TRUSTED_SECURITY_DOMAINS.unwrap(context, model);
        final List<InjectedValue<SecurityDomain>> trustedSecurityDomainInjectors = new ArrayList<>(trustedSecurityDomainNames.size());
        final Set<SecurityDomain> trustedSecurityDomains = new HashSet<>();

        List<String> outflowSecurityDomainNames = OUTFLOW_SECURITY_DOMAINS.unwrap(context, model);
        final boolean outflowAnonymous = OUTFLOW_ANONYMOUS.resolveModelAttribute(context, model).asBoolean();
        final List<InjectedValue<SecurityDomain>> outflowSecurityDomainInjectors = new ArrayList<>(outflowSecurityDomainNames.size());
        final Set<SecurityDomain> outflowSecurityDomains = new HashSet<>();

        installInitialService(context, initialName, model, trustedSecurityDomains::contains,
                outflowSecurityDomainNames.size() > 0 ? i -> outflow(i, outflowAnonymous, outflowSecurityDomains) : UnaryOperator.identity());

        TrivialService<SecurityDomain> finalDomainService = new TrivialService<SecurityDomain>();
        finalDomainService.setValueSupplier(new ValueSupplier<SecurityDomain>() {

            @Override
            public SecurityDomain get() throws StartException {
                trustedSecurityDomainInjectors.forEach(i -> trustedSecurityDomains.add(i.getValue()));
                outflowSecurityDomainInjectors.forEach(i -> outflowSecurityDomains.add(i.getValue()));
                return securityDomain.getValue();
            }

            @Override
            public void dispose() {
                trustedSecurityDomains.clear();
            }

        });

        ServiceTarget serviceTarget = context.getServiceTarget();
        ServiceBuilder<SecurityDomain> domainBuilder = serviceTarget.addService(domainName, finalDomainService)
                .setInitialMode(Mode.ACTIVE);
        domainBuilder.addDependency(initialName, SecurityDomain.class, securityDomain);
        for (String trustedDomainName : trustedSecurityDomainNames) {
            InjectedValue<SecurityDomain> trustedDomainInjector = new InjectedValue<>();
            domainBuilder.addDependency(context.getCapabilityServiceName(SECURITY_DOMAIN_CAPABILITY, trustedDomainName, SecurityDomain.class).append(INITIAL), SecurityDomain.class, trustedDomainInjector);
            trustedSecurityDomainInjectors.add(trustedDomainInjector);
        }

        for (String outflowDomainName : outflowSecurityDomainNames) {
            InjectedValue<SecurityDomain> outflowDomainInjector = new InjectedValue<>();
            domainBuilder.addDependency(context.getCapabilityServiceName(SECURITY_DOMAIN_CAPABILITY, outflowDomainName, SecurityDomain.class).append(INITIAL), SecurityDomain.class, outflowDomainInjector);
            outflowSecurityDomainInjectors.add(outflowDomainInjector);
        }

        // This depends on the initial service which depends on the common dependencies so no need to add them for this one.
        return domainBuilder.install();
    }

    private static SecurityIdentity outflow(final SecurityIdentity identity, final boolean outflowAnonymous, final Set<SecurityDomain> outflowDomains) {
        return identity.withSecurityIdentitySupplier(new Supplier<SecurityIdentity[]>() {

            private volatile SecurityIdentity[] outflowIdentities = null;

            @Override
            public SecurityIdentity[] get() {
                SecurityIdentity[] outflowIdentities = this.outflowIdentities;
                if (outflowIdentities == null) {
                    // We could synchronize on the identity but if anything outside
                    // also synchronizes we could get unpredictable locking.
                    synchronized(this) {
                        outflowIdentities = this.outflowIdentities;
                        if (outflowIdentities == null) {
                            if (WildFlySecurityManager.isChecking()) {
                                outflowIdentities = doPrivileged((PrivilegedAction<SecurityIdentity[]>) () -> performOutflow(identity, outflowAnonymous, outflowDomains));
                            } else {
                                outflowIdentities = performOutflow(identity, outflowAnonymous, outflowDomains);
                            }

                            this.outflowIdentities = outflowIdentities;
                        }
                    }
                }

                return outflowIdentities;
            }
        });
    }

    private static SecurityIdentity[] performOutflow(SecurityIdentity identity, boolean outflowAnonymous, Set<SecurityDomain> outflowDomains) {
        List<SecurityIdentity> outflowIdentities = new ArrayList<>(outflowDomains.size());
        outflowDomains.forEach(d -> {
            ServerAuthenticationContext sac = d.createNewAuthenticationContext();
            try {
                if (sac.importIdentity(identity)) {
                    outflowIdentities.add(sac.getAuthorizedIdentity());
                } else if (outflowAnonymous) {
                    outflowIdentities.add(d.getAnonymousSecurityIdentity());
                }
            } catch (RealmUnavailableException e) {
                throw ROOT_LOGGER.unableToPerformOutflow(identity.getPrincipal().getName(), e);
            }
        });

        return outflowIdentities.toArray(new SecurityIdentity[outflowIdentities.size()]);
    }

    private static void injectPrincipalTransformer(String principalTransformer, OperationContext context, ServiceBuilder<SecurityDomain> domainBuilder, Injector<PrincipalTransformer> injector) {
        if (principalTransformer == null) {
            return;
        }

        if (injector == null) {
            // Service did not supply one as one is already present for this name.
            return;
        }

        String runtimeCapability = RuntimeCapability.buildDynamicCapabilityName(PRINCIPAL_TRANSFORMER_CAPABILITY, principalTransformer);
        ServiceName principalTransformerServiceName = context.getCapabilityServiceName(runtimeCapability, PrincipalTransformer.class);

        domainBuilder.addDependency(principalTransformerServiceName, PrincipalTransformer.class, injector);
    }

    private static void injectRoleMapper(String roleMapper, OperationContext context, ServiceBuilder<SecurityDomain> domainBuilder, Injector<RoleMapper> injector) {
        if (roleMapper == null) {
            return;
        }

        if (injector == null) {
            // Service did not supply one as one is already present for this name.
            return;
        }

        String runtimeCapability = RuntimeCapability.buildDynamicCapabilityName(ROLE_MAPPER_CAPABILITY, roleMapper);
        ServiceName roleMapperServiceName = context.getCapabilityServiceName(runtimeCapability, RoleMapper.class);

        domainBuilder.addDependency(roleMapperServiceName, RoleMapper.class, injector);
    }

    private static void injectRoleDecoder(String roleDecoder, OperationContext context, ServiceBuilder<SecurityDomain> domainBuilder, Injector<RoleDecoder> injector) {
        if (roleDecoder == null) {
            return;
        }
        if (injector == null) {
            return;
        }
        String runtimeCapability = RuntimeCapability.buildDynamicCapabilityName(ROLE_DECODER_CAPABILITY, roleDecoder);
        ServiceName roleDecoderServiceName = context.getCapabilityServiceName(runtimeCapability, RoleDecoder.class);
        domainBuilder.addDependency(roleDecoderServiceName, RoleDecoder.class, injector);
    }

    private static class DomainAddHandler extends BaseAddHandler {

        private DomainAddHandler() {
            super(SECURITY_DOMAIN_RUNTIME_CAPABILITY, ATTRIBUTES);
        }

        @Override
        protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource)
                throws OperationFailedException {
            super.populateModel(context, operation, resource);
            context.addStep(new DomainValidationHandler(), Stage.MODEL);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            RuntimeCapability<Void> runtimeCapability = SECURITY_DOMAIN_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName domainName = runtimeCapability.getCapabilityServiceName(SecurityDomain.class);

            installService(context, domainName, model);
        }

    }

    private static class DomainRemoveHandler extends TrivialCapabilityServiceRemoveHandler {

        DomainRemoveHandler(AbstractAddStepHandler addHandler) {
            super(addHandler, SECURITY_DOMAIN_RUNTIME_CAPABILITY);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) {
            super.performRuntime(context, operation, model);
            if (context.isResourceServiceRestartAllowed()) {
                final PathAddress address = context.getCurrentAddress();
                final String name = address.getLastElement().getValue();
                context.removeService(serviceName(name, address).append(INITIAL));
            }
        }
    }

    private static class WriteAttributeHandler extends ElytronRestartParentWriteAttributeHandler {

        WriteAttributeHandler(String parentKeyName) {
            super(parentKeyName, ATTRIBUTES);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress pathAddress) {
            return SECURITY_DOMAIN_RUNTIME_CAPABILITY.fromBaseCapability(pathAddress.getLastElement().getValue()).getCapabilityServiceName(SecurityDomain.class);
        }

        @Override
        protected void recreateParentService(OperationContext context, PathAddress parentAddress, ModelNode parentModel)
                throws OperationFailedException {
            installService(context, getParentServiceName(parentAddress), parentModel);
        }

        protected void validateUpdatedModel(final OperationContext context, final Resource resource) throws OperationFailedException {
            // Defer validation to end of model stage.
            context.addStep(new DomainValidationHandler(), Stage.MODEL);
        }

    }

    private static class DomainValidationHandler implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();


            List<ModelNode> realms = REALMS.resolveModelAttribute(context, model).asList();

            Set<String> realmNames = new HashSet<>(realms.size());

            for(ModelNode realm : realms) {
                String realmName = REALM_NAME.resolveModelAttribute(context, realm).asString();
                if (realmNames.add(realmName) == false) {
                    throw ROOT_LOGGER.realmRefererencedTwice(realmName);
                }
            }

            String defaultRealm = DomainDefinition.DEFAULT_REALM.resolveModelAttribute(context, model).asString();
            if (realmNames.contains(defaultRealm) == false) {
                StringBuilder realmsStringBuilder = new StringBuilder();
                for(String currentRealm : realmNames) {
                    if (realmsStringBuilder.length() != 0) realmsStringBuilder.append(", ");
                    realmsStringBuilder.append(currentRealm);
                }
                throw ROOT_LOGGER.defaultRealmNotReferenced(defaultRealm, realmsStringBuilder.toString());
            }
        }
    }

}

