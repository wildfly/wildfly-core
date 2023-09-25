/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain;


/**
 * Exception indicating that the rollback of a domain model update failed
 * because of cancellation. The cancellation would be due to the failure
 * of another rollback update.
 *
 * @author Brian Stansberry
 */
public class RollbackCancelledException extends UpdateFailedException {

    private static final long serialVersionUID = -1706640796845639910L;

    public RollbackCancelledException(String message) {
        super(message);
    }
}
