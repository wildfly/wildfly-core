/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

/**
 * An authentication mechanism eg: PLAIN, DIGEST-MD5, ...
 *
 * @author jdenise@redhat.com
 */
public class AuthMechanism {
    private final String type;
    private final MechanismConfiguration config;

    public AuthMechanism(String type, MechanismConfiguration config) {
        this.type = type;
        this.config = config;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * @return the config
     */
    public MechanismConfiguration getConfig() {
        return config;
    }
}
