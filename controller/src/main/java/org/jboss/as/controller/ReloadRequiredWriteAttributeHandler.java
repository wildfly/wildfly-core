/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import org.jboss.dmr.ModelNode;

import java.util.Collection;
import org.jboss.dmr.ModelType;

/**
 * Simple {@link AbstractWriteAttributeHandler} that triggers putting the process in a restart-required state if attribute that
 * has flag {@link org.jboss.as.controller.registry.AttributeAccess.Flag#RESTART_JVM} otherwise it puts process in
 * reload-required state.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ReloadRequiredWriteAttributeHandler extends AbstractWriteAttributeHandler<Void> {

    public static final OperationStepHandler INSTANCE = new ReloadRequiredWriteAttributeHandler();

    protected ReloadRequiredWriteAttributeHandler() {
    }

    /**
     * @deprecated Use {@link #INSTANCE} instead.
     */
    @Deprecated(forRemoval = true)
    public ReloadRequiredWriteAttributeHandler(final AttributeDefinition... definitions) {
        super(definitions);
    }

    /**
     * @deprecated Use {@link #INSTANCE} instead.
     */
    @Deprecated(forRemoval = true)
    public ReloadRequiredWriteAttributeHandler(final Collection<AttributeDefinition> definitions) {
        super(definitions);
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> voidHandback) throws OperationFailedException {
//      Some might ask why we are comparing a resolvedValue with the currentValuewhich is not resolved.
//      Well the issue is that the context that would permit to resolve the currentValue might have changed thus
//      the resolved value for the currentValue wouldn't be correct.
//      In fact we just can't resolve the currentValue without any doubt. When in doubt reload, so we will return true in this case.
//      For example if the currentValue is ${foo} and that for some reason foo has changed in between, then we should reload even if now ${foo} resolves
//      as resolvedValue.
        ModelNode resolvedTypedValue = convertToType(context, attributeName, resolvedValue);
        return !resolvedTypedValue.equals(currentValue);
    }

    private static ModelNode convertToType(OperationContext context, String attributeName, ModelNode resolvedValue) {
        AttributeDefinition attributeDefinition = context.getResourceRegistration().getAttributeAccess(PathAddress.EMPTY_ADDRESS, attributeName).getAttributeDefinition();
        ModelType type = attributeDefinition.getType();
        ModelNode converted = resolvedValue.clone();
        try {
            switch (type) {
                case BIG_DECIMAL:
                    converted.set(resolvedValue.asBigDecimal());
                    break;
                case BIG_INTEGER:
                    converted.set(resolvedValue.asBigInteger());
                    break;
                case BOOLEAN:
                    converted.set(resolvedValue.asBoolean());
                    break;
                case BYTES:
                    converted.set(resolvedValue.asBytes());
                    break;
                case DOUBLE:
                    converted.set(resolvedValue.asDouble());
                    break;
                case INT:
                    converted.set(resolvedValue.asInt());
                    break;
                case LIST:
                    break;
                case LONG:
                    converted.set(resolvedValue.asLong());
                    break;
                case OBJECT:
                    break;
                case PROPERTY:
                    break;
                case STRING:
                    converted.set(resolvedValue.asString());
                    break;
                case TYPE:
                    converted.set(resolvedValue.asType());
                    break;
                case UNDEFINED:
                    break;
            }
        } catch (IllegalArgumentException ex) {

        }
        return converted;
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode resolvedValue, Void handback) throws OperationFailedException {
        // no-op
    }
}
