/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.resolvers;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface ModelNodeResolver<T> {

    /**
     * Formats the attribute to the desired type.
     *
     * @param context the operation context
     * @param value   the value to format
     *
     * @return the formatted value
     *
     * @throws OperationFailedException if an error occurs
     */
    T resolveValue(OperationContext context, ModelNode value) throws OperationFailedException;
}
