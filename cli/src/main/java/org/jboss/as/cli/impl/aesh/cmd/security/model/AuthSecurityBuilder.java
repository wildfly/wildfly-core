/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

import static org.wildfly.common.Assert.checkNotNullParamWithNullPointerException;

import java.io.IOException;
import java.util.List;
import org.aesh.command.CommandException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.dmr.ModelNode;

/**
 * Main class to build the requests to enable/disable authentication on any kind
 * of interface/factory.
 *
 * @author jdenise@redhat.com
 */
public class AuthSecurityBuilder {

    private String newSecurityDomain;
    private String newFactoryName;
    private String newRealmName;
    private String activeFactoryName;
    private final ModelNode composite = new ModelNode();
    private final AuthMechanism mechanism;
    private AuthFactory authFactory;
    private final AuthFactory ootbFactory;
    private final List<String> order;
    private final AuthFactorySpec spec;
    private final String securityDomain;

    public AuthSecurityBuilder(AuthMechanism mechanism, AuthFactorySpec spec) throws CommandException {
        this.mechanism = checkNotNullParamWithNullPointerException("mechanism", mechanism);
        ootbFactory = null;
        order = null;
        securityDomain = null;
        this.spec = checkNotNullParamWithNullPointerException("spec", spec);
        init();
    }

    public AuthSecurityBuilder(AuthFactory ootbFactory) throws CommandException {
        mechanism = null;
        this.ootbFactory = checkNotNullParamWithNullPointerException("ootbFactory", ootbFactory);
        order = null;
        securityDomain = null;
        spec = ootbFactory.getSpec();
        init();
    }

    public AuthSecurityBuilder(String securityDomain) throws CommandException {
        this.securityDomain = checkNotNullParamWithNullPointerException("securityDomain", securityDomain);
        order = null;
        mechanism = null;
        ootbFactory = null;
        spec = null;
        init();
    }

    public AuthSecurityBuilder(List<String> order) {
        this.order = checkNotNullParamWithNullPointerException("order", order);
        mechanism = null;
        this.ootbFactory = null;
        init();
        spec = AuthFactorySpec.SASL;
        securityDomain = null;
    }

    private void init() {
        composite.get(Util.OPERATION).set(Util.COMPOSITE);
        composite.get(Util.ADDRESS).setEmptyList();
    }

    public ModelNode getRequest() {
        return composite;
    }

    public ModelNode getSteps() {
        return composite.get(Util.STEPS);
    }

    public AuthFactory getAuthFactory() {
        return ootbFactory == null ? authFactory : ootbFactory;
    }

    public String getReferencedSecurityDomain() {
        return securityDomain;
    }

    public AuthSecurityBuilder setNewRealmName(String newRealmName) {
        this.newRealmName = newRealmName;
        return this;
    }

    public AuthSecurityBuilder setSecurityDomainName(String securityDomain) {
        this.newSecurityDomain = securityDomain;
        return this;
    }

    public AuthSecurityBuilder setAuthFactoryName(String newFactoryName) {
        this.newFactoryName = newFactoryName;
        return this;
    }

    public AuthSecurityBuilder setActiveFactoryName(String activeFactoryName) {
        this.activeFactoryName = activeFactoryName;
        return this;
    }

    public boolean isFactoryAlreadySet() {
        return activeFactoryName != null;
    }

    public void buildRequest(CommandContext ctx) throws Exception {
        // rely on existing resources, no request.
        if (ootbFactory != null || securityDomain != null) {
            return;
        }

        if (order != null) {
            if (activeFactoryName == null) {
                throw new Exception("No SASL factory to re-order");
            }
            ModelNode request = ElytronUtil.reorderSASLFactory(ctx, order, activeFactoryName);
            getSteps().add(request);
            return;
        }

        Realm realm;
        SecurityDomain securityDomain;
        // First build the realm.
        realm = buildRealm(ctx);

        // If the configuration requires some constant roles, add a new constant-mapper.
        if (realm != null) {
            if (realm.getConfig().getRoles() != null) {
                String roleMapper = ElytronUtil.findMatchingConstantRoleMapper(realm.getConfig().getRoles(), ctx);
                if (roleMapper == null) {
                    roleMapper = DefaultResourceNames.buildConstantRoleMapperName(realm, ctx);
                    ModelNode request = ElytronUtil.buildConstantRoleMapper(realm.getConfig().getRoles(), roleMapper, ctx);
                    getSteps().add(request);
                }
                // update the role mapper.
                realm.getConfig().setRoleMapper(roleMapper);
            }
        }
        // If we have an active Factory, the securityDomain is already present.
        if (activeFactoryName == null) {
            // In case no name has been provided. Lookup for a factory that would contain only this exact same mechanism/realm
            // and reuse it.
            if (newFactoryName == null) {
                authFactory = ElytronUtil.findMatchingAuthFactory(mechanism, spec, ctx);
            }
            if (authFactory == null) {
                // Add a new security domain (realm added to the new securityDomain)
                securityDomain = buildSecurityDomain(ctx, realm);
                // Finally the Factory;
                authFactory = buildAuthFactory(ctx, securityDomain);
            } else {
                // We have a factory that contains the realm, need to add again the realm to the security-domain
                // in case its configuration changed.
                addRealm(ctx, authFactory.getSecurityDomain(), realm);
            }
        } else {
            authFactory = ElytronUtil.getAuthFactory(activeFactoryName, spec, ctx);
            if (authFactory == null) {
                throw new Exception("Impossible to create factory");
            }
            // Add the realm to the existing security domain.
            if (realm != null) {
                addRealm(ctx, authFactory.getSecurityDomain(), realm);
            }
        }
        if (authFactory == null) {
            throw new Exception("Impossible to create factory");
        }

        //Add the mechanism to the factory
        addAuthMechanism(ctx, authFactory, mechanism);
    }

    private Realm buildRealm(CommandContext ctx) throws Exception {
        boolean existing = false;
        String name;
        if (mechanism.getConfig() instanceof PropertiesRealmConfiguration) {
            PropertiesRealmConfiguration config = (PropertiesRealmConfiguration) mechanism.getConfig();
            String rName = null;
            // If a name is provided do not re-use existing realm.
            if (newRealmName == null) {
                rName = ElytronUtil.findMatchingUsersPropertiesRealm(ctx, config);
            }
            if (rName == null) {
                if (newRealmName == null) {
                    newRealmName = DefaultResourceNames.buildUserPropertiesDefaultRealmName(ctx, config);
                }
                ModelNode request = ElytronUtil.addUsersPropertiesRealm(ctx, newRealmName, config);
                getSteps().add(request);
                name = newRealmName;
            } else {
                existing = true;
                name = rName;
            }
        } else if (mechanism.getConfig() instanceof KeyStoreConfiguration) {
            KeyStoreConfiguration tsConfig = (KeyStoreConfiguration) mechanism.getConfig();
            String ksRealmName = null;
            // If a name is provided do not re-use existing realm.
            if (newRealmName == null) {
                ksRealmName = ElytronUtil.findKeyStoreRealm(ctx, tsConfig.getTrustStore());
            }
            if (ksRealmName == null) {
                if (newRealmName == null) {
                    ksRealmName = "ks-realm-" + tsConfig.getTrustStore();
                } else {
                    ksRealmName = newRealmName;
                }
                ModelNode request = ElytronUtil.addKeyStoreRealm(ctx, ksRealmName, tsConfig.getTrustStore());
                getSteps().add(request);
            } else {
                existing = true;
            }
            name = ksRealmName;
        } else {
            // File system or existing properties realm.
            existing = true;
            name = mechanism.getConfig().getRealmName();
        }

        if (name == null) {
            // No realm, not fully supported.
            return null;
        }

        String constantMapper = ElytronUtil.findConstantRealmMapper(ctx, name);
        if (constantMapper == null) {
            getSteps().add(ElytronUtil.addConstantRealmMapper(ctx, name));
            constantMapper = name;
        }
        mechanism.getConfig().setRealmMapperName(constantMapper);

        return new Realm(name, constantMapper,
                mechanism.getConfig(), existing);
    }

    private SecurityDomain buildSecurityDomain(CommandContext ctx, Realm realm) throws OperationFormatException, IOException {
        if (newSecurityDomain == null) {
            newSecurityDomain = DefaultResourceNames.buildDefaultSecurityDomainName(realm, ctx);
        }
        ModelNode request = ElytronUtil.addSecurityDomain(ctx, realm, newSecurityDomain);
        getSteps().add(request);
        SecurityDomain domain = new SecurityDomain(newSecurityDomain);
        if (realm != null) {
            domain.addRealm(realm);
        }
        return domain;
    }

    private AuthFactory buildAuthFactory(CommandContext ctx, SecurityDomain securityDomain) throws OperationFormatException, IOException {
        if (newFactoryName == null) {
            newFactoryName = DefaultResourceNames.buildDefaultAuthFactoryName(mechanism, spec, ctx);
        }
        ModelNode request = ElytronUtil.addAuthFactory(ctx, securityDomain, newFactoryName, spec);
        getSteps().add(request);
        AuthFactory factory = new AuthFactory(newFactoryName, securityDomain, spec);
        return factory;
    }

    private void addAuthMechanism(CommandContext ctx, AuthFactory authFactory, AuthMechanism mechanism) throws OperationFormatException {
        ElytronUtil.addAuthMechanism(ctx, authFactory, mechanism, getSteps());
    }

    private void addRealm(CommandContext ctx, SecurityDomain securityDomain, Realm realm) throws OperationFormatException {
        ElytronUtil.addRealm(ctx, securityDomain, realm, getSteps());
    }

    public boolean isEmpty() {
        return !getSteps().isDefined();
    }
}
