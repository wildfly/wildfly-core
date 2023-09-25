/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform;

import org.jboss.dmr.ModelNode;

/**
 * Policy for rejecting operations on the domain controller. This allows the operation transformer to reject
 * successfully applied operation from a slave HC, which is going to trigger a global rollback of the operation
 * in the domain. Ignored resources on a slave HC won't get propagated and therefore can't cause a global rollback.
 *
 * @author Emanuel Muckenhuber
 */
public interface OperationRejectionPolicy {

    /**
     * Reject the operation.
     *
     * @param preparedResult the prepared result
     * @return whether to reject the operation or not
     */
    boolean rejectOperation(ModelNode preparedResult);

    /**
     * Get the optional failure description.
     *
     * @return the failure description
     */
    String getFailureDescription();

}
