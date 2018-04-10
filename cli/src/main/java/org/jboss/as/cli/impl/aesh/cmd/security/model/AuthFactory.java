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
 * Models an authentication factory.
 *
 * @author jdenise@redhat.com
 */
public class AuthFactory {

    private final String name;
    private final SecurityDomain domain;
    private final List<AuthMechanism> mechanisms = new ArrayList<>();
    private final AuthFactorySpec spec;

    public AuthFactory(String name, SecurityDomain domain, AuthFactorySpec spec) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(domain);
        Objects.requireNonNull(spec);
        this.name = name;
        this.domain = domain;
        this.spec = spec;
    }

    public AuthFactorySpec getSpec() {
        return spec;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the domain
     */
    public SecurityDomain getSecurityDomain() {
        return domain;
    }

    public void addMechanism(AuthMechanism mec) {
        Objects.requireNonNull(mec);
        mechanisms.add(mec);
    }

    public List<AuthMechanism> getMechanisms() {
        return Collections.unmodifiableList(mechanisms);
    }
}
