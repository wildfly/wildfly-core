/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;


/**
 * Indicates whether this host is the domain master.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class IsMasterHandler implements OperationStepHandler {

    public static final IsMasterHandler INSTANCE = new IsMasterHandler();

    private IsMasterHandler() {
        // singleton
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final ModelNode subModel = context.readResource(PathAddress.EMPTY_ADDRESS, false).getModel();
        boolean master = subModel.get(ModelDescriptionConstants.DOMAIN_CONTROLLER).hasDefined(ModelDescriptionConstants.LOCAL);
        context.getResult().set(master);
    }
}
