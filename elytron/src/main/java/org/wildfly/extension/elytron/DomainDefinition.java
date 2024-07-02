/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static java.security.AccessController.doPrivileged;
import static org.wildfly.extension.elytron.Capabilities.EVIDENCE_DECODER_CAPABILITY;
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
import static org.wildfly.extension.elytron.Capabilities.VIRTUAL_SECURITY_DOMAIN_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.INITIAL;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
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
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.security.VirtualDomainMetaData;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.elytron.DomainService.RealmDependency;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;
import org.wildfly.extension.elytron.capabilities.PrincipalTransformer;
import org.wildfly.extension.elytron.capabilities._private.SecurityEventListener;
import org.wildfly.security.auth.server.EvidenceDecoder;
import org.wildfly.security.auth.server.PrincipalDecoder;
import org.wildfly.security.auth.server.RealmMapper;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.auth.server.ServerAuthenticationContext;
import org.wildfly.security.authz.Attributes;
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

    static final SimpleAttributeDefinition DEFAULT_REALM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DEFAULT_REALM, ModelType.STRING, true)
         .setAllowExpression(false)
         .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
         .build();

    static final SimpleAttributeDefinition PRE_REALM_PRINCIPAL_TRANSFORMER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PRE_REALM_PRINCIPAL_TRANSFORMER, ModelType.STRING, true)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setCapabilityReference(PRINCIPAL_TRANSFORMER_CAPABILITY, SECURITY_DOMAIN_CAPABILITY)
        .build();

    static final SimpleAttributeDefinition POST_REALM_PRINCIPAL_TRANSFORMER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.POST_REALM_PRINCIPAL_TRANSFORMER, ModelType.STRING, true)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setCapabilityReference(PRINCIPAL_TRANSFORMER_CAPABILITY, SECURITY_DOMAIN_CAPABILITY)
        .build();

    static final SimpleAttributeDefinition PRINCIPAL_DECODER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PRINCIPAL_DECODER, ModelType.STRING, true)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setCapabilityReference(PRINCIPAL_DECODER_CAPABILITY, SECURITY_DOMAIN_CAPABILITY)
        .build();

    static final SimpleAttributeDefinition PERMISSION_MAPPER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PERMISSION_MAPPER, ModelType.STRING, true)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setCapabilityReference(PERMISSION_MAPPER_CAPABILITY, SECURITY_DOMAIN_CAPABILITY)
        .build();

    static final SimpleAttributeDefinition REALM_MAPPER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REALM_MAPPER, ModelType.STRING, true)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setCapabilityReference(REALM_MAPPER_CAPABILITY, SECURITY_DOMAIN_CAPABILITY)
        .build();

    static final SimpleAttributeDefinition ROLE_MAPPER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ROLE_MAPPER, ModelType.STRING, true)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setCapabilityReference(ROLE_MAPPER_CAPABILITY, SECURITY_DOMAIN_CAPABILITY)
        .build();

    static final SimpleAttributeDefinition EVIDENCE_DECODER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.EVIDENCE_DECODER, ModelType.STRING, true)
            .setMinSize(1)
            .setCapabilityReference(EVIDENCE_DECODER_CAPABILITY, SECURITY_DOMAIN_CAPABILITY)
            .build();

    static final SimpleAttributeDefinition REALM_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REALM, ModelType.STRING, false)
        .setXmlName(ElytronDescriptionConstants.NAME)
        .setMinSize(1)
        .setCapabilityReference(SECURITY_REALM_CAPABILITY, SECURITY_DOMAIN_CAPABILITY)
        .build();

    static final SimpleAttributeDefinition REALM_PRINCIPAL_TRANSFORMER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PRINCIPAL_TRANSFORMER, ModelType.STRING, true)
        .setMinSize(1)
        .setCapabilityReference(PRINCIPAL_TRANSFORMER_CAPABILITY, SECURITY_DOMAIN_CAPABILITY)
        .build();

    static final SimpleAttributeDefinition REALM_ROLE_DECODER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ROLE_DECODER, ModelType.STRING, true)
        .setMinSize(1)
        .setCapabilityReference(ROLE_DECODER_CAPABILITY, SECURITY_DOMAIN_CAPABILITY)
        .build();

    static final SimpleAttributeDefinition ROLE_DECODER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ROLE_DECODER, ModelType.STRING, true)
            .setMinSize(1)
            .setCapabilityReference(ROLE_DECODER_CAPABILITY, SECURITY_DOMAIN_CAPABILITY)
            .build();

    static final ObjectTypeAttributeDefinition REALM = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.REALM, REALM_NAME, REALM_PRINCIPAL_TRANSFORMER, REALM_ROLE_DECODER, ROLE_MAPPER)
        .setRequired(true)
        .build();

    static final ObjectListAttributeDefinition REALMS = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.REALMS, REALM)
            .setRequired(false)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setAttributeParser(AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER)
            .setAttributeMarshaller(AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
            .build();

    static final StringListAttributeDefinition TRUSTED_SECURITY_DOMAINS = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.TRUSTED_SECURITY_DOMAINS)
            .setRequired(false)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilityReference(SECURITY_DOMAIN_CAPABILITY, SECURITY_DOMAIN_CAPABILITY)
            .build();

    static final StringListAttributeDefinition TRUSTED_VIRTUAL_SECURITY_DOMAINS = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.TRUSTED_VIRTUAL_SECURITY_DOMAINS)
            .setRequired(false)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilityReference(VIRTUAL_SECURITY_DOMAIN_CAPABILITY, SECURITY_DOMAIN_CAPABILITY)
            .build();

    static final SimpleAttributeDefinition OUTFLOW_ANONYMOUS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.OUTFLOW_ANONYMOUS, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .setRequires(ElytronDescriptionConstants.OUTFLOW_SECURITY_DOMAINS)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final StringListAttributeDefinition OUTFLOW_SECURITY_DOMAINS = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.OUTFLOW_SECURITY_DOMAINS)
            .setRequired(false)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilityReference(SECURITY_DOMAIN_CAPABILITY, SECURITY_DOMAIN_CAPABILITY)
            .build();

    static final SimpleAttributeDefinition SECURITY_EVENT_LISTENER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SECURITY_EVENT_LISTENER, ModelType.STRING, true)
            .setAllowExpression(false)
            .setCapabilityReference(SECURITY_EVENT_LISTENER_CAPABILITY, SECURITY_DOMAIN_CAPABILITY)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { PRE_REALM_PRINCIPAL_TRANSFORMER, POST_REALM_PRINCIPAL_TRANSFORMER, PRINCIPAL_DECODER,
            REALM_MAPPER, ROLE_MAPPER, PERMISSION_MAPPER, DEFAULT_REALM, REALMS, TRUSTED_SECURITY_DOMAINS, TRUSTED_VIRTUAL_SECURITY_DOMAINS, OUTFLOW_ANONYMOUS, OUTFLOW_SECURITY_DOMAINS, SECURITY_EVENT_LISTENER,
            EVIDENCE_DECODER, ROLE_DECODER};

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
        ReadSecurityDomainIdentityHandler.register(resourceRegistration, getResourceDescriptionResolver());
    }

    private static ServiceController<SecurityDomain> installInitialService(OperationContext context, ServiceName initialName, ModelNode model,
            Predicate<SecurityDomain> trustedSecurityDomain, UnaryOperator<SecurityIdentity> identityOperator) throws OperationFailedException {
        ServiceTarget serviceTarget = context.getServiceTarget();

        String defaultRealm = DomainDefinition.DEFAULT_REALM.resolveModelAttribute(context, model).asStringOrNull();
        ModelNode realms = REALMS.resolveModelAttribute(context, model);

        String preRealmPrincipalTransformer = PRE_REALM_PRINCIPAL_TRANSFORMER.resolveModelAttribute(context, model).asStringOrNull();
        String postRealmPrincipalTransformer = POST_REALM_PRINCIPAL_TRANSFORMER.resolveModelAttribute(context, model).asStringOrNull();
        String principalDecoder = PRINCIPAL_DECODER.resolveModelAttribute(context, model).asStringOrNull();
        String permissionMapper = PERMISSION_MAPPER.resolveModelAttribute(context, model).asStringOrNull();
        String realmMapper = REALM_MAPPER.resolveModelAttribute(context, model).asStringOrNull();
        String roleMapper = ROLE_MAPPER.resolveModelAttribute(context, model).asStringOrNull();
        String evidenceDecoder = EVIDENCE_DECODER.resolveModelAttribute(context, model).asStringOrNull();
        String securityEventListener = SECURITY_EVENT_LISTENER.resolveModelAttribute(context, model).asStringOrNull();
        String roleDecoder = ROLE_DECODER.resolveModelAttribute(context, model).asStringOrNull();

        DomainService domain = new DomainService(defaultRealm, trustedSecurityDomain, identityOperator);

        ServiceBuilder<SecurityDomain> domainBuilder = serviceTarget.addService(initialName, domain)
                .setInitialMode(Mode.LAZY);

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

        if (evidenceDecoder != null) {
            String runtimeCapability = RuntimeCapability.buildDynamicCapabilityName(EVIDENCE_DECODER_CAPABILITY, evidenceDecoder);
            ServiceName evidenceDecoderServiceName = context.getCapabilityServiceName(runtimeCapability, EvidenceDecoder.class);
            domainBuilder.addDependency(evidenceDecoderServiceName, EvidenceDecoder.class, domain.getEvidenceDecoderInjector());
        }

        if (securityEventListener != null) {
            domainBuilder.addDependency(
                    context.getCapabilityServiceName(SECURITY_EVENT_LISTENER_CAPABILITY, securityEventListener, SecurityEventListener.class),
                    SecurityEventListener.class, domain.getSecurityEventListenerInjector());
        }

        if (roleDecoder != null) {
            injectRoleDecoder(roleDecoder, context, domainBuilder, domain.createDomainRoleDecoderInjector(roleDecoder));
        }

        if (realms.isDefined()) {
            for (ModelNode current : realms.asList()) {
                String realmName = REALM_NAME.resolveModelAttribute(context, current).asString();
                String runtimeCapability = RuntimeCapability.buildDynamicCapabilityName(SECURITY_REALM_CAPABILITY, realmName);
                ServiceName realmServiceName = context.getCapabilityServiceName(runtimeCapability, SecurityRealm.class);

                RealmDependency realmDependency = domain.createRealmDependency(realmName);
                REALM_SERVICE_UTIL.addInjection(domainBuilder, realmDependency.getSecurityRealmInjector(), realmServiceName);

                String principalTransformer = REALM_PRINCIPAL_TRANSFORMER.resolveModelAttribute(context, current).asStringOrNull();
                if (principalTransformer != null) {
                    Injector<PrincipalTransformer> principalTransformerInjector = realmDependency.getPrincipalTransformerInjector(principalTransformer);
                    injectPrincipalTransformer(principalTransformer, context, domainBuilder, principalTransformerInjector);
                }
                String realmRoleMapper = ROLE_MAPPER.resolveModelAttribute(context, current).asStringOrNull();
                if (realmRoleMapper != null) {
                    injectRoleMapper(realmRoleMapper, context, domainBuilder, realmDependency.getRoleMapperInjector(realmRoleMapper));
                }
                String realmRoleDecoder = REALM_ROLE_DECODER.resolveModelAttribute(context, current).asStringOrNull();
                if (realmRoleDecoder != null) {
                    injectRoleDecoder(realmRoleDecoder, context, domainBuilder, realmDependency.getRoleDecoderInjector(realmRoleDecoder));
                }
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

        List<String> trustedVirtualSecurityDomainNames = TRUSTED_VIRTUAL_SECURITY_DOMAINS.unwrap(context, model);
        final List<InjectedValue<VirtualDomainMetaData>> trustedVirtualSecurityDomainInjectors = new ArrayList<>(trustedVirtualSecurityDomainNames.size());
        final Set<VirtualDomainMetaData> trustedVirtualSecurityDomains = new HashSet<>();

        List<String> outflowSecurityDomainNames = OUTFLOW_SECURITY_DOMAINS.unwrap(context, model);
        final boolean outflowAnonymous = OUTFLOW_ANONYMOUS.resolveModelAttribute(context, model).asBoolean();
        final List<InjectedValue<SecurityDomain>> outflowSecurityDomainInjectors = new ArrayList<>(outflowSecurityDomainNames.size());
        final Set<SecurityDomain> outflowSecurityDomains = new HashSet<>();

        Predicate<SecurityDomain> isTrustedSecurityDomain = domain -> {
            if (trustedSecurityDomains.contains(domain)) {
                return true;
            }
            for (VirtualDomainMetaData trustedVirtualDomainMetaData : trustedVirtualSecurityDomains) {
                if (trustedVirtualDomainMetaData.getSecurityDomain() != null && trustedVirtualDomainMetaData.getSecurityDomain().equals(domain)) {
                    return true;
                }
            }
            return false;
        };

        installInitialService(context, initialName, model, isTrustedSecurityDomain,
                !outflowSecurityDomainNames.isEmpty() ? i -> outflow(i, outflowAnonymous, outflowSecurityDomains) : UnaryOperator.identity());

        TrivialService<SecurityDomain> finalDomainService = new TrivialService<SecurityDomain>();
        finalDomainService.setValueSupplier(new ValueSupplier<SecurityDomain>() {

            @Override
            public SecurityDomain get() throws StartException {
                for (InjectedValue<SecurityDomain> trustedSecurityDomainInjector : trustedSecurityDomainInjectors) {
                    trustedSecurityDomains.add(trustedSecurityDomainInjector.getValue());
                }
                for (InjectedValue<VirtualDomainMetaData> trustedVirtualSecurityDomainInjector : trustedVirtualSecurityDomainInjectors) {
                    trustedVirtualSecurityDomains.add(trustedVirtualSecurityDomainInjector.getValue());
                }
                for (InjectedValue<SecurityDomain> i : outflowSecurityDomainInjectors) {
                    outflowSecurityDomains.add(i.getValue());
                }
                return securityDomain.getValue();
            }

            @Override
            public void dispose() {
                trustedSecurityDomains.clear();
                trustedVirtualSecurityDomains.clear();
            }

        });

        ServiceTarget serviceTarget = context.getServiceTarget();
        ServiceBuilder<SecurityDomain> domainBuilder = serviceTarget.addService(domainName, finalDomainService)
                .setInitialMode(Mode.LAZY);
        domainBuilder.addDependency(initialName, SecurityDomain.class, securityDomain);
        for (String trustedDomainName : trustedSecurityDomainNames) {
            InjectedValue<SecurityDomain> trustedDomainInjector = new InjectedValue<>();
            domainBuilder.addDependency(context.getCapabilityServiceName(SECURITY_DOMAIN_CAPABILITY, trustedDomainName, SecurityDomain.class).append(INITIAL), SecurityDomain.class, trustedDomainInjector);
            trustedSecurityDomainInjectors.add(trustedDomainInjector);
        }

        for (String trustedVirtualDomainName : trustedVirtualSecurityDomainNames) {
            InjectedValue<VirtualDomainMetaData> trustedVirtualDomainInjector = new InjectedValue<>();
            domainBuilder.addDependency(context.getCapabilityServiceName(VIRTUAL_SECURITY_DOMAIN_CAPABILITY, trustedVirtualDomainName, VirtualDomainMetaData.class).append(INITIAL), VirtualDomainMetaData.class, trustedVirtualDomainInjector);
            trustedVirtualSecurityDomainInjectors.add(trustedVirtualDomainInjector);
        }

        for (String outflowDomainName : outflowSecurityDomainNames) {
            InjectedValue<SecurityDomain> outflowDomainInjector = new InjectedValue<>();
            domainBuilder.addDependency(context.getCapabilityServiceName(SECURITY_DOMAIN_CAPABILITY, outflowDomainName, SecurityDomain.class).append(INITIAL), SecurityDomain.class, outflowDomainInjector);
            outflowSecurityDomainInjectors.add(outflowDomainInjector);
        }

        // This depends on the initial service which depends on the common dependencies so no need to add them for this one.
        return domainBuilder.install();
    }

    static SecurityIdentity outflow(final SecurityIdentity identity, final boolean outflowAnonymous, final Set<SecurityDomain> outflowDomains) {
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

    static SecurityIdentity[] performOutflow(SecurityIdentity identity, boolean outflowAnonymous, Set<SecurityDomain> outflowDomains) {
        List<SecurityIdentity> outflowIdentities = new ArrayList<>(outflowDomains.size());
        for (SecurityDomain d : outflowDomains) {
            try(ServerAuthenticationContext sac = d.createNewAuthenticationContext()) {
                if (sac.importIdentity(identity)) {
                    outflowIdentities.add(sac.getAuthorizedIdentity());
                } else if (outflowAnonymous) {
                    outflowIdentities.add(d.getAnonymousSecurityIdentity());
                }
            } catch (RealmUnavailableException e) {
                throw ROOT_LOGGER.unableToPerformOutflow(identity.getPrincipal().getName(), e);
            }
        }

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
            super(SECURITY_DOMAIN_RUNTIME_CAPABILITY);
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
        protected void removeServices(final OperationContext context, final ServiceName parentService, final ModelNode parentModel) throws OperationFailedException {
            // WFCORE-2632, just for security-domain, remove also service with initial suffix.
            context.removeService(parentService.append(INITIAL));
            super.removeServices(context, parentService, parentModel);
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

            ModelNode realmsNode = REALMS.resolveModelAttribute(context, model);
            List<ModelNode> realms = realmsNode.isDefined() ? realmsNode.asList() : Collections.emptyList();

            Set<String> realmNames = new HashSet<>(realms.size());

            for(ModelNode realm : realms) {
                String realmName = REALM_NAME.resolveModelAttribute(context, realm).asString();
                if (! realmNames.add(realmName)) {
                    throw ROOT_LOGGER.realmRefererencedTwice(realmName);
                }
            }

            String defaultRealm = DomainDefinition.DEFAULT_REALM.resolveModelAttribute(context, model).asStringOrNull();
            if (defaultRealm != null && ! realmNames.contains(defaultRealm)) {
                StringBuilder realmsStringBuilder = new StringBuilder();
                for(String currentRealm : realmNames) {
                    if (realmsStringBuilder.length() != 0) realmsStringBuilder.append(", ");
                    realmsStringBuilder.append(currentRealm);
                }
                throw ROOT_LOGGER.defaultRealmNotReferenced(defaultRealm, realmsStringBuilder.toString());
            }
        }
    }

    static class ReadSecurityDomainIdentityHandler extends ElytronRuntimeOnlyHandler {

        public static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NAME, ModelType.STRING, false)
                .setAllowExpression(false)
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.READ_IDENTITY, descriptionResolver)
                            .setParameters(NAME)
                            .setRuntimeOnly()
                            .setReadOnly()
                            .build(),
                    new ReadSecurityDomainIdentityHandler());
        }

        private ReadSecurityDomainIdentityHandler() {
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            // Retrieving the modifiable registry here as the SecurityDomain is used to create a new authentication
            // context.
            ServiceRegistry serviceRegistry = context.getServiceRegistry(true);
            RuntimeCapability<Void> runtimeCapability = SECURITY_DOMAIN_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName domainServiceName = runtimeCapability.getCapabilityServiceName(SecurityDomain.class);
            ServiceController<SecurityDomain> serviceController = getRequiredService(serviceRegistry, domainServiceName, SecurityDomain.class);
            startSecurityDomainServiceIfNotUp(serviceController);
            SecurityDomain domain = serviceController.getValue();
            String principalName = NAME.resolveModelAttribute(context, operation).asString();

            try(ServerAuthenticationContext authenticationContext = domain.createNewAuthenticationContext()) {
                authenticationContext.setAuthenticationName(principalName);

                if (!authenticationContext.exists()) {
                    context.getFailureDescription().set(ROOT_LOGGER.identityNotFound(principalName));
                    return;
                }

                if (!authenticationContext.authorize(principalName)) {
                    context.getFailureDescription().set(ROOT_LOGGER.identityNotAuthorized(principalName));
                    return;
                }

                SecurityIdentity identity = authenticationContext.getAuthorizedIdentity();
                ModelNode result = context.getResult();

                result.get(ElytronDescriptionConstants.NAME).set(principalName);

                ModelNode attributesNode = result.get(ElytronDescriptionConstants.ATTRIBUTES);

                for (Attributes.Entry entry : identity.getAttributes().entries()) {
                    ModelNode entryNode = attributesNode.get(entry.getKey()).setEmptyList();
                    for (String s : entry) {
                        entryNode.add(s);
                    }
                }

                ModelNode rolesNode = result.get(ElytronDescriptionConstants.ROLES);
                for (String s : identity.getRoles()) {
                    rolesNode.add(s);
                }
            } catch (RealmUnavailableException e) {
                throw ROOT_LOGGER.couldNotReadIdentity(principalName, domainServiceName, e);
            }
        }
    }

    private static void startSecurityDomainServiceIfNotUp(ServiceController<SecurityDomain> serviceController) throws OperationFailedException {
        if (serviceController.getState() != ServiceController.State.UP) {
            serviceController.setMode(Mode.ACTIVE);
            try {
                serviceController.awaitValue();
            } catch (InterruptedException e) {
                throw new OperationFailedException(e);
            }
        }
    }
}
