/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Ensures that a placeholder resource for the deprecated configuration=endpoint resource
 * exists, unless this is a domain profile and the user configured the legacy 'worker' attributes.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
class WorkerThreadPoolVsEndpointHandler implements OperationStepHandler {

    private final boolean forDomain;

    WorkerThreadPoolVsEndpointHandler(boolean forDomain) {
        this.forDomain = forDomain;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
        ModelNode model = resource.getModel();
        boolean hasLegacy = false;
        if (forDomain) {
            for (final AttributeDefinition attribute : RemotingSubsystemRootResource.LEGACY_ATTRIBUTES) {
                if (model.hasDefined(attribute.getName())) {
                    hasLegacy = true;
                    break;
                }
            }
        }

        if (!hasLegacy && !resource.hasChild(RemotingEndpointResource.ENDPOINT_PATH)) {
            // User didn't configure either worker-thread-pool or endpoint. Add a default endpoint resource so
            // users can read the default config attribute values
            context.addResource(PathAddress.pathAddress(RemotingEndpointResource.ENDPOINT_PATH), Resource.Factory.create());
        }
    }
}
