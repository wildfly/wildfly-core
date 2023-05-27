/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
package org.jboss.as.controller;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Simple remove handler that, if allowed, restarts a parent resource when a child is removed.
 * Otherwise the server is put into a forced reload.
 *
 * @author Jason T. Greene
 */
public abstract class RestartParentResourceRemoveHandler extends RestartParentResourceHandlerBase {

    protected RestartParentResourceRemoveHandler(String parentKeyName) {
        super(parentKeyName);
    }

    @Deprecated(forRemoval = true)
    protected RestartParentResourceRemoveHandler(String parentKeyName, RuntimeCapability ... capabilities) {
        super(parentKeyName);
    }

    /**
     * Performs the update to the persistent configuration model. This default implementation simply removes
     * the targeted resource.
     *
     * @param context the operation context
     * @param operation  the operation
     * @throws OperationFailedException if there is a problem updating the model
     */
    @Override
    protected void updateModel(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        // verify that the resource exist before removing it
        context.readResource(PathAddress.EMPTY_ADDRESS, false);
        Resource resource = context.removeResource(PathAddress.EMPTY_ADDRESS);
        recordCapabilitiesAndRequirements(context, operation, resource);
    }

    protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        for (RuntimeCapability<?> capability : context.getResourceRegistration().getCapabilities()) {
            if (capability.isDynamicallyNamed()) {
                context.deregisterCapability(capability.getDynamicName(context.getCurrentAddress()));
            } else {
                context.deregisterCapability(capability.getName());
            }
        }
        ModelNode model = resource.getModel();
        ImmutableManagementResourceRegistration mrr = context.getResourceRegistration();
        for (String attr : mrr.getAttributeNames(PathAddress.EMPTY_ADDRESS)) {
            AttributeAccess aa = mrr.getAttributeAccess(PathAddress.EMPTY_ADDRESS, attr);
            if (aa != null) {
                AttributeDefinition ad = aa.getAttributeDefinition();
                if (ad != null && (model.hasDefined(ad.getName()) || ad.hasCapabilityRequirements())) {
                    ad.removeCapabilityRequirements(context, resource, model.get(ad.getName()));
                }
            }
        }
    }
}
