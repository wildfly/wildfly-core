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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Security Domain model.
 *
 * @author jdenise@redhat.com
 */
public class SecurityDomain {
    private final String name;
    private final List<Realm> realms = new ArrayList<>();
    public SecurityDomain(String name) {
        this.name = name;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    void addRealm(Realm realm) {
        Objects.requireNonNull(realm);
        realms.add(realm);
    }

    List<Realm> getRealms() {
        return Collections.unmodifiableList(realms);
    }

}
