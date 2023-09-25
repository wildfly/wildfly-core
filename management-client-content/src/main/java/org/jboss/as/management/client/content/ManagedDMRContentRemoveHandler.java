/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.management.client.content;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;

/**
 * Remove handler for a resource that represents a named bit of re-usable DMR.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ManagedDMRContentRemoveHandler extends AbstractRemoveStepHandler {

    public static final ManagedDMRContentRemoveHandler INSTANCE = new ManagedDMRContentRemoveHandler();

    private ManagedDMRContentRemoveHandler() {
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return false;
    }
}
