/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.operation;

import org.jboss.as.cli.CommandLineFormat;

/**
 *
 * @author Alexey Loubyansky
 */
public class OperationFormat implements CommandLineFormat {

    public static final OperationFormat INSTANCE = new OperationFormat();

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandLineFormat#getPropertyListStart()
     */
    @Override
    public String getPropertyListStart() {
        return "(";
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandLineFormat#getPropertyListEnd()
     */
    @Override
    public String getPropertyListEnd() {
        return ")";
    }

    @Override
    public boolean isPropertySeparator(char ch) {
        return ch == ',';
    }

    @Override
    public String getNodeSeparator() {
        return "/";
    }

    @Override
    public String getAddressOperationSeparator() {
        return ":";
    }

    @Override
    public String getPropertySeparator() {
        return ",";
    }
}
