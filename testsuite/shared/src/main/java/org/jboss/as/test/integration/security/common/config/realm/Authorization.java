/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.security.common.config.realm;

/**
 * A helper class to provide settings for SecurityRealm's authorization.
 *
 * @author Josef Cacek
 */
public class Authorization {

    private final String path;
    private final String relativeTo;

    // Constructors ----------------------------------------------------------
    private Authorization(Builder builder) {
        this.path = builder.path;
        this.relativeTo = builder.relativeTo;
    }

    public String getPath() {
        return path;
    }

    public String getRelativeTo() {
        return relativeTo;
    }

    @Override
    public String toString() {
        return "Authorization [path=" + path + ", relative-to=" + relativeTo + "]";
    }

    public static class Builder {

        private String path;
        private String relativeTo;

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder relativeTo(String relativeTo) {
            this.relativeTo = relativeTo;
            return this;
        }

        public Authorization build() {
            return new Authorization(this);
        }
    }
}
