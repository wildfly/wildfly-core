/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

/**
 *
 * @author jdenise@redhat.com
 */
public class ApplicationSecurityDomain {
    private final String name;
    private final String factory;
    private final String secDomain;

    ApplicationSecurityDomain(String name, String factory, String secDomain) {
        this.name = name;
        this.factory = factory;
        this.secDomain = secDomain;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the factory
     */
    public String getFactory() {
        return factory;
    }

    /**
     * @return the secDomain
     */
    public String getSecurityDomain() {
        return secDomain;
    }

}
