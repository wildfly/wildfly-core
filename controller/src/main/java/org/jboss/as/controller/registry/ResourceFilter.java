/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.registry;

import org.jboss.as.controller.PathAddress;

/**
 * A resource filter.
 *
 * @author Emanuel Muckenhuber
 */
@FunctionalInterface
public interface ResourceFilter {

    /**
     * Test whether the resource should be included or not.
     *
     * @param address the resource address
     * @param resource the resource
     * @return {@code true} when to include the resource
     */
    boolean accepts(final PathAddress address, final Resource resource);

}
