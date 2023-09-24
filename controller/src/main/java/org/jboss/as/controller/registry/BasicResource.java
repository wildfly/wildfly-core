/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.registry;

import java.util.ConcurrentModificationException;
import java.util.Set;

import org.jboss.dmr.ModelNode;

/**
 * Standard {@link Resource} implementation.
 *
 * <p>Concurrency note: if a thread needs to modify a BasicResource, it must use the clone() method to obtain its
 * own copy of the resource. That instance cannot be made visible to other threads until all writes are complete.</p>
 *
 * @author Emanuel Muckenhuber
 */
class BasicResource extends AbstractModelResource implements Resource {

    /** The local model. */
    private final ModelNode model = new ModelNode();

    protected BasicResource() {
        this(false);
    }

    protected BasicResource(boolean runtimeOnly) {
        super(runtimeOnly);
    }

    protected BasicResource(boolean runtimeOnly, String...orderedChildTypes) {
        super(runtimeOnly, orderedChildTypes);
    }

    protected BasicResource(boolean runtimeOnly, Set<String> orderedChildTypes) {
        super(runtimeOnly, orderedChildTypes);
    }

    private BasicResource(boolean runtimeOnly, Set<String> orderedChildTypes, boolean safe) {
        super(runtimeOnly, orderedChildTypes, safe);
    }

    @Override
    public ModelNode getModel() {
        return model;
    }

    @Override
    public void writeModel(ModelNode newModel) {
        model.set(newModel);
    }

    @Override
    public boolean isModelDefined() {
        return model.isDefined();
    }
    @SuppressWarnings({"CloneDoesntCallSuperClone"})
    @Override
    public Resource clone() {
        final BasicResource clone = new BasicResource(isRuntime(), getOrderedChildTypes(), true);
        for (;;) {
            try {
                clone.writeModel(model);
                break;
            } catch (ConcurrentModificationException ignore) {
                // TODO horrible hack :(
            }
        }
        cloneProviders(clone);
        return clone;
    }

}
