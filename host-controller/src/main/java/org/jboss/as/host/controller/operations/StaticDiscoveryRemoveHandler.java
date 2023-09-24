/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STATIC_DISCOVERY;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * Handler for a static discovery resource's remove operation.
 *
 * @author Farah Juma
 */
public class StaticDiscoveryRemoveHandler extends AbstractDiscoveryOptionRemoveHandler {

    /**
     * Create the StaticDiscoveryRemoveHandler.
     */
    public StaticDiscoveryRemoveHandler() {
    }

    @Override
    protected void performRemove(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        super.performRemove(context, operation, model);
        updateOptionsAttribute(context, operation, STATIC_DISCOVERY);
    }
}
