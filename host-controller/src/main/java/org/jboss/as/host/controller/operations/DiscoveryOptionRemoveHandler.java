/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;

/**
 * Handler for a discovery resource's remove operation.
 *
 * @author Farah Juma
 */
public class DiscoveryOptionRemoveHandler extends AbstractDiscoveryOptionRemoveHandler {

    /**
     * Create the DiscoveryOptionRemoveHandler.
     */
    public DiscoveryOptionRemoveHandler() {
    }

    @Override
    protected void performRemove(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        super.performRemove(context, operation, model);
        updateOptionsAttribute(context, operation, ModelDescriptionConstants.CUSTOM_DISCOVERY);
    }
}
