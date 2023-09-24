/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

/**
 * An empty mechanism configuration
 *
 * @author jdenise@redhat.com
 */
public class EmptyConfiguration implements MechanismConfiguration {

    @Override
    public String getRealmName() {
        return null;
    }

    @Override
    public String getRoleDecoder() {
        return null;
    }

    @Override
    public String getRoleMapper() {
        return null;
    }

    @Override
    public String getRealmMapper() {
        return null;
    }

    @Override
    public String getExposedRealmName() {
        return null;
    }

    @Override
    public void setRealmMapperName(String constantMapper) {
    }

}
