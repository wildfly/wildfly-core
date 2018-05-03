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
