/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
import java.util.List;
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
            VALUES.add(tr.name().toLowerCase());
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
                TIMEOUT_RESET_VALUE resetValue = Enum.valueOf(TIMEOUT_RESET_VALUE.class, v.toUpperCase());
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
