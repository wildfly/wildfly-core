/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.dmr.ModelNode;

/**
 * Encapsulates information about a boot operation for use during boot execution.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ParsedBootOp {

    public final ModelNode operation;
    public final String operationName;
    public final PathAddress address;
    public final OperationStepHandler handler;
    public final ModelNode response;
    private List<ModelNode> childOperations;
    private boolean bootHandlerUpdateNeeded = false;

    ParsedBootOp(final ModelNode operation) {
        this(operation, null, new ModelNode());
    }

    public ParsedBootOp(final ModelNode operation, final OperationStepHandler handler) {
        this(operation, handler, new ModelNode());
    }

    ParsedBootOp(final ModelNode operation, final OperationStepHandler handler, final ModelNode response) {
        this.operation = operation;
        this.address = PathAddress.pathAddress(operation.get(OP_ADDR));
        this.operationName = operation.require(OP).asString();
        this.handler = handler;
        this.response = response;
    }

    public ParsedBootOp(final ParsedBootOp toCopy, final OperationStepHandler handler) {
        this.operation = toCopy.operation;
        this.address = toCopy.address;
        this.operationName = toCopy.operationName;
        this.handler = handler;
        this.response = toCopy.response;

    }

    public void addChildOperation(ParsedBootOp child) {
        if (childOperations == null) {
            childOperations = new ArrayList<ModelNode>();
        }
        childOperations.add(child.operation);
    }

    boolean isExtensionAdd() {
        return address.size() == 1 && address.getElement(0).getKey().equals(EXTENSION)
                && operationName.equals(ADD);
    }

    boolean isInterfaceOperation() {
        return address.size() > 0 && address.getElement(0).getKey().equals(INTERFACE);
    }

    boolean isSocketOperation() {
        return address.size() > 0 && address.getElement(0).getKey().equals(SOCKET_BINDING_GROUP);
    }

    public List<ModelNode> getChildOperations() {
        return childOperations == null ? Collections.<ModelNode>emptyList() : Collections.unmodifiableList(childOperations);
    }

    public PathAddress getAddress() {
        return address;
    }

    public boolean isBootHandlerUpdateNeeded() {
        return this.handler instanceof ParallelBootOperationStepHandler && bootHandlerUpdateNeeded;
    }

    /**
     * Setting this will force the ParallelBootOperationStepHandler handdler to be updated on boot.
     */
    public void bootHandlerUpdateNeeded() {
        this.bootHandlerUpdateNeeded = true;
    }


    @Override
    public String toString() {
        return "ParsedBootOp{" + "operation=" + operation + ", operationName=" + operationName + ", address=" + address + ", handler=" + handler + ", response=" + response + ", childOperations=" + childOperations + '}';
    }
}
