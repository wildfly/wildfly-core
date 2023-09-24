/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;


/**
 * Policy defining whether resource or operation transformations should be rejected.
 *
 * @author Emanuel Muckenhuber
 */
public enum DiscardPolicy {
        /**
         * Don't discard the resource or operation.
         */
        NEVER,
        /**
         * Reject operations and only warn for resource transformations.
         */
        REJECT_AND_WARN,
        /**
         * Discard operations silently, but warn for resource transformations.
         */
        DISCARD_AND_WARN,
        /**
         * Discard silently.
         */
        SILENT;

}
