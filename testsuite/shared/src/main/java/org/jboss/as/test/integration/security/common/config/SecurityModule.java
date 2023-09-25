/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.security.common.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A helper class to provide setting for the {@link SecurityDomain}.
 *
 * @author Josef Cacek
 */
public class SecurityModule {

    private final String name;
    private final String flag;
    private final Map<String, String> options;

    // Constructors ----------------------------------------------------------

    /**
     * Create a new SecurityModule.
     *
     * @param builder
     */
    private SecurityModule(Builder builder) {
        this.name = builder.name;
        this.flag = builder.flag;
        this.options = builder.options == null ? null : Collections
                .unmodifiableMap(new HashMap<String, String>(builder.options));
    }

    // Public methods --------------------------------------------------------

    /**
     * Get the name.
     *
     * @return the name.
     */
    public final String getName() {
        return name;
    }

    /**
     * Get the flag.
     *
     * @return the flag.
     */
    public final String getFlag() {
        return flag;
    }

    /**
     * Get the options.
     *
     * @return the options.
     */
    public final Map<String, String> getOptions() {
        return options;
    }

    // Embedded classes ------------------------------------------------------

    public static class Builder {
        private String name;
        private String flag;
        private Map<String, String> options;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder flag(String flag) {
            this.flag = flag;
            return this;
        }

        public Builder options(Map<String, String> options) {
            this.options = options;
            return this;
        }

        public Builder putOption(String name, String value) {
            if (options == null) {
                options = new HashMap<String, String>();
            }
            options.put(name, value);
            return this;
        }

        public SecurityModule build() {
            return new SecurityModule(this);
        }
    }

}
