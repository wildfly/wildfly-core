/*
Copyright 2017 Red Hat, Inc.

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
 * KeyStore model class.
 *
 * @author jdenise@redhat.com
 */
public class KeyStore {
    private final String name;
    private final String password;
    private final boolean exists;
    private final String alias;

    public KeyStore(String name, String password, String alias, boolean exists) {
        this.name = name;
        this.password = password;
        this.exists = exists;
        this.alias = alias;
    }

    public KeyStore(String name, String password, boolean exists) {
        this(name, password, null, exists);
    }

    public boolean exists() {
        return exists;
    }

    public String getName() {
        return name;
    }

    public String getAlias() {
        return alias;
    }

    public String getPassword() {
        return password;
    }
}
