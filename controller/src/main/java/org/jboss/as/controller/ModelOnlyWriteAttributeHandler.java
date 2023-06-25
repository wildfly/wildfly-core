/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.Collection;

import org.jboss.dmr.ModelNode;

/**
 * A {@code write-attribute} handler that simply validates the attribute value and stores it in the model.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class ModelOnlyWriteAttributeHandler extends AbstractWriteAttributeHandler<Void> {

    public ModelOnlyWriteAttributeHandler(AttributeDefinition... attributeDefinitions) {
        super(attributeDefinitions);
    }

    public ModelOnlyWriteAttributeHandler(Collection<AttributeDefinition> attributeDefinitions) {
        super(attributeDefinitions);
    }

    @Override
    protected final boolean requiresRuntime(OperationContext context) {
        return false;
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> handbackHolder) throws OperationFailedException {
        // should not be called as requiresRuntime returns false
        throw new IllegalStateException();
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Void handback) throws OperationFailedException {
        // should not be called as requiresRuntime returns false
        throw new IllegalStateException();
    }
}
