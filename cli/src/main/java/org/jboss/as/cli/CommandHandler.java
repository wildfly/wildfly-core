/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli;

import java.util.Collection;


/**
 *
 * @author Alexey Loubyansky
 */
public interface CommandHandler {

    /**
     * Checks whether the command is available in the current context
     * (e.g. some commands require connection with the controller,
     * some are available only in the batch mode, etc)
     * @param ctx  current context
     * @return  true if the command can be executed in the current context, false - otherwise.
     */
    boolean isAvailable(CommandContext ctx);

    /**
     * Whether the command supports batch mode or not.
     * The result could depend on the context, e.g. it won't make sense
     * to add 'some_command --help' to a batch.
     *
     * @param ctx  the current context
     * @return  true if the command can be added to the batch, otherwise - false.
     */
    boolean isBatchMode(CommandContext ctx);

    /**
     * Executes the command.
     * @param ctx  current command context
     * @throws CommandLineException  if for any reason the command can't be properly handled
     * the implementation must throw an instance of CommandLineException.
     */
    void handle(CommandContext ctx) throws CommandLineException;

    /**
     * Returns command argument declared by the command handler
     * corresponding to the argument name. Or null if the argument
     * wasn't found among the accepted arguments.
     *
     * @param ctx  the current context
     * @param name  the name of the argument
     * @return  command argument corresponding to the argument name or null
     * if the argument name wasn't recognized.
     */
    CommandArgument getArgument(CommandContext ctx, String name);

    /**
     * Checks whether the command handler recognizes the argument by the name.
     * @param ctx  the current context
     * @param name  argument name to check
     * @return  true if the handler recognizes the argument, otherwise - false.
     */
    boolean hasArgument(CommandContext ctx, String name);

    /**
     * Checks whether the command handler accepts an argument with the specified index.
     * @param ctx  the current context
     * @param index  argument index to check
     * @return  true if the handler accepts an argument with the specified index, otherwise - false.
     */
    boolean hasArgument(CommandContext ctx, int index);

    /**
     * Returns a collection of the command arguments the handler supports in the current context.
     *
     * @param ctx  current command line context
     * @return  list of the command arguments supported in the current context
     */
    Collection<CommandArgument> getArguments(CommandContext ctx);
}
