/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.executor;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * Encapsulates the execution of a runtime metric.
 * @author Paul Ferraro
 * @param <C> the metric execution context.
 */
public interface MetricExecutor<C> {
    /**
     * Executes the specified executable against the specified operation context.
     * @param context an operation context
     * @param metric the target metric
     * @return the result of the execution (possibly null).
     * @throws OperationFailedException if execution fails
     */
    ModelNode execute(OperationContext context, Metric<C> metric) throws OperationFailedException;
}
