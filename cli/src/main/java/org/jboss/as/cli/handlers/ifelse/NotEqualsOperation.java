/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.ifelse;

import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public class NotEqualsOperation extends ComparisonOperation {

    static final String SYMBOL = "!=";

    NotEqualsOperation() {
        super(SYMBOL);
    }

    @Override
    protected boolean compare(Object left, Object right) {
        if(left == null) {
            return right != null;
        }
        if(((ModelNode) left).getType() != ((ModelNode)right).getType()) {
            return true;
        }
        return !left.equals(right);
    }
}
