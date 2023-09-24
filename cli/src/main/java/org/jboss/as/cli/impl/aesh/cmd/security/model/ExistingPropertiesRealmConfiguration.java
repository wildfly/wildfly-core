/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

import org.jboss.as.cli.Util;

/**
 * A configuration for an existing properties-realm
 *
 * @author jdenise@redhat.com
 */
public class ExistingPropertiesRealmConfiguration implements MechanismConfiguration {

    private final String name;
    private final String exposedRealmName;
    private String realmMapper;
    public ExistingPropertiesRealmConfiguration(String name, String exposedRealmName) {
        this.name = name;
        this.exposedRealmName = exposedRealmName;
    }

    @Override
    public String getRealmName() {
        return name;
    }

    @Override
    public String getRoleDecoder() {
        return Util.GROUPS_TO_ROLES;
    }

    @Override
    public String getRoleMapper() {
        return null;
    }

    @Override
    public String getRealmMapper() {
        return realmMapper;
    }

    @Override
    public String getExposedRealmName() {
        return exposedRealmName;
    }

    @Override
    public void setRealmMapperName(String constantMapper) {
        this.realmMapper = constantMapper;
    }

}
