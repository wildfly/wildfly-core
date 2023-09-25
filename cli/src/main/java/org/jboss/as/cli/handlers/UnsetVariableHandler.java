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
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.DefaultCompleter;
import org.jboss.as.cli.operation.ParsedCommandLine;

/**
 *
 * @author Alexey Loubyansky
 */
public class UnsetVariableHandler extends CommandHandlerWithHelp {

    public UnsetVariableHandler() {
        super("unset");
        new ArgumentWithValue(this, new DefaultCompleter(new DefaultCompleter.CandidatesProvider() {
            @Override
            public Collection<String> getAllCandidates(CommandContext ctx) {
                final List<String> specified = ctx.getParsedCommandLine().getOtherProperties();
                if(specified.isEmpty()) {
                    return ctx.getVariables();
                }
                if(ctx.getVariables().isEmpty()) {
                    return Collections.emptyList();
                }
                final ArrayList<String> all = new ArrayList<String>(ctx.getVariables());
                all.removeAll(specified);
                return all;
            }
        }), 0, "--variable") {
            @Override
            public boolean canAppearNext(CommandContext ctx) {
                try {
                    if(helpArg.isPresent(ctx.getParsedCommandLine())) {
                        return false;
                    }
                } catch (CommandFormatException e) {
                    return false;
                }
                return true;
            }
        };
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#handle(org.jboss.as.cli.CommandContext, java.lang.String)
     */
    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {
        final List<String> names = ctx.getParsedCommandLine().getOtherProperties();
        if(names.isEmpty()) {
            throw new CommandFormatException("Variable name is missing");
        }

        for (String name : names) {
            if (name.charAt(0) == '$') {
                name = name.substring(1);
                if (name.isEmpty()) {
                    throw new CommandFormatException("Variable name is missing after '$'");
                }
            }
            ctx.setVariable(name, null);
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
