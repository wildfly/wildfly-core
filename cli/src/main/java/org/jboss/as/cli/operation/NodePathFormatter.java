/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation;


/**
 * Creates a string representation of the OperationRequestAddress instance.
 *
 * @author Alexey Loubyansky
 */
public interface NodePathFormatter {

    /**
     * Creates a string representation of the Prefix instance.
     * @param prefix the prefix instance
     * @return  the string representation of the prefix.
     */
    String format(OperationRequestAddress prefix);
}
