/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.security.common.elytron;

import static org.wildfly.common.Assert.checkNotNullParamWithNullPointerException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract parent for {@link ConfigurableElement} implementations which are able to configure (and provide) users and roles.
 * It extends {@link AbstractConfigurableElement} and holds user list to be created.
 *
 * @author Josef Cacek
 */
public abstract class AbstractUserRolesCapableElement extends AbstractConfigurableElement implements UsersRolesCapableElement {

    private final List<UserWithRoles> usersWithRoles;

    protected AbstractUserRolesCapableElement(Builder<?> builder) {
        super(builder);
        this.usersWithRoles = Collections.unmodifiableList(new ArrayList<>(builder.usersWithRoles));
    }

    @Override
    public List<UserWithRoles> getUsersWithRoles() {
        return usersWithRoles;
    }

    /**
     * Builder to build {@link AbstractUserRolesCapableElement}.
     */
    public abstract static class Builder<T extends Builder<T>> extends AbstractConfigurableElement.Builder<T> {
        private List<UserWithRoles> usersWithRoles = new ArrayList<>();

        protected Builder() {
        }

        /**
         * Adds the given user to list of users in the domain.
         *
         * @param userWithRoles not-null {@link UserWithRoles} instance
         */
        public final T withUser(UserWithRoles userWithRoles) {
            this.usersWithRoles.add(checkNotNullParamWithNullPointerException("userWithRoles", userWithRoles));
            return self();
        }

        /**
         * Shortcut method for {@link #withUser(UserWithRoles)} one.
         *
         * @param username must not be null
         * @param password must not be null
         * @param roles roles to be assigned to user (may be null)
         */
        public final T withUser(String username, String password, String... roles) {
            this.usersWithRoles.add(UserWithRoles.builder().withName(username).withPassword(password).withRoles(roles).build());
            return self();
        }
    }

}
