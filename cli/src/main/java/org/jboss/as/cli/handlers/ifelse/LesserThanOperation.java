/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.ifelse;

/**
 *
 * @author Alexey Loubyansky
 */
public class LesserThanOperation extends SameTypeOperation {

    static final String SYMBOL = "<";

    LesserThanOperation() {
        super(SYMBOL);
    }

    @Override
    protected boolean doCompare(Object left, Object right) {
        if(left == null || right == null) {
            return false;
        }
        return ((Comparable<String>)left.toString()).compareTo(right.toString()) < 0;
    }
}
