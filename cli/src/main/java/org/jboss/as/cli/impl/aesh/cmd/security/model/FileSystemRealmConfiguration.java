/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

/**
 * A configuration for an existing file-system realm.
 *
 * @author jdenise@redhat.com
 */
public class FileSystemRealmConfiguration implements MechanismConfiguration {
    private final String realmName;
    private final String roleDecoder;
    private final String exposedRealmName;
    private String realmMapper;
    public FileSystemRealmConfiguration(String exposedRealmName, String realmName, String roleDecoder) {
        this.realmName = realmName;
        this.roleDecoder = roleDecoder;
        this.exposedRealmName = exposedRealmName;
    }

    /**
     * @return the realmName
     */
    @Override
    public String getRealmName() {
        return realmName;
    }

    /**
     * @return the roleDecoder
     */
    @Override
    public String getRoleDecoder() {
        return roleDecoder;
    }

    @Override
    public String getRealmMapper() {
        return realmMapper;
    }

    @Override
    public String getRoleMapper() {
        return null;
    }

    @Override
    public String getExposedRealmName() {
        return exposedRealmName;
    }

    @Override
    public void setRealmMapperName(String realmMapper) {
        this.realmMapper = realmMapper;
    }

}
