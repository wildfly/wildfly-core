/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.jboss.as.controller.OperationContext;
import org.jboss.dmr.ModelNode;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;

/**
 * @author Stuart Douglas
 */
public class CompositeOperationAwareTransmuter implements DomainOperationTransmuter {

    private final ModelNode newOperation;

    public CompositeOperationAwareTransmuter(final ModelNode newOperation) {
        this.newOperation = newOperation;
    }

    @Override
    public ModelNode transmmute(final OperationContext context, final ModelNode operation) {
        if (operation.get(OP).asString().equals(COMPOSITE)) {
            ModelNode ret = operation.clone();
            final List<ModelNode> list = new ArrayList<ModelNode>();
            ListIterator<ModelNode> it = ret.get(STEPS).asList().listIterator();
            while (it.hasNext()) {
                final ModelNode subOperation = it.next();
                list.add(transmmute(context, subOperation));
            }
            ret.get(STEPS).set(list);
            return ret;
        } else {
            if (matches(operation, newOperation)) {
                return newOperation.clone();
            } else {
                return operation;
            }
        }
    }

    protected boolean matches(final ModelNode operation, final ModelNode newOperation) {
        return operation.get(OP).equals(newOperation.get(OP)) &&
                operation.get(ADDRESS).equals(newOperation.get(ADDRESS));
    }
}
