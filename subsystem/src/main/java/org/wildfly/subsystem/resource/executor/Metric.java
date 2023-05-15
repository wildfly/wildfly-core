/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.executor;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.wildfly.subsystem.resource.AttributeDefinitionProvider;

/**
 * Interface to be implemented by metric enumerations.
 * @author Paul Ferraro
 * @param <C> metric context
 */
public interface Metric<C> extends AttributeDefinitionProvider {
    /**
     * Execute against the specified context.
     * @param context an execution context
     * @return the execution result (possibly null).
     */
    ModelNode execute(C context) throws OperationFailedException;
}
