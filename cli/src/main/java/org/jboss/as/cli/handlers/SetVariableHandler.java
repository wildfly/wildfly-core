/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.operation.ParsedCommandLine;


/**
 * @author Alexey Loubyansky
 *
 */
public class SetVariableHandler extends CommandHandlerWithHelp {

    public SetVariableHandler() {
        super("set");
        new ArgumentWithValue(this, new CommandLineCompleter(){
            @Override
            public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
                int equals = buffer.indexOf('=');
                if(equals < 1 || equals + 1 == buffer.length()) {
                    return -1;
                }
                // the problem is splitting values with whitespaces, e.g. for command substitution
                String value = buffer.substring(equals + 1);
                if (value.startsWith("`")) {
                    value = value.substring(1);
                    final int valueIndex = ctx.getDefaultCommandCompleter().complete(ctx, value, value.length(), candidates);
                    if (valueIndex < 0) {
                        return -1;
                    }
                    // + 1 for '=', +1 for '`'
                    return equals + 1 + valueIndex + 1;
                } else {
                    return -1;
                }
            }}, Integer.MAX_VALUE, "--variable") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                return !helpArg.isPresent(ctx.getParsedCommandLine());
            }
        };
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#doHandle(org.jboss.as.cli.CommandContext)
     */
    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {
        ParsedCommandLine parsedArgs = ctx.getParsedCommandLine();
        final List<String> vars = parsedArgs.getOtherProperties();
        if(vars.isEmpty()) {
            final Collection<String> defined = ctx.getVariables();
            if(defined.isEmpty()) {
                return;
            }
            final List<String> pairs = new ArrayList<String>(defined.size());
            for(String var : defined) {
                pairs.add(var + '=' + ctx.getVariable(var));
            }
            Collections.sort(pairs);
            for(String pair : pairs) {
                ctx.printLine(pair);
            }
            return;
        }
        for(String arg : vars) {
            arg = ArgumentWithValue.resolveValue(arg);
            if(arg.charAt(0) == '$') {
                arg = arg.substring(1);
                if(arg.isEmpty()) {
                    throw new CommandFormatException("Variable name is missing after '$'");
                }
            }
            final int equals = arg.indexOf('=');
            if(equals < 1) {
                throw new CommandFormatException("'=' is missing for variable '" + arg + "'");
            }
            final String name = arg.substring(0, equals);
            if(name.isEmpty()) {
                throw new CommandFormatException("The name is missing in '" + arg + "'");
            }
            if(equals == arg.length() - 1) {
                ctx.setVariable(name, null);
            } else {
                String value = arg.substring(equals + 1);
                if(value.length() > 2 && value.charAt(0) == '`' && value.charAt(value.length() - 1) == '`') {
                    value = Util.getResult(ctx, value.substring(1, value.length() - 1));
                }
                ctx.setVariable(name, value);
            }
        }
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
