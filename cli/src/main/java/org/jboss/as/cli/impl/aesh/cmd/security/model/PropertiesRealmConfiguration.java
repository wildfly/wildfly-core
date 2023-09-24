/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

import java.io.IOException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.cmd.RelativeFile;

/**
 * A configuration for a new properties realm.
 *
 * @author jdenise@redhat.com
 */
public class PropertiesRealmConfiguration implements MechanismConfiguration {
    private String realmName;
    private final String relativeTo;
    private final String userPropertiesFile;
    private final String groupPropertiesFile;
    private final String exposedRealmName;
    private final boolean plainText;
    private String realmMapper;

    public PropertiesRealmConfiguration(String exposedRealmName, RelativeFile userPropertiesFile, RelativeFile groupPropertiesFile, String relativeTo, boolean plainText) throws IOException {
        this.exposedRealmName = exposedRealmName;
        this.userPropertiesFile = relativeTo != null ? userPropertiesFile.getOriginalPath() : userPropertiesFile.getCanonicalPath();
        this.groupPropertiesFile = groupPropertiesFile == null ? null : relativeTo != null ? groupPropertiesFile.getOriginalPath() : groupPropertiesFile.getCanonicalPath();
        this.relativeTo = relativeTo;
        this.plainText = plainText;
    }

    public PropertiesRealmConfiguration(String name) throws IOException {
        this(null, null, null, null, false);
    }


    /**
     * @return the realmName
     */
    @Override
    public String getExposedRealmName() {
        return exposedRealmName;
    }

    /**
     * @return the realmName
     */
    @Override
    public String getRealmName() {
        return realmName;
    }

    /**
     * @return the relativeTo
     */
    public String getRelativeTo() {
        return relativeTo;
    }

    /**
     * @return the plainText
     */
    public boolean getPlainText() {
        return plainText;
    }

    /**
     * @return the userPropertiesFile
     * @throws java.io.IOException
     */
    public String getUserPropertiesFile() throws IOException {
        return userPropertiesFile;
    }

    /**
     * @return the groupPropertiesFile
     * @throws java.io.IOException
     */
    public String getGroupPropertiesFile() throws IOException {
        return groupPropertiesFile;
    }

    @Override
    public String getRoleDecoder() {
        return Util.GROUPS_TO_ROLES;
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
    public void setRealmMapperName(String realmMapper) {
        this.realmMapper = realmMapper;
    }
}
