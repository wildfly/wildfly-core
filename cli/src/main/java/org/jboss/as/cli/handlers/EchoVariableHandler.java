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
package org.jboss.as.cli.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.util.CLIExpressionResolver;

/**
 *
 * @author Alexey Loubyansky
 */
public class EchoVariableHandler extends CommandHandlerWithHelp {

    public EchoVariableHandler() {
        super("echo");
        // The line takes benefit of top level operation/command completer
        // that does handle variable completion of any value starting with '$'
        // and followed by 0 to n characters.
        // No reference is kept for this argument, it is automaticaly added to this
        // handler in the ArgumentWithValue constructor.
        new ArgumentWithValue(this, 0, "--variable");
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#handle(org.jboss.as.cli.CommandContext, java.lang.String)
     */
    @Override
    protected void doHandle(CommandContext ctx) throws CommandFormatException {
        final ParsedCommandLine line = ctx.getParsedCommandLine();
        String result = line.getSubstitutedLine().trim().substring(line.getOperationName().length()).trim();
        if(ctx.isResolveParameterValues()) {
            result = CLIExpressionResolver.resolve(result);
        }
        // apply escape rules
        int i = result.indexOf('\\');
        if(i >= 0) {
            final StringBuilder buf = new StringBuilder(result.length() - 1);
            buf.append(result.substring(0, i));
            boolean escaped = true;
            while(++i < result.length()) {
                if(escaped) {
                    buf.append(result.charAt(i));
                    escaped = false;
                } else {
                    final char c = result.charAt(i);
                    if(c == '\\') {
                        escaped = true;
                    } else {
                        buf.append(c);
                    }
                }
            }
            result = buf.toString();
        }
        ctx.printLine(result);
    }

    @Override
    protected void recognizeArguments(CommandContext ctx) throws CommandFormatException {

        final ParsedCommandLine args = ctx.getParsedCommandLine();
        final Set<String> propertyNames = args.getPropertyNames();
        if(!propertyNames.isEmpty()) {
            final Collection<String> names;
            if(helpArg.isPresent(args)) {
                if(propertyNames.size() == 1) {
                    return;
                }
                names = new ArrayList<String>(propertyNames);
                names.remove(helpArg.getFullName());
                names.remove(helpArg.getShortName());
            } else {
                names = propertyNames;
            }
            throw new CommandFormatException("Unrecognized argument names: " + names);
        }
    }
}
