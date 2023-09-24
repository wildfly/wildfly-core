/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.security.common.config;

/**
 * Simple property holder for security domain configuration.
 *
 * @see org.jboss.as.test.integration.security.common.AbstractSecurityDomainsServerSetupTask
 * @author Josef Cacek
 */
public class SecurityDomain {

    public static final String DEFAULT_NAME = "test-security-domain";

    private final String name;
    private final String cacheType;
    private final SecurityModule[] loginModules;
    private final SecurityModule[] authorizationModules;
    private final SecurityModule[] mappingModules;
    private final JaspiAuthn jaspiAuthn;
    private final JSSE jsse;

    // Constructors ----------------------------------------------------------

    private SecurityDomain(Builder builder) {
        this.name = builder.name != null ? builder.name : DEFAULT_NAME;
        this.loginModules = builder.loginModules;
        this.authorizationModules = builder.authorizationModules;
        this.mappingModules = builder.mappingModules;
        this.jaspiAuthn = builder.jaspiAuthn;
        this.jsse = builder.jsse;
        this.cacheType = builder.cacheType;
    }

    // Public methods --------------------------------------------------------

    /**
     * Get the name.
     *
     * @return the name.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the cacheType.
     *
     * @return the cacheType.
     */
    public String getCacheType() {
        return cacheType;
    }

    /**
     * Get the loginModules.
     *
     * @return the loginModules.
     */
    public SecurityModule[] getLoginModules() {
        return loginModules;
    }

    /**
     * Get the authorizationModules.
     *
     * @return the authorizationModules.
     */
    public SecurityModule[] getAuthorizationModules() {
        return authorizationModules;
    }

    /**
     * Get the mappingModules.
     *
     * @return the mappingModules.
     */
    public SecurityModule[] getMappingModules() {
        return mappingModules;
    }

    /**
     * Get the jaspiAuthn.
     *
     * @return the jaspiAuthn.
     */
    public JaspiAuthn getJaspiAuthn() {
        return jaspiAuthn;
    }

    /**
     * Get the jsse.
     *
     * @return the jsse.
     */
    public JSSE getJsse() {
        return jsse;
    }

    // Embedded classes ------------------------------------------------------

    public static class Builder {

        private String name;
        private String cacheType;
        private SecurityModule[] loginModules;
        private SecurityModule[] authorizationModules;
        private SecurityModule[] mappingModules;
        private JaspiAuthn jaspiAuthn;
        private JSSE jsse;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder cacheType(String cacheType) {
            this.cacheType = cacheType;
            return this;
        }

        public Builder loginModules(SecurityModule... loginModules) {
            this.loginModules = loginModules;
            return this;
        }

        public Builder authorizationModules(SecurityModule... authorizationModules) {
            this.authorizationModules = authorizationModules;
            return this;
        }

        public Builder mappingModules(SecurityModule... mappingModules) {
            this.mappingModules = mappingModules;
            return this;
        }

        public Builder jaspiAuthn(JaspiAuthn jaspiAuthn) {
            this.jaspiAuthn = jaspiAuthn;
            return this;
        }

        public Builder jsse(JSSE jsse) {
            this.jsse = jsse;
            return this;
        }

        public SecurityDomain build() {
            return new SecurityDomain(this);
        }
    }

}
