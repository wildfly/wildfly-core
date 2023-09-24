/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation.impl;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.operation.ParsedOperationRequestHeader;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public class SimpleParsedOperationRequestHeader implements ParsedOperationRequestHeader {

    private final String name;
    private final String value;

    public SimpleParsedOperationRequestHeader(String name, String value) throws CommandFormatException {
        if(name == null) {
            throw new CommandFormatException("Header name is null.");
        }
        if(value == null) {
            throw new CommandFormatException("Value for header '" + name + "' is null.");
        }
        this.name = name;
        this.value = value;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.operation.OperationRequestHeader#getName()
     */
    @Override
    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.operation.OperationRequestHeader#toModelNode()
     */
    @Override
    public void addTo(CommandContext ctx, ModelNode headers) throws CommandFormatException {
        if(name == null) {
            throw new CommandFormatException("Header name is null.");
        }
        if(value == null) {
            throw new CommandFormatException("Value for header '" + name + "' is null.");
        }
        headers.get(name).set(value);
    }
}
