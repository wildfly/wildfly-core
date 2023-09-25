/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * An attachment holding the names of services detected in the deployment unit.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ServicesAttachment {

    private final Map<String, List<String>> services;

    ServicesAttachment(final Map<String, List<String>> services) {
        this.services = services;
    }

    /**
     * Get the service implementations for a given type name.
     *
     * @param serviceTypeName the type name
     * @return the possibly empty list of services
     */
    public List<String> getServiceImplementations(String serviceTypeName) {
        final List<String> strings = services.get(serviceTypeName);
        return strings == null ? Collections.<String>emptyList() : Collections.unmodifiableList(strings);
    }
}
