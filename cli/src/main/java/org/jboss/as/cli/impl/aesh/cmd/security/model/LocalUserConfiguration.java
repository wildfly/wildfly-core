/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

import org.jboss.as.cli.Util;

/**
 * A configuration for local mechanism.
 *
 * @author jdenise@redhat.com
 */
public class LocalUserConfiguration implements MechanismConfiguration {

    private final boolean superUser;
    private String realmMapper;

    public LocalUserConfiguration(boolean superUser) {
        this.superUser = superUser;
    }
    @Override
    public String getRealmName() {
        return Util.LOCAL;
    }

    @Override
    public String getRoleDecoder() {
        return null;
    }

    @Override
    public String getRoleMapper() {
        return superUser ? Util.SUPER_USER_MAPPER : null;
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

}
