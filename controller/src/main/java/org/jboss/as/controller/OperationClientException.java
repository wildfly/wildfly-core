/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.dmr.ModelNode;

/**
 * Marker interface for an Exception indicating a management operation has failed due to a client mistake
 * (e.g. an operation with invalid parameters was invoked.) Should not be used to report server failures.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public interface OperationClientException {

    /**
     * Get the detyped failure description.
     *
     * @return the description. Will not be {@code null}
     */
   ModelNode getFailureDescription();
}
