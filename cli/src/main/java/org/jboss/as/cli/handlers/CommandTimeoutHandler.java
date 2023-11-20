/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContext.TIMEOUT_RESET_VALUE;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.impl.ArgumentWithValue;

/**
 *
 * @author jfdenise
 */
public class CommandTimeoutHandler extends CommandHandlerWithHelp {

    private final ArgumentWithValue action;
    private final ArgumentWithValue value;
    private static final List<String> ACTIONS = new ArrayList<>();
    private static final List<String> VALUES = new ArrayList<>();
    private static final String GET = "get";
    private static final String RESET = "reset";
    private static final String SET = "set";

    static {
        ACTIONS.add(GET);
        ACTIONS.add(RESET);
        ACTIONS.add(SET);
        for (TIMEOUT_RESET_VALUE tr : TIMEOUT_RESET_VALUE.values()) {
            VALUES.add(tr.name().toLowerCase(Locale.ENGLISH));
        }
    }

    public CommandTimeoutHandler() {
        super("command-timeout");
        action = new ArgumentWithValue(this,
                (ctx, buffer, cursor, candidates)
                -> doComplete(ACTIONS, buffer, candidates), 0, "--action");

        CommandLineCompleter valueCompleter
                = (CommandContext ctx, String buffer, int cursor,
                        List<String> candidates) -> {
                    try {
                        if (!action.isPresent(ctx.getParsedCommandLine())) {
                            return -1;
                        }
                    } catch (CommandFormatException ex) {
                        return -1;
                    }
                    String act = action.getValue(ctx.getParsedCommandLine());
                    switch (act) {
                        case RESET: {
                            return doComplete(VALUES, buffer, candidates);
                        }
                        case GET:
                        case SET:
                        default:
                            return -1;
                    }
                };
        value = new ArgumentWithValue(this, valueCompleter, 1, "--value");

        value.addRequiredPreceding(action);
    }

    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {
        if (!action.isPresent(ctx.getParsedCommandLine())) {
            throw new CommandLineException("No command-timeout action, must be one of "
                    + ACTIONS);
        }

        String act = action.getValue(ctx.getParsedCommandLine());

        switch (act) {
            case GET: {
                ctx.printLine("" + ctx.getCommandTimeout());
                break;
            }
            case SET: {
                if (!value.isPresent(ctx.getParsedCommandLine())) {
                    throw new CommandLineException("No value to set");
                }
                try {
                    ctx.setCommandTimeout(Integer.parseInt(value.getValue(ctx.getParsedCommandLine())));
                } catch (NumberFormatException ex) {
                    throw new CommandLineException("Invalid command timeout value "
                            + value.getValue(ctx.getParsedCommandLine()));
                }
                break;
            }
            case RESET: {
                if (!value.isPresent(ctx.getParsedCommandLine())) {
                    throw new CommandLineException("No reset value");
                }
                String v = value.getValue(ctx.getParsedCommandLine());
                TIMEOUT_RESET_VALUE resetValue = Enum.valueOf(TIMEOUT_RESET_VALUE.class, v.toUpperCase(Locale.ENGLISH));
                ctx.resetTimeout(resetValue);
                break;
            }
            default: {
                throw new CommandLineException("Unknown command-timeout action, must be one of "
                        + ACTIONS);
            }
        }
    }

    private static int doComplete(List<String> values, String buffer, List<String> candidates) {
        if (buffer == null || buffer.isEmpty()) {
            candidates.addAll(values);
        } else {
            for (String v : values) {
                if (v.equals(buffer)) {
                    candidates.add(v + " ");
                    break;
                } else if (v.startsWith(buffer)) {
                    candidates.add(v);
                }
            }
        }
        return 0;
    }
}
