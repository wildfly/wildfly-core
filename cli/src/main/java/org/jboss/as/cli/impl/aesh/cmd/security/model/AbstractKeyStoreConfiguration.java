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
