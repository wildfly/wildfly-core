/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.handlers.CommandHandlerWithArguments;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public class RequiredRequestParamArg extends RequestParamArgWithValue {

    public RequiredRequestParamArg(String paramName, CommandHandlerWithArguments handler, CommandLineCompleter valueCompleter) {
        super(paramName, handler, valueCompleter);
    }

    public RequiredRequestParamArg(String paramName, CommandHandlerWithArguments handler) {
        super(paramName, handler);
    }

    public RequiredRequestParamArg(String paramName, CommandHandlerWithArguments handler, String fullArgName) {
        super(paramName, handler, fullArgName);
    }

    @Override
    public void set(ParsedCommandLine args, ModelNode request) throws CommandFormatException {
        setValue(request, paramName, getValue(args, true));
    }
}
