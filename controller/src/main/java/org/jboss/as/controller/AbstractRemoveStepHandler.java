/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
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

    private static final OperationContext.AttachmentKey<Set<PathAddress>> RECURSION = OperationContext.AttachmentKey.create(Set.class);

    protected AbstractRemoveStepHandler() {
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
        final ModelNode model = Resource.Tools.readModel(resource);

        performRemove(context, operation, model);

        // Check for the continued presense of the resource. If it's still there, do nothing further.
        // This allows performRemove's recursive removal to work by deferring the runtime
        // bits until child resources are removed
        if (!hasResource(context)) {

            recordCapabilitiesAndRequirements(context, operation, resource);

            if (requiresRuntime(context)) {
                context.addStep(new OperationStepHandler() {
                    @Override
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
    }

    protected void performRemove(OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
        final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);

        Set<PathAddress> removed = context.getAttachment(RECURSION);
        if (removed != null && removed.contains(context.getCurrentAddress())) {
            // We have already tried to remove children (see below) and are now back.
            // So avoid any chance of continual looping and just remove the resource
            context.removeResource(PathAddress.EMPTY_ADDRESS);
            return;
        }

        // We're going to add steps to remove any children before we remove the parent
        // First, figure out what child steps we need

        PathAddress address = context.getCurrentAddress();
        final Map<PathAddress, OperationStepHandler> map = new LinkedHashMap<>();
        for (String childType : resource.getChildTypes()) {
            for (final Resource.ResourceEntry entry : resource.getChildren(childType)) {
                PathElement path = entry.getPathElement();
                if (!entry.isRuntime() && removeChildRecursively(path)) {
                    ImmutableManagementResourceRegistration mrr = context.getResourceRegistration().getSubModel(PathAddress.pathAddress(path));
                    if (!mrr.isRuntimeOnly() && !mrr.isAlias()) {
                        OperationStepHandler childHandler = mrr.getOperationHandler(PathAddress.EMPTY_ADDRESS, ModelDescriptionConstants.REMOVE);
                        if (childHandler != null) {
                            PathAddress childAddress = address.append(path);
                            map.put(childAddress, childHandler);
                        }
                    }
                }
            }
        }

        if (map.isEmpty()) {
            // No children we need to remove; just do the normal remove
            context.removeResource(PathAddress.EMPTY_ADDRESS);
        } else {
            // We are going to add steps to the front of the current stage step queue to remove
            // the children, and then to call ourself again (said call will do the runtime part).
            // We put them on the top of the stack so all work associated with this step is done
            // before the next currently registered step executes.
            // Since these are going to the front of the queue we add them in reverse order of
            // intended execution.

            // First, call ourself again
            context.addStep(this, OperationContext.Stage.MODEL, true);

            // Now for each child
            // We add the steps in the opposite order of how the resource provides the children
            // This may not be necessary, but produces a kind of FIFO behavior if the
            // children's order happens to be meaningful. Generally FIFO is a good thing.
            for (Map.Entry<PathAddress, OperationStepHandler> entry : map.entrySet()) {
                PathAddress child = entry.getKey();
                ControllerLogger.MGMT_OP_LOGGER.debugf("Adding remove step for child at %s", child);
                context.addStep(Util.createRemoveOperation(child), entry.getValue(), OperationContext.Stage.MODEL, true);
            }

            // Record that we have been here so when we come back we avoid looping
            if (removed == null) {
                removed = new HashSet<>();
                context.attach(RECURSION, removed);
            }
            removed.add(context.getCurrentAddress());
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
        ImmutableManagementResourceRegistration registration = context.getResourceRegistration();
        for (RuntimeCapability<?> capability : registration.getCapabilities()) {
            if (capability.isDynamicallyNamed()) {
                context.deregisterCapability(capability.getDynamicName(context.getCurrentAddress()));
            } else {
                context.deregisterCapability(capability.getName());
            }
        }
        ModelNode model = resource.getModel();
        for (AttributeAccess attribute : registration.getAttributes(PathAddress.EMPTY_ADDRESS).values()) {
            // Skip runtime attributes and aliases
            if (AttributeAccess.Storage.RUNTIME.test(attribute) || AttributeAccess.Flag.ALIAS.test(attribute)) continue;

            AttributeDefinition definition = attribute.getAttributeDefinition();
            String attributeName = definition.getName();
            if (model.hasDefined(attributeName) || definition.hasCapabilityRequirements()) {
                definition.removeCapabilityRequirements(context, resource, model.get(attributeName));
            }
        }
        assert registration.getRequirements() != null;
        for (CapabilityReferenceRecorder recorder : registration.getRequirements()) {
            recorder.removeCapabilityRequirements(context, resource, null);
        }
    }

    /**
     * Gets whether a child resource should be removed via the addition of a step to invoke
     * the "remove" operation for its address. If this method returns {@code false}
     * then the child resource will simply be discarded along with their parent.
     * <p>
     * <stong>NOTE:</stong> If a subclass returns {@code false} from this method, it must ensure that it itself
     * will make any necessary changes to the capability registry and the service container associated with
     * the child resource. Generally, returning {@code false} would only be appropriate for parent resources whose
     * children only represent configuration details of the parent, and are not independent entities.
     * <p>
     * This default implementation returns {@code true}
     *
     * @param child the path element pointing to the child resource
     *
     * @return {@code true} if separate steps should be added to remove children; {@code false}
     *         if any children can simply be discarded along with the parent
     */
    protected boolean removeChildRecursively(PathElement child) {
        return true;
    }

    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
    }

    protected void recoverServices(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
    }

    protected boolean requiresRuntime(OperationContext context) {
        return context.isDefaultRequiresRuntime();
    }

    private static boolean hasResource(OperationContext context) {
        try {
            context.readResource(PathAddress.EMPTY_ADDRESS, false);
            return true;
        } catch (Resource.NoSuchResourceException nsre) {
            return false;
        }
    }

}
