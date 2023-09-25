/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.completion.mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
*
* @author Alexey Loubyansky
*/
public class MockNode {
    private final String name;
    private Map<String, MockNode> children;
    private Map<String, MockOperation> operations;

    public MockNode(String name) {
        this.name = name;
    }

    public MockNode remove(String name) {
        return children == null ? null : children.remove(name);
    }

    public MockNode getChild(String name) {
        return children == null ? null : children.get(name);
    }

    public MockNode addChild(String name) {
        MockNode child = new MockNode(name);
        if(children == null) {
            children = new HashMap<String, MockNode>();
        }
        children.put(name, child);
        return child;
    }

    public void addChild(MockNode child) {
        if(children == null) {
            children = new HashMap<String, MockNode>();
        }
        children.put(child.name, child);
    }

    public List<String> getChildNames() {
        return children == null ? Collections.<String>emptyList() : new ArrayList<String>(children.keySet());
    }

    public Set<String> getOperationNames() {
        return operations == null ? Collections.<String>emptySet() : operations.keySet();
    }

    public MockOperation getOperation(String name) {
        return operations == null ? null : operations.get(name);
    }

    public void addOperation(MockOperation operation) {
        if(operations == null) {
            operations = new HashMap<String, MockOperation>();
        }
        operations.put(operation.getName(), operation);
    }
}