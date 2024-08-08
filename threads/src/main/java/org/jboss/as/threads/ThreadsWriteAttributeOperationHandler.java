/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import static org.jboss.as.threads.CommonAttributes.KEEPALIVE_TIME;
import static org.jboss.as.threads.CommonAttributes.TIME;
import static org.jboss.as.threads.CommonAttributes.UNIT;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;

/**
 * Abstract superclass for write-attribute operation handlers for the threads subsystem.
 *
 * @author Brian Stansberry
 * @author Alexey Loubyansky
 */
public abstract class ThreadsWriteAttributeOperationHandler extends AbstractWriteAttributeHandler<Boolean> {

    private final AttributeDefinition[] attributes;

    /**
     * Creates a handler that doesn't validate values.
     * @param attributes all persistent attributes of the
     * @param runtimeAttributes attributes whose updated value can immediately be applied to the runtime
     */
    public ThreadsWriteAttributeOperationHandler(AttributeDefinition[] attributes) {
        this.attributes = attributes;
    }

    @Override
    protected boolean applyUpdateToRuntime(final OperationContext context, final ModelNode operation,
                                           final String attributeName, final ModelNode newValue,
                                           final ModelNode currentValue, final HandbackHolder<Boolean> handbackHolder) throws OperationFailedException {
        if (context.getResourceRegistration().getAttributeAccess(PathAddress.EMPTY_ADDRESS, attributeName).getFlags().contains(AttributeAccess.Flag.RESTART_ALL_SERVICES)) {
            // Not a runtime attribute; restart required
            return true;
        }

        final ServiceController<?> service = getService(context, operation);
        if (service == null) {
            // The service isn't installed, so the work done in the Stage.MODEL part is all there is to it
            return false;
        } else if (service.getState() != ServiceController.State.UP) {
            // Service is installed but not up?
            //throw new IllegalStateException(String.format("Cannot apply attribue %s to runtime; service %s is not in state %s, it is in state %s",
            //            attributeName, MessagingServices.JBOSS_MESSAGING, ServiceController.State.UP, hqService.getState()));
            // No, don't barf; just let the update apply to the model and put the server in a reload-required state
            return true;
        } else {
            // Actually apply the update
            final ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
            applyOperation(context, model, attributeName, service, false);
            handbackHolder.setHandback(Boolean.TRUE);
            return false;
        }
    }

    @Override
    protected void revertUpdateToRuntime(final OperationContext context, final ModelNode operation,
                                         final String attributeName, final ModelNode valueToRestore,
                                         final ModelNode valueToRevert, final Boolean handback) throws OperationFailedException {
        if (handback != null && handback.booleanValue() && context.getResourceRegistration().getAttributeAccess(PathAddress.EMPTY_ADDRESS, attributeName).getFlags().contains(AttributeAccess.Flag.RESTART_NONE)) {
            final ServiceController<?> service = getService(context, operation);
            if (service != null && service.getState() == ServiceController.State.UP) {
                // Create and execute a write-attribute operation that uses the valueToRestore
                ModelNode revertModel = context.readResource(PathAddress.EMPTY_ADDRESS).getModel().clone();
                revertModel.get(attributeName).set(valueToRestore);
                applyOperation(context, revertModel, attributeName, service, true);
            }
        }
    }

    public void registerAttributes(final ManagementResourceRegistration registry) {
        for (AttributeDefinition attribute : this.attributes) {
            registry.registerReadWriteAttribute(attribute, null, this);
        }
    }

    protected abstract ServiceController<?> getService(final OperationContext context, final ModelNode model) throws OperationFailedException;

    protected abstract void applyOperation(final OperationContext context, ModelNode operation, String attributeName,
                                           ServiceController<?> service, boolean forRollback) throws OperationFailedException;


    static TimeSpec getTimeSpec(OperationContext context, ModelNode model, TimeUnit defaultUnit) throws OperationFailedException {
        ModelNode value = PoolAttributeDefinitions.KEEPALIVE_TIME.resolveModelAttribute(context, model);
        if (!value.hasDefined(TIME)) {
            throw ThreadsLogger.ROOT_LOGGER.missingTimeSpecTime(TIME, KEEPALIVE_TIME);
        }
        final TimeUnit unit;
        if (!value.hasDefined(UNIT)) {
            unit = defaultUnit;
        } else {
            try {
            unit = Enum.valueOf(TimeUnit.class, value.get(UNIT).asString());
            } catch(IllegalArgumentException e) {
                throw ThreadsLogger.ROOT_LOGGER.failedToParseUnit(UNIT, Arrays.asList(TimeUnit.values()));
            }
        }
        return new TimeSpec(unit, value.get(TIME).asLong());
    }
}
