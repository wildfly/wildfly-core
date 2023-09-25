/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.dmr.ModelNode;

/**
 * Represents a request header.
 *
 * @author Alexey Loubyansky
 */
public interface ParsedOperationRequestHeader {

    String getName();

    void addTo(CommandContext ctx, ModelNode headers) throws CommandFormatException;
}
