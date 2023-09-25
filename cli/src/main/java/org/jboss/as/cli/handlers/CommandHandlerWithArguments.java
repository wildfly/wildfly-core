/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.cli.CommandArgument;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandHandler;


/**
 *
 * @author Alexey Loubyansky
 */
public abstract class CommandHandlerWithArguments implements CommandHandler {

    private int maxArgumentIndex = -1;
    private Map<String, CommandArgument> args = Collections.emptyMap();
    private List<CommandArgument> argList = Collections.emptyList();

    public void addArgument(CommandArgument arg) {
        checkNotNullParam("arg", arg);
        if(arg.getIndex() > -1) {
            maxArgumentIndex = arg.getIndex() > maxArgumentIndex ? arg.getIndex() : maxArgumentIndex;
        }
        checkNotNullParam("arg.getFullName()", arg.getFullName());

        switch(argList.size()) {
            case 0:
                argList = Collections.singletonList(arg);
                args = new HashMap<String, CommandArgument>();
                break;
            case 1:
                CommandArgument tmp = argList.get(0);
                argList = new ArrayList<CommandArgument>();
                argList.add(tmp);
            default:
                argList.add(arg);
        }

        args.put(arg.getFullName(), arg);
        if(arg.getShortName() != null) {
            args.put(arg.getShortName(), arg);
        }
    }

    @Override
    public CommandArgument getArgument(CommandContext ctx, String name) {
        return args.get(name);
    }

    @Override
    public boolean hasArgument(CommandContext ctx, String name) {
        return args.containsKey(name);
    }

    @Override
    public boolean hasArgument(CommandContext ctx, int index) {
        //return index <= maxArgumentIndex;
        throw new UnsupportedOperationException("not used yet");
    }

    @Override
    public Collection<CommandArgument> getArguments(CommandContext ctx) {
        return argList;
    }

    protected void recognizeArguments(CommandContext ctx) throws CommandFormatException {
        final Set<String> specifiedNames = ctx.getParsedCommandLine().getPropertyNames();
        Map<String, CommandArgument> argsMap = getArgumentsMap(ctx);
        if (!argsMap.keySet().containsAll(specifiedNames)) {
            Collection<String> unrecognized = new HashSet<String>(specifiedNames);
            unrecognized.removeAll(argsMap.keySet());
            throw new CommandFormatException("Unrecognized arguments: " + unrecognized);
        }
        if(ctx.getParsedCommandLine().getOtherProperties().size() -1 > this.maxArgumentIndex) {
            throw new CommandFormatException("The command accepts " + (this.maxArgumentIndex + 1) + " unnamed argument(s) but received: "
                    + ctx.getParsedCommandLine().getOtherProperties());
        }
    }

    protected Map<String, CommandArgument> getArgumentsMap(CommandContext ctx) {
        return args;
    }
}
