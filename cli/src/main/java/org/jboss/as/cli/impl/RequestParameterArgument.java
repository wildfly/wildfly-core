/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl;

import org.jboss.as.cli.CommandArgument;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public interface RequestParameterArgument extends CommandArgument {

    void set(ParsedCommandLine args, ModelNode request) throws CommandFormatException;

    String getPropertyName();
}
