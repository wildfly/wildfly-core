/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl;

import static org.wildfly.common.Assert.checkNotNullParam;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.CommandHandlerWithArguments;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public class RequestParamArgWithValue extends ArgumentWithValue implements RequestParameterArgument {

    protected final String paramName;

    public RequestParamArgWithValue(String paramName, CommandHandlerWithArguments handler, CommandLineCompleter valueCompleter) {
        super(handler, valueCompleter, "--" + paramName);
        this.paramName = checkNotNullParam("paramName", paramName);
    }

    public RequestParamArgWithValue(String paramName, CommandHandlerWithArguments handler) {
        this(paramName, handler, "--" + paramName);
    }

    public RequestParamArgWithValue(String paramName, CommandHandlerWithArguments handler, String fullArgName) {
        super(handler, fullArgName);
        this.paramName = checkNotNullParam("paramName", paramName);
    }

    public RequestParamArgWithValue(String paramName, CommandHandlerWithArguments handler, String fullArgName, CommandLineCompleter completer) {
        super(handler, completer, fullArgName);
        this.paramName = checkNotNullParam("paramName", paramName);
    }

    public void set(ParsedCommandLine args, ModelNode request) throws CommandFormatException {
        final String value = getValue(args);
        if(value != null) {
            setValue(request, paramName, value);
        }
    }

    protected static void setValue(ModelNode request, String name, String value) {
        Util.setRequestProperty(request, name, value);
    }

    @Override
    public String getPropertyName() {
        return paramName;
    }
}
