/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.capability;

import java.util.function.Function;

import org.jboss.as.controller.PathAddress;

/**
 * Dynamic name mapper implementations for ternary capability names.
 * @author Paul Ferraro
 */
public enum TernaryCapabilityNameResolver implements Function<PathAddress, String[]> {
    GRANDPARENT_PARENT_CHILD() {
        @Override
        public String[] apply(PathAddress address) {
            PathAddress parent = address.getParent();
            return new String[] { parent.getParent().getLastElement().getValue(), parent.getLastElement().getValue(), address.getLastElement().getValue() };
        }
    },
    GREATGRANDPARENT_GRANDPARENT_PARENT() {
        @Override
        public String[] apply(PathAddress address) {
            PathAddress parent = address.getParent();
            PathAddress grandparent = parent.getParent();
            return new String[] { grandparent.getParent().getLastElement().getValue(), grandparent.getLastElement().getValue(), parent.getLastElement().getValue() };
        }
    },
    GREATGRANDPARENT_GRANDPARENT_CHILD() {
        @Override
        public String[] apply(PathAddress address) {
            PathAddress grandparent = address.getParent().getParent();
            return new String[] { grandparent.getParent().getLastElement().getValue(), grandparent.getLastElement().getValue(), address.getLastElement().getValue() };
        }
    },
    GREATGRANDPARENT_PARENT_CHILD() {
        @Override
        public String[] apply(PathAddress address) {
            PathAddress parent = address.getParent();
            return new String[] { parent.getParent().getParent().getLastElement().getValue(), parent.getLastElement().getValue(), address.getLastElement().getValue() };
        }
    },
    ;
}
