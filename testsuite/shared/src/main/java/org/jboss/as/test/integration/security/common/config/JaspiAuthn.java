/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.security.common.config;

/**
 * A simple config holder for JASPIC authentication in a security domain.
 *
 * @author Josef Cacek
 */
public class JaspiAuthn {

    private final LoginModuleStack[] loginModuleStacks;
    private final AuthnModule[] authnModules;

    // Constructors ----------------------------------------------------------
    /**
     * Create a new JaspiAuthn.
     *
     * @param builder
     */
    private JaspiAuthn(Builder builder) {
        this.loginModuleStacks = builder.loginModuleStacks;
        this.authnModules = builder.authnModules;
    }

    // Public methods --------------------------------------------------------

    /**
     * Get the loginModuleStacks.
     *
     * @return the loginModuleStacks.
     */
    public LoginModuleStack[] getLoginModuleStacks() {
        return loginModuleStacks;
    }

    /**
     * Get the authnModules.
     *
     * @return the authnModules.
     */
    public AuthnModule[] getAuthnModules() {
        return authnModules;
    }

    // Embedded classes ------------------------------------------------------

    public static class Builder {
        private LoginModuleStack[] loginModuleStacks;
        private AuthnModule[] authnModules;

        public Builder loginModuleStacks(LoginModuleStack... loginModuleStacks) {
            this.loginModuleStacks = loginModuleStacks;
            return this;
        }

        public Builder authnModules(AuthnModule... authnModules) {
            this.authnModules = authnModules;
            return this;
        }

        public JaspiAuthn build() {
            return new JaspiAuthn(this);
        }
    }

}
