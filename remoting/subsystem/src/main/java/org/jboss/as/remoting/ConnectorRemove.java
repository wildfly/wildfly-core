/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * Removes a connector from the remoting container.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Emanuel Muckenhuber
 */
public class ConnectorRemove extends AbstractRemoveStepHandler {

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) {
        final String name = context.getCurrentAddressValue();
        RemotingServices.removeConnectorServices(context, name);
    }

    @Override
    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        ConnectorAdd.launchServices(context, model);
    }
}
