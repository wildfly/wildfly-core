/*
 * Copyright 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.test.security.common.elytron;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Object which holds user configuration (password, roles).
 *
 * @author Josef Cacek
 */
public class UserWithRoles {

    private final String name;
    private final String password;
    private final Set<String> roles;

    private UserWithRoles(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "Username must be not-null");
        this.password = builder.password != null ? builder.password : builder.name;
        this.roles = new HashSet<>(builder.roles);
    }

    /**
     * Returns username.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns password as plain text.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Set of roles to be assigned to the user.
     */
    public Set<String> getRoles() {
        return roles;
    }

    /**
     * Creates builder to build {@link UserWithRoles}.
     *
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder to build {@link UserWithRoles}.
     */
    public static final class Builder {
        private String name;
        private String password;
        private final Set<String> roles = new HashSet<>();

        private Builder() {
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withPassword(String password) {
            this.password = password;
            return this;
        }

        /**
         * Add given roles to the builder. It doesn't replace existing roles, but it adds given roles to them.
         */
        public Builder withRoles(Set<String> roles) {
            if (roles != null) {
                this.roles.addAll(roles);
            }
            return this;
        }

        /**
         * Add given roles to the builder. It doesn't replace existing roles, but it adds given roles to them.
         */
        public Builder withRoles(String... roles) {
            if (roles != null) {
                this.roles.addAll(Arrays.asList(roles));
            }
            return this;
        }

        /**
         * Clears set of already added roles.
         */
        public Builder clearRoles() {
            this.roles.clear();
            return this;
        }

        /**
         * Builds UserWithRoles instance.
         *
         * @return
         */
        public UserWithRoles build() {
            return new UserWithRoles(this);
        }
    }

}
