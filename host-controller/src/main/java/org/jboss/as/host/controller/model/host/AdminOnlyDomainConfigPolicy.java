/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.model.host;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Policy options for how a slave host controller started in
 * {@link org.jboss.as.controller.RunningMode#ADMIN_ONLY admin-only mode} should
 * deal with the absence of a local copy of the domain-wide configuration.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public enum AdminOnlyDomainConfigPolicy {

    /** Start the HC with no domain wide configuration. */
    ALLOW_NO_CONFIG("allow-no-config"),
    /**
     * This is just an alias for FETCH_FROM_DOMAIN_CONTROLLER used only to parse legacy configuration files.
     */
    LEGACY_FETCH_FROM_DOMAIN_CONTROLLER("fetch-from-master"),
    /**
     * Contact the primary host controller for the current domain wide configuration.
     * The host will not actually register with the primary Host Controller. If the primary
     * Host Controller cannot be reached, start of the host controller will fail.
     */
    FETCH_FROM_DOMAIN_CONTROLLER("fetch-from-domain-controller"),
    /**
     * This absence of a local copy domain wide config is not supported, and start
     * of the host controller will fail.
     */
    REQUIRE_LOCAL_CONFIG("require-local-config");

    public static final AdminOnlyDomainConfigPolicy DEFAULT = ALLOW_NO_CONFIG;

    private final String toString;

    private AdminOnlyDomainConfigPolicy(String toString) {
        this.toString = toString;
    }

    @Override
    public String toString() {
        return toString;
    }

    private static final Map<String, AdminOnlyDomainConfigPolicy> POLICY_MAP = new HashMap<String, AdminOnlyDomainConfigPolicy>();

    static {
        for (AdminOnlyDomainConfigPolicy policy : AdminOnlyDomainConfigPolicy.values()) {
            POLICY_MAP.put(policy.toString().toUpperCase(Locale.ENGLISH), policy);
        }
    }

    public static AdminOnlyDomainConfigPolicy getPolicy(String stringForm) {
        AdminOnlyDomainConfigPolicy result = POLICY_MAP.get(stringForm.toUpperCase(Locale.ENGLISH));
        if (result == null) {
            result = AdminOnlyDomainConfigPolicy.valueOf(stringForm);
        }
        return result;
    }
}
