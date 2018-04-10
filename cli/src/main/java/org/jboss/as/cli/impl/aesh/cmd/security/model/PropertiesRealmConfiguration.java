/*
Copyright 2018 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
    private String realmMapper;

    public PropertiesRealmConfiguration(String exposedRealmName, RelativeFile userPropertiesFile, RelativeFile groupPropertiesFile, String relativeTo) throws IOException {
        this.exposedRealmName = exposedRealmName;
        this.userPropertiesFile = relativeTo != null ? userPropertiesFile.getOriginalPath() : userPropertiesFile.getCanonicalPath();
        this.groupPropertiesFile = groupPropertiesFile == null ? null : relativeTo != null ? groupPropertiesFile.getOriginalPath() : groupPropertiesFile.getCanonicalPath();
        this.relativeTo = relativeTo;
    }

    public PropertiesRealmConfiguration(String name) throws IOException {
        this(null, null, null, null);
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
