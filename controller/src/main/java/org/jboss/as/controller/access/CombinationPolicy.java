/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access;

/**
 * Policy for combining multiple permissions.
 */
public enum CombinationPolicy {

    /** If multiple permissions for the same action exist, if any of them allow the action
     * the action should be allowed. */
    PERMISSIVE("permissive"),
//    /** If multiple permissions with the same action exist, the action should be allowed only if all of them
//     * allow the action. */
//    RESTRICTIVE,
    /** If multiple permissions for the same action exist an exception should be thrown and the action should not
     * be allowed. */
    REJECTING("rejecting");

    private final String toString;

    private CombinationPolicy(String toString) {
        this.toString = toString;
    }

    @Override
    public String toString() {
        return toString;
    }
}
