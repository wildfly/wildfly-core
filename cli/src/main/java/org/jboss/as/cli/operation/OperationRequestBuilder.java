/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation;

import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public interface OperationRequestBuilder {

    /**
     * Sets the name operation to be invoked.
     *
     * @param name the name of the operation to invoke.
     */
    void setOperationName(String name);

    /**
     * The address is specified as a path to the target node. Each element of the path is a node
     * and is identified by its type and name.
     *
     * @param type the type of the node
     * @param name the name of the node
     */
    void addNode(String type, String name);

    /**
     * This method is supposed to be invoked from applying the prefix with ends on a node type.
     * @param type  the type of the node.
     */
    void addNodeType(String type);

    /**
     * This method assumes there is a non-empty prefix which ends on a node type.
     * Otherwise, this method will result in an exception.
     * @param name the name of the node for the type specified by the prefix.
     */
    void addNodeName(String name);

    /**
     * Adds an argument.
     *
     * @param name the name of the argument
     * @param value the value of the argument
     */
    void addProperty(String name, String value);

    /**
     * Builds the operation request based on the collected operation name, address and arguments.
     *
     * @return an instance of ModelNode representing the operation request
     * @throws OperationFormatException
     */
    ModelNode buildRequest() throws OperationFormatException;
}
