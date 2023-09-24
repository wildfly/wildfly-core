/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.ifelse;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.cli.CommandLineException;

/**
*
* @author Alexey Loubyansky
*/
abstract class BaseOperation implements Operation, Comparable<Operation> {

    private BaseOperation parent;
    private final String name;
    private final int priority;
    private final List<Operand> operands;

    BaseOperation(String name, int priority) {
        this.name = checkNotNullParam("name", name);
        this.priority = priority;
        operands = new ArrayList<Operand>();
    }

    void setParent(BaseOperation parent) {
        this.parent = parent;
    }

    BaseOperation getParent() {
        return parent;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public List<Operand> getOperands() {
        return operands;
    }

    public boolean allowsMoreArguments() {
        return operands.size() < 2;
    }

    void addOperand(Operand operand) throws CommandLineException {
        operands.add(checkNotNullParam("operand", operand));
    }

    Operand getLastOperand() {
        return operands.size() < 2 ? null : operands.get(operands.size() - 1);
    }

    void replaceLastOperand(Operand operand) throws CommandLineException {
        if(operands.size() < 2) {
            addOperand(operand);
        } else {
            operands.set(operands.size() - 1, operand);
        }
    }

    @Override
    public int compareTo(Operation o) {
        checkNotNullParam("o", o);
        return priority < o.getPriority() ? -1 : (priority > o.getPriority() ? 1 : 0);
    }

    @Override
    public String toString() {
        return '(' + name + ' ' + operands + ')';
    }
}