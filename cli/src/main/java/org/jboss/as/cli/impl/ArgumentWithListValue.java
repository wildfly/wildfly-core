/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.impl;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.handlers.CommandHandlerWithArguments;
import org.jboss.as.cli.operation.ParsedCommandLine;

/**
 * @author Ryan Emerson
 */
public class ArgumentWithListValue extends ArgumentWithValue {

    public ArgumentWithListValue(CommandHandlerWithArguments handler, String fullName) {
        super(handler, fullName);
    }

    public ArgumentWithListValue(CommandHandlerWithArguments handler, CommandLineCompleter completer, String fullName) {
        super(handler, completer, fullName);
    }

    @Override
    public String getValue(ParsedCommandLine args, boolean required) throws CommandFormatException {
        return stripArrayCharacters(super.getValue(args, required));
    }

    private String stripArrayCharacters(String line) {
        if (line == null || line.length() == 0)
            return line;

        line = line.trim();
        if (line.startsWith("["))
            line = line.substring(1);

        if (line.endsWith("]"))
            line = line.substring(0, line.length() - 1);

        return line;
    }
}
