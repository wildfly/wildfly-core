/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.operation;

/**
 * Exposes the descriptor of a described {@link org.jboss.as.controller.OperationStepHandler}
 * @author Paul Ferraro
 * @param <D> the descriptor type
 */
public interface DescribedOperationStepHandler<D extends OperationStepHandlerDescriptor> {
    /**
     * Returns the descriptor of this {@link org.jboss.as.controller.OperationStepHandler}
     * @return the descriptor of this {@link org.jboss.as.controller.OperationStepHandler}
     */
    D getDescriptor();
}
