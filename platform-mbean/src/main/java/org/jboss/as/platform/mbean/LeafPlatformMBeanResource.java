/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.util.Collections;
import java.util.Set;

import org.jboss.as.controller.PathElement;

/**
 * Resource impl for leaf platform mbean resources.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class LeafPlatformMBeanResource extends AbstractPlatformMBeanResource {

    public LeafPlatformMBeanResource(PathElement pathElement) {
        super(pathElement);
    }

    @Override
    ResourceEntry getChildEntry(String name) {
        return null;
    }

    @Override
    Set<String> getChildrenNames() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getChildTypes() {
        return Collections.emptySet();
    }
}
