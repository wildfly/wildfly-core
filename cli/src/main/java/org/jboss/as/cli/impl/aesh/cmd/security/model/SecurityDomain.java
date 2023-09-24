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
        checkNotNullParamWithNullPointerException("realm", realm);
        realms.add(realm);
    }

    List<Realm> getRealms() {
        return Collections.unmodifiableList(realms);
    }

}
