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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
    private final Set<RuntimeCapability> capabilities;


    protected RestartParentResourceRemoveHandler(String parentKeyName) {
        super(parentKeyName);
        capabilities = NULL_CAPABILITIES;
    }

    protected RestartParentResourceRemoveHandler(String parentKeyName, RuntimeCapability... capabilities) {
        this(parentKeyName, capabilities.length == 0 ? AbstractAddStepHandler.NULL_CAPABILITIES : new HashSet<RuntimeCapability>(Arrays.asList(capabilities)));
    }

    protected RestartParentResourceRemoveHandler(String parentKeyName, Set<RuntimeCapability> capabilities) {
        super(parentKeyName);
        this.capabilities = capabilities == null ? AbstractAddStepHandler.NULL_CAPABILITIES : capabilities;
    }

    /**
     * Performs the update to the persistent configuration model. This default implementation simply removes
     * the targeted resource.
     *
     * @param context the operation context
     * @param operation  the operation
     * @throws OperationFailedException if there is a problem updating the model
     */
    protected void updateModel(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        // verify that the resource exist before removing it
        context.readResource(PathAddress.EMPTY_ADDRESS, false);

        Resource resource = context.removeResource(PathAddress.EMPTY_ADDRESS);
        recordCapabilitiesAndRequirements(context, operation, resource);
    }


    /**
     * Record any new {@link org.jboss.as.controller.capability.RuntimeCapability capabilities} that are no longer available as
     * a result of this operation, as well as any requirements for other capabilities that no longer exist. This method is
     * invoked during {@link org.jboss.as.controller.OperationContext.Stage#MODEL}.
     * <p>
     * Any changes made by this method will automatically be discarded if the operation rolls back.
     * </p>
     * <p>
     * This default implementation deregisters any capabilities passed to the constructor.
     * </p>
     *
     * @param context   the context. Will not be {@code null}
     * @param operation the operation that is executing Will not be {@code null}
     * @param resource  the resource that will be removed. Will <strong>not</strong> reflect any updates made by
     *                  {@link #performRemove(OperationContext, org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode)} as this method
     *                  is invoked before that method is. Will not be {@code null}
     */
    protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        Set<RuntimeCapability> capabilitySet = capabilities.isEmpty() ? context.getResourceRegistration().getCapabilities() : capabilities;

        for (RuntimeCapability capability : capabilitySet) {
            if (capability.isDynamicallyNamed()) {
                context.deregisterCapability(capability.getDynamicName(context.getCurrentAddressValue()));
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
                    ad.removeCapabilityRequirements(context, model.get(ad.getName()));
                }
            }
        }
    }

}
