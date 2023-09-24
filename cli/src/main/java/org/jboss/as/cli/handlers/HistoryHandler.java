/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers;

import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandHistory;
import org.jboss.as.cli.impl.ArgumentWithoutValue;
import org.jboss.as.cli.operation.ParsedCommandLine;

/**
 *
 * @author Alexey Loubyansky
 */
public class HistoryHandler extends CommandHandlerWithHelp {

    private final ArgumentWithoutValue clear;
    private final ArgumentWithoutValue disable;
    private final ArgumentWithoutValue enable;

    public HistoryHandler() {
        this("history");
    }

    public HistoryHandler(String command) {
        super(command);

        clear = new ArgumentWithoutValue(this, "--clear");
        clear.setExclusive(true);

        disable = new ArgumentWithoutValue(this, "--disable");
        disable.setExclusive(true);

        enable = new ArgumentWithoutValue(this, "--enable");
        enable.setExclusive(true);
    }

    @Override
    protected void doHandle(CommandContext ctx) throws CommandFormatException {

        ParsedCommandLine args = ctx.getParsedCommandLine();
        if(!args.hasProperties()) {
            printHistory(ctx);
            return;
        }

        if(clear.isPresent(args)) {
            ctx.getHistory().clear();
        } else if(disable.isPresent(args)) {
            ctx.getHistory().setUseHistory(false);
        } else if(enable.isPresent(args)) {
            ctx.getHistory().setUseHistory(true);
        } else {
            throw new CommandFormatException("Unexpected argument '" + ctx.getArgumentsString() + '\'');
        }
    }

    private static void printHistory(CommandContext ctx) {

        CommandHistory history = ctx.getHistory();
        List<String> list = history.asList();
        for(String cmd : list) {
            ctx.printLine(cmd);
        }
        ctx.printLine("(The history is currently " + (history.isUseHistory() ? "enabled)" : "disabled)"));
    }
}
