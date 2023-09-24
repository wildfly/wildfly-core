/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

import java.util.List;

/**
 * A command base class for trust-store based configuration.
 *
 * @author jdenise@redhat.com
 */
abstract class AbstractKeyStoreConfiguration implements MechanismConfiguration {

    private String realmMapper;
    private final List<String> roles;
    private String roleMapper;

    protected AbstractKeyStoreConfiguration(List<String> roles) {
        this.roles = roles;
    }

    @Override
    public void setRoleMapper(String roleMapper) {
        this.roleMapper = roleMapper;
    }

    @Override
    public String getRoleDecoder() {
        return null;
    }

    @Override
    public String getRoleMapper() {
        return roleMapper;
    }

    @Override
    public String getRealmMapper() {
        return realmMapper;
    }

    @Override
    public String getExposedRealmName() {
        return null;
    }

    @Override
    public void setRealmMapperName(String realmMapper) {
        this.realmMapper = realmMapper;
    }

    @Override
    public List<String> getRoles() {
        return roles;
    }

}
