/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.aesh.complete.AeshCompleteOperation;
import org.aesh.readline.terminal.formatting.TerminalString;
import org.jboss.as.cli.impl.CLICommandCompleter;
import org.jboss.as.cli.impl.CLICommandCompleter.Completer;
import org.jboss.as.cli.impl.CommandCandidatesProvider;
import org.jboss.as.cli.operation.OperationCandidatesProvider;
import org.jboss.as.cli.operation.OperationRequestCompleter;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.parsing.command.CommandFormat;
import org.jboss.as.cli.parsing.operation.OperationFormat;


/**
 * Tab-completer for commands starting with '/'.
 *
 * @author Alexey Loubyansky
 */
public class CommandCompleter implements CommandLineCompleter, Completer {

    private final CommandRegistry cmdRegistry;
    private final CommandCandidatesProvider cmdProvider;
    private final CLICommandCompleter cliCompleter = new CLICommandCompleter();

    public CommandCompleter(CommandRegistry cmdRegistry) {
        this.cmdRegistry = checkNotNullParam("cmdRegistry", cmdRegistry);
        this.cmdProvider = new CommandCandidatesProvider(cmdRegistry);
    }

    @Override
    public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
        AeshCompleteOperation op = new AeshCompleteOperation(buffer, cursor);
        cliCompleter.complete(ctx, op, this);
        if (!op.getCompletionCandidates().isEmpty()) {
            for (TerminalString ts : op.getCompletionCandidates()) {
                candidates.add(ts.getCharacters());
            }
            return op.getOffset();
        }
        return -1;
    }

    @Override
    public void addAllCommandNames(CommandContext ctx, AeshCompleteOperation op) {
        List<String> candidates = new ArrayList<>();
        for (String cmd : cmdRegistry.getTabCompletionCommands()) {
            CommandHandler handler = cmdRegistry.getCommandHandler(cmd);
            if (handler.isAvailable(ctx)) {
                candidates.add(cmd);
            }
        }
        Collections.sort(candidates);
        candidates.add(OperationFormat.INSTANCE.getAddressOperationSeparator());
        op.addCompletionCandidates(candidates);
    }

    @Override
    public void complete(CommandContext ctx, DefaultCallbackHandler parsedCmd, AeshCompleteOperation op) {
        parsedCmd = parsedCmd == null ? (DefaultCallbackHandler) ctx.getParsedCommandLine() : parsedCmd;
        final OperationCandidatesProvider candidatesProvider;
        String buffer = op.getBuffer();
        int cursor = op.getCursor();
        List<String> candidates = new ArrayList<>();
        if (buffer.isEmpty() || parsedCmd.getFormat() == CommandFormat.INSTANCE) {
            candidatesProvider = cmdProvider;
        } else {
            candidatesProvider = ctx.getOperationCandidatesProvider();
        }

        final int result = OperationRequestCompleter.INSTANCE.complete(ctx, candidatesProvider, buffer, cursor, candidates);
        // Util.NOT_OPERATOR not supported in commands.
        if (parsedCmd.getFormat() != OperationFormat.INSTANCE) {
            int notIndex = candidates.indexOf(Util.NOT_OPERATOR);
            if (notIndex >= 0) {
                candidates.remove(notIndex);
            }
        }
        if (!candidates.isEmpty()) {
            Collections.sort(candidates);
            op.addCompletionCandidates(candidates);
            op.setOffset(result);
        }
    }
}
