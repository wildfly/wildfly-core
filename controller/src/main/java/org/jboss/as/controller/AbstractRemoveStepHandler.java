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

import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Base class for handlers that remove resources.
 *
 * @author John Bailey
 */
public abstract class AbstractRemoveStepHandler implements OperationStepHandler {

    private final Set<RuntimeCapability> capabilities;

    protected AbstractRemoveStepHandler() {
        this(AbstractAddStepHandler.NULL_CAPABILITIES);
    }

    protected AbstractRemoveStepHandler(RuntimeCapability... capabilities) {
        this(capabilities.length == 0 ? AbstractAddStepHandler.NULL_CAPABILITIES : new HashSet<RuntimeCapability>(Arrays.asList(capabilities)));
    }

    protected AbstractRemoveStepHandler(Set<RuntimeCapability> capabilities) {
        this.capabilities = capabilities == null ? AbstractAddStepHandler.NULL_CAPABILITIES : capabilities;
    }

    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
        final ModelNode model = Resource.Tools.readModel(resource);

        recordCapabilitiesAndRequirements(context, operation, resource);

        performRemove(context, operation, model);

        if (requiresRuntime(context)) {
            context.addStep(new OperationStepHandler() {
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    performRuntime(context, operation, model);

                    context.completeStep(new OperationContext.RollbackHandler() {
                        @Override
                        public void handleRollback(OperationContext context, ModelNode operation) {
                            try {
                                recoverServices(context, operation, model);
                            } catch (Exception e) {
                                MGMT_OP_LOGGER.errorRevertingOperation(e, getClass().getSimpleName(),
                                    operation.require(ModelDescriptionConstants.OP).asString(),
                                    context.getCurrentAddress());
                            }
                        }
                    });
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

    protected void performRemove(OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
        final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
        if (!requireNoChildResources() || resource.getChildTypes().isEmpty()) {
            context.removeResource(PathAddress.EMPTY_ADDRESS);
        } else {
            List<PathElement> children = getChildren(resource);
            throw ControllerLogger.ROOT_LOGGER.cannotRemoveResourceWithChildren(children);
        }
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
     * @param context the context. Will not be {@code null}
     * @param operation the operation that is executing Will not be {@code null}
     * @param resource the resource that will be removed. Will <strong>not</strong> reflect any updates made by
     * {@link #performRemove(OperationContext, org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode)} as this method
     *                 is invoked before that method is. Will not be {@code null}
     */
    protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        for (RuntimeCapability capability : capabilities) {
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
                if (ad != null && model.hasDefined(ad.getName())) {
                    ad.removeCapabilityRequirements(context, model.get(ad.getName()));
                }
            }
        }
    }

    protected boolean requireNoChildResources() {
        return false;
    }

    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
    }

    protected void recoverServices(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
    }

    protected boolean requiresRuntime(OperationContext context) {
        return context.isDefaultRequiresRuntime();
    }

    private List<PathElement> getChildren(Resource resource) {
        final List<PathElement> pes = new ArrayList<PathElement>();
        for (String childType : resource.getChildTypes()) {
            for (Resource.ResourceEntry entry : resource.getChildren(childType)) {
                pes.add(entry.getPathElement());
            }
        }
        return pes;
    }


}
