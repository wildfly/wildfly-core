/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.security.common.config;

/**
 * A simple config holder for loginModuleStack configuration of JASPIC authentication in a security domain.
 *
 * @author Josef Cacek
 */
public class LoginModuleStack {

    private final String name;
    private final SecurityModule[] loginModules;

    // Constructors ----------------------------------------------------------

    private LoginModuleStack(Builder builder) {
        this.name = builder.name;
        this.loginModules = builder.loginModules;
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
     * Get the loginModules.
     *
     * @return the loginModules.
     */
    public SecurityModule[] getLoginModules() {
        return loginModules;
    }

    // Embedded classes ------------------------------------------------------

    public static class Builder {
        private String name;
        private SecurityModule[] loginModules;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder loginModules(SecurityModule... loginModules) {
            this.loginModules = loginModules;
            return this;
        }

        public LoginModuleStack build() {
            return new LoginModuleStack(this);
        }
    }

}
