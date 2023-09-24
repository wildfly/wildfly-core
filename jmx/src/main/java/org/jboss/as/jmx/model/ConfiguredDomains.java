/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx.model;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ConfiguredDomains {
    private final String legacyDomain;
    private final String exprDomain;

    public ConfiguredDomains(String legacyDomain, String exprDomain){
        assert legacyDomain != null || exprDomain != null;
        this.legacyDomain = legacyDomain;
        this.exprDomain = exprDomain;
    }

    String[] getDomains() {
        if (legacyDomain != null && exprDomain != null) {
            return new String[] {legacyDomain, exprDomain};
        } else if (legacyDomain != null) {
            return new String[] {legacyDomain};
        } else if (exprDomain != null) {
            return new String[] {exprDomain};
        } else {
            return new String[] {};
        }

    }

    ObjectName getMirroredObjectName(ObjectName name) {
        String domain = name.getDomain();
        String mirroredDomain = null;
        if (domain.equals(legacyDomain)) {
            mirroredDomain = exprDomain;
        } else if (domain.equals(exprDomain)) {
            mirroredDomain = legacyDomain;
        }
        if (mirroredDomain == null) {
            return null;
        }
        try {
            return new ObjectName(mirroredDomain, name.getKeyPropertyList());
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException(e);
        }
    }

    boolean isLegacyDomain(ObjectName name) {
        return name.getDomain().equals(legacyDomain);
    }

    String getLegacyDomain() {
        return legacyDomain;
    }

    String getExprDomain() {
        return exprDomain;
    }
}
