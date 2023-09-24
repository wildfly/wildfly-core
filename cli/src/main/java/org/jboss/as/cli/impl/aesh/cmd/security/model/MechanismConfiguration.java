/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

import java.util.List;

/**
 * The set of attributes exposed by a mechanism configuration.
 *
 * @author jdenise@redhat.com
 */
public interface MechanismConfiguration {

    String getRealmName();

    String getRoleDecoder();

    String getRoleMapper();

    default List<String> getRoles() {
        return null;
    }

    default void setRoleMapper(String roleMapper) {
    }

    String getRealmMapper();

    String getExposedRealmName();

    void setRealmMapperName(String constantMapper);
}
