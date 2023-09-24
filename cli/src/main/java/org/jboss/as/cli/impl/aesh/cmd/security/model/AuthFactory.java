/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

import static org.wildfly.common.Assert.checkNotNullParamWithNullPointerException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


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
        this.name = checkNotNullParamWithNullPointerException("name", name);
        this.domain = checkNotNullParamWithNullPointerException("domain", domain);
        this.spec = checkNotNullParamWithNullPointerException("spec", spec);
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
        checkNotNullParamWithNullPointerException("mec", mec);
        mechanisms.add(mec);
    }

    public List<AuthMechanism> getMechanisms() {
        return Collections.unmodifiableList(mechanisms);
    }
}
