/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.dmr.ModelNode;

/**
 * {@link OperationStepHandler} that reads the address of the operation and returns the value of its
 * last element as the operation result. Intended for use as a {@code read-attribute} handler for resources
 * that have "name" attribute.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class ReadResourceNameOperationStepHandler implements OperationStepHandler {

    public static final ReadResourceNameOperationStepHandler INSTANCE = new ReadResourceNameOperationStepHandler();

    private ReadResourceNameOperationStepHandler() {

    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        // Check if the resource exists before return the name
        context.readResource(PathAddress.EMPTY_ADDRESS, false);
        final String name = context.getCurrentAddressValue();

        context.getResult().set(name);
    }
}
