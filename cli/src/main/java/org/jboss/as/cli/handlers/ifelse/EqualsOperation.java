/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.ifelse;

/**
 *
 * @author Alexey Loubyansky
 */
public class EqualsOperation extends SameTypeOperation {

    static final String SYMBOL = "==";

    EqualsOperation() {
        super(SYMBOL);
    }

    @Override
    protected boolean doCompare(Object left, Object right) {
        if(left == null) {
            return right == null;
        }
        return left.equals(right);
    }
}
