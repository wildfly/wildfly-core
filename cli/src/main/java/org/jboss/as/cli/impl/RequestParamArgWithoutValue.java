/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.CommandHandlerWithArguments;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public class RequestParamArgWithoutValue extends ArgumentWithoutValue implements RequestParameterArgument {

    private final String paramName;

    public RequestParamArgWithoutValue(String paramName, CommandHandlerWithArguments handler) {
        super(handler, "--" + paramName);
        this.paramName = paramName;
    }

    @Override
    public void set(ParsedCommandLine args, ModelNode request) throws CommandFormatException {
        if(isPresent(args)) {
            Util.setRequestProperty(request, paramName, "true");
        }
    }

    @Override
    public String getPropertyName() {
        return paramName;
    }

}
