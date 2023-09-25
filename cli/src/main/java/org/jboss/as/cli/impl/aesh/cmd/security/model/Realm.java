/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

/**
 * Realm model.
 *
 * @author jdenise@redhat.com
 */
public class Realm {
    private final boolean exists;
    private final MechanismConfiguration config;
    private final String constantMapper;
    private final String resourceName;

    Realm(String resourceName, String constantMapper, MechanismConfiguration config, boolean exists) {
        this.resourceName = resourceName;
        this.constantMapper = constantMapper;
        this.config = config;
        this.exists = exists;
    }

    public String getConstantMapper() {
        return constantMapper;
    }

    public String getResourceName() {
        return resourceName;
    }

    /**
     * @return the exists
     */
    public boolean exists() {
        return exists;
    }

    /**
     * @return the config
     */
    public MechanismConfiguration getConfig() {
        return config;
    }
}
