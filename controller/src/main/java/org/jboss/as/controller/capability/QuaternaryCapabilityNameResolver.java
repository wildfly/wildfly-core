/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.capability;

import java.util.function.Function;

import org.jboss.as.controller.PathAddress;

/**
 * Dynamic name mapper implementations for quaternary capability names.
 * @author Paul Ferraro
 */
public enum QuaternaryCapabilityNameResolver implements Function<PathAddress, String[]> {
    GREATGRANDPARENT_GRANDPARENT_PARENT_CHILD() {
        @Override
        public String[] apply(PathAddress address) {
            PathAddress parent = address.getParent();
            PathAddress grandparent = parent.getParent();
            return new String[] { grandparent.getParent().getLastElement().getValue(), grandparent.getLastElement().getValue(), parent.getLastElement().getValue(), address.getLastElement().getValue() };
        }
    },
    ;
}
