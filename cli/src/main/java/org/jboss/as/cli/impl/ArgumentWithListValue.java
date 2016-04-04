/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
