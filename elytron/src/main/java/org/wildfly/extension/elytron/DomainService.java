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

import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.elytron.capabilities.PrincipalTransformer;
import org.wildfly.extension.elytron.capabilities._private.SecurityEventListener;
import org.wildfly.security.auth.server.EvidenceDecoder;
import org.wildfly.security.auth.server.PrincipalDecoder;
import org.wildfly.security.auth.server.RealmMapper;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityDomain.RealmBuilder;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.authz.PermissionMapper;
import org.wildfly.security.authz.RoleDecoder;
import org.wildfly.security.authz.RoleMapper;


/**
 * A {@link Service} responsible for managing the lifecycle of a single {@link SecurityDomain}.
 *
 * <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class DomainService implements Service<SecurityDomain> {

    private volatile SecurityDomain securityDomain;

    private final String defaultRealm;
    private final Predicate<SecurityDomain> trustedSecurityDomain;
    private final UnaryOperator<SecurityIdentity> identityOperator;
    private String preRealmPrincipalTransformer;
    private String postRealmPrincipalTransformer;
    private String roleMapper;
    private String roleDecoder;

    private final Map<String, RealmDependency> realms = new HashMap<>();
    private final Map<String, InjectedValue<PrincipalTransformer>> principalTransformers = new HashMap<>();
    private final Map<String, InjectedValue<RoleMapper>> roleMappers = new HashMap<>();
    private final Map<String, InjectedValue<RoleDecoder>> roleDecoders = new HashMap<>();
    private final InjectedValue<PrincipalDecoder> principalDecoderInjector = new InjectedValue<>();
    private final InjectedValue<RealmMapper> realmMapperInjector = new InjectedValue<>();
    private final InjectedValue<PermissionMapper> permissionMapperInjector = new InjectedValue<>();
    private final InjectedValue<EvidenceDecoder> evidenceDecoderInjector = new InjectedValue<>();
    private final InjectedValue<SecurityEventListener> securityEventListenerInjector = new InjectedValue<>();

    DomainService(final String defaultRealm, final Predicate<SecurityDomain> trustedSecurityDomain, final UnaryOperator<SecurityIdentity> identityOperator) {
        this.defaultRealm = defaultRealm;
        this.trustedSecurityDomain = trustedSecurityDomain;
        this.identityOperator = identityOperator;
    }

    RealmDependency createRealmDependency(final String realmName) throws OperationFailedException {
        if (realms.containsKey(realmName)) {
            throw ROOT_LOGGER.duplicateRealmInjection(realmName);
        }

        RealmDependency realmDependency = new RealmDependency();
        realms.put(realmName, realmDependency);
        return realmDependency;
    }

    private Injector<PrincipalTransformer> createPrincipalTransformerInjector(final String principalTransformerName) {
        if (principalTransformers.containsKey(principalTransformerName)) {
            return null; // i.e. should already be injected for this name.
        }

        InjectedValue<PrincipalTransformer> principalTransformerInjector = new InjectedValue<>();
        principalTransformers.put(principalTransformerName, principalTransformerInjector);
        return principalTransformerInjector;
    }

    private Injector<RoleMapper> createRoleMapperInjector(final String roleMapperName) {
        if (roleMappers.containsKey(roleMapperName)) {
            return null; // i.e. should already be injected for this name.
        }

        InjectedValue<RoleMapper> roleMapperInjector = new InjectedValue<>();
        roleMappers.put(roleMapperName, roleMapperInjector);
        return roleMapperInjector;
    }

    private Injector<RoleDecoder> createRoleDecoderInjector(final String roleDecoderName) {
        if (roleDecoders.containsKey(roleDecoderName)) {
            return null; // i.e. should already be injected for this name.
        }

        InjectedValue<RoleDecoder> roleDecoderInjector = new InjectedValue<>();
        roleDecoders.put(roleDecoderName, roleDecoderInjector);
        return roleDecoderInjector;
    }

    Injector<PrincipalDecoder> getPrincipalDecoderInjector() {
        return principalDecoderInjector;
    }

    Injector<RealmMapper> getRealmMapperInjector() {
        return realmMapperInjector;
    }

    Injector<PermissionMapper> getPermissionMapperInjector() {
        return permissionMapperInjector;
    }

    Injector<PrincipalTransformer> createPreRealmPrincipalTransformerInjector(final String name) {
        this.preRealmPrincipalTransformer = name;

        return createPrincipalTransformerInjector(name);
    }

    Injector<PrincipalTransformer> createPostRealmPrincipalTransformerInjector(final String name) {
        this.postRealmPrincipalTransformer = name;

        return createPrincipalTransformerInjector(name);
    }

    Injector<RoleMapper> createDomainRoleMapperInjector(final String name) {
        this.roleMapper = name;

        return createRoleMapperInjector(name);
    }

    Injector<EvidenceDecoder> getEvidenceDecoderInjector() {
        return evidenceDecoderInjector;
    }

    Injector<SecurityEventListener> getSecurityEventListenerInjector() {
        return securityEventListenerInjector;
    }

    Injector<RoleDecoder> createDomainRoleDecoderInjector(final String name) {
        this.roleDecoder = name;
        return createRoleDecoderInjector(name);
    }

    @Override
    public void start(StartContext context) throws StartException {
        SecurityDomain.Builder builder = SecurityDomain.builder();

        if (preRealmPrincipalTransformer != null) {
            builder.setPreRealmRewriter(principalTransformers.get(preRealmPrincipalTransformer).getValue());
        }
        if (postRealmPrincipalTransformer != null) {
            builder.setPostRealmRewriter(principalTransformers.get(postRealmPrincipalTransformer).getValue());
        }
        PrincipalDecoder principalDecoder = principalDecoderInjector.getOptionalValue();
        if (principalDecoder != null) {
            builder.setPrincipalDecoder(PrincipalDecoder.aggregate(principalDecoder, PrincipalDecoder.DEFAULT));
        }
        RealmMapper realmMapper = realmMapperInjector.getOptionalValue();
        if (realmMapper != null) {
            builder.setRealmMapper(realmMapper);
        }
        PermissionMapper permissionMapper = permissionMapperInjector.getOptionalValue();
        if (permissionMapper != null) {
            builder.setPermissionMapper(permissionMapper);
        }
        if (roleMapper != null) {
            builder.setRoleMapper(roleMappers.get(roleMapper).getValue());
        }
        EvidenceDecoder evidenceDecoder = evidenceDecoderInjector.getOptionalValue();
        if (evidenceDecoder != null) {
            builder.setEvidenceDecoder(evidenceDecoder);
        }
        if (roleDecoder != null) {
            builder.setRoleDecoder(roleDecoders.get(roleDecoder).getValue());
        }
        if (defaultRealm != null) {
            builder.setDefaultRealmName(defaultRealm);
        }
        for (Entry<String, RealmDependency> entry : realms.entrySet()) {
            String realmName = entry.getKey();
            RealmDependency realmDependency = entry.getValue();
            RealmBuilder realmBuilder = builder.addRealm(realmName, realmDependency.securityRealmInjector.getValue());
            if (realmDependency.principalTransformer != null) {
                realmBuilder.setPrincipalRewriter(principalTransformers.get(realmDependency.principalTransformer).getValue());
            }
            if (realmDependency.roleDecoder != null) {
                RoleDecoder roleDecoder = roleDecoders.get(realmDependency.roleDecoder).getOptionalValue();
                if (roleDecoder != null) {
                    realmBuilder.setRoleDecoder(roleDecoder);
                }
            }
            if (realmDependency.roleMapper != null) {
                realmBuilder.setRoleMapper(roleMappers.get(realmDependency.roleMapper).getValue());
            }
            realmBuilder.build();
        }

        builder.setTrustedSecurityDomainPredicate(trustedSecurityDomain);
        builder.setSecurityIdentityTransformer(identityOperator);
        SecurityEventListener securityEventListener = securityEventListenerInjector.getOptionalValue();
        if (securityEventListener != null) {
            builder.setSecurityEventListener(securityEventListener);
        }

        securityDomain = builder.build();
    }

    @Override
    public void stop(StopContext context) {
       securityDomain = null;
    }

    @Override
    public SecurityDomain getValue() throws IllegalStateException, IllegalArgumentException {
        return securityDomain;
    }

    class RealmDependency {

        private InjectedValue<SecurityRealm> securityRealmInjector = new InjectedValue<>();

        private String principalTransformer;

        private String roleMapper;

        private String roleDecoder;

        Injector<SecurityRealm> getSecurityRealmInjector() {
            return securityRealmInjector;
        }

        Injector<PrincipalTransformer> getPrincipalTransformerInjector(final String name) {
            this.principalTransformer = name;
            return createPrincipalTransformerInjector(name);
        }

        Injector<RoleDecoder> getRoleDecoderInjector(final String name) {
            this.roleDecoder = name;
            return createRoleDecoderInjector(name);
        }

        Injector<RoleMapper> getRoleMapperInjector(final String name) {
            this.roleMapper = name;
            return createRoleMapperInjector(name);
        }

    }
}
