/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation.impl;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.Iterator;

import org.jboss.as.cli.Util;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.OperationRequestAddress.Node;
import org.jboss.as.cli.operation.OperationRequestBuilder;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public class DefaultOperationRequestBuilder implements OperationRequestBuilder {

    private ModelNode request = new ModelNode();
    private OperationRequestAddress prefix;

    public DefaultOperationRequestBuilder() {
        this.prefix = new DefaultOperationRequestAddress();
    }

    public DefaultOperationRequestBuilder(OperationRequestAddress prefix) {
        this.prefix = new DefaultOperationRequestAddress(checkNotNullParam("prefix", prefix));
    }

    public OperationRequestAddress getAddress() {
        return prefix;
    }

    /**
     * Makes sure that the operation name and the address have been set and returns a ModelNode
     * representing the operation request.
     */
    public ModelNode buildRequest() throws OperationFormatException {

        ModelNode address = request.get(Util.ADDRESS);
        if(prefix.isEmpty()) {
            address.setEmptyList();
        } else {
            Iterator<Node> iterator = prefix.iterator();
            while (iterator.hasNext()) {
                OperationRequestAddress.Node node = iterator.next();
                if (node.getName() != null) {
                    address.add(node.getType(), node.getName());
                } else if (iterator.hasNext()) {
                    throw new OperationFormatException(
                            "The node name is not specified for type '"
                                    + node.getType() + "'");
                }
            }
        }

        if(!request.hasDefined(Util.OPERATION)) {
            throw new OperationFormatException("The operation name is missing or the format of the operation request is wrong.");
        }

        return request;
    }

    @Override
    public void setOperationName(String name) {
        request.get(Util.OPERATION).set(name);
    }

    @Override
    public void addNode(String type, String name) {
        prefix.toNode(type, name);
    }

    @Override
    public void addNodeType(String type) {
        prefix.toNodeType(type);
    }

    @Override
    public void addNodeName(String name) {
        prefix.toNode(name);
    }

    @Override
    public void addProperty(String name, String value) {

        if(name == null || name.trim().isEmpty())
            throw new IllegalArgumentException("The argument name is not specified: '" + name + "'");
        if(value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("The argument value is not specified for " + name + ": '" + value + "'");
        ModelNode toSet = null;
        try {
            toSet = ModelNode.fromString(value);
        } catch (Exception e) {
            // just use the string
            toSet = new ModelNode().set(value);
        }
        request.get(name).set(toSet);
    }

    public ModelNode getModelNode() {
        return request;
    }
}
