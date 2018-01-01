/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
