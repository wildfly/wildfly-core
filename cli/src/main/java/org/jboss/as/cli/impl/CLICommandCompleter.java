/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aesh.complete.AeshCompleteOperation;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.parsing.CommandSubstitutionException;
import org.jboss.as.cli.parsing.UnresolvedVariableException;
import org.jboss.as.cli.parsing.operation.OperationFormat;

/**
 * Shared completion logic between CLI native completer and Aesh completer. It
 * mainly handles variables completion.
 *
 * @author jdenise@redhat.com
 */
public class CLICommandCompleter {

    public interface Completer {

        void addAllCommandNames(CommandContext ctx, AeshCompleteOperation op);
        void complete(CommandContext ctx, DefaultCallbackHandler parsedCmd, AeshCompleteOperation op);
    }

    public void complete(CommandContext ctx, AeshCompleteOperation op, Completer completer) {
        // support for commands and operations spread across multiple lines
        int offset = 0;
        String buffer = op.getBuffer();
        if (ctx.getArgumentsString() != null) {
            offset = ctx.getArgumentsString().length();
            buffer = ctx.getArgumentsString() + buffer;
        }
        AeshCompleteOperation op2 = new AeshCompleteOperation(op.getContext(), buffer,
                op.getCursor());
        doComplete(ctx, op2, completer);
        if (!op2.getCompletionCandidates().isEmpty()) {
            transferOperation(op2, op);
            op.setOffset(op2.getOffset() - offset);
        }
    }

    public static void transferOperation(AeshCompleteOperation from, AeshCompleteOperation to) {
        to.setIgnoreNonEscapedSpace(from.doIgnoreNonEscapedSpace());
        to.setIgnoreOffset(from.doIgnoreOffset());
        to.setIgnoreStartsWith(from.isIgnoreStartsWith());
        to.setSeparator(from.getSeparator());
        to.doAppendSeparator(from.hasAppendSeparator());
        if (!from.getCompletionCandidates().isEmpty()) {
            to.addCompletionCandidatesTerminalString(from.getCompletionCandidates());
            to.setOffset(from.getOffset());
        }
    }

    protected void doComplete(CommandContext ctx, AeshCompleteOperation op, Completer completer) {
        String buffer = op.getBuffer();
        int cursor = op.getCursor();
        if (buffer.isEmpty() || cursor == 0) {
            completer.addAllCommandNames(ctx, op);
            return;
        }

        final DefaultCallbackHandler parsedCmd = (DefaultCallbackHandler) ctx.getParsedCommandLine();
        boolean unresolvedVariableException = false;
        List<String> candidates = new ArrayList<>();
        try {
            // WFCORE-1627 parse current position if cursor moves
            if (ctx.getArgumentsString() == null && buffer.length() > cursor) {
                parsedCmd.parse(ctx.getCurrentNodePath(), buffer.substring(0, cursor), false, ctx);
            } else if (ctx.getArgumentsString() != null && buffer.length() - ctx.getArgumentsString().length() > cursor) {
                // multiple lines
                parsedCmd.parse(ctx.getCurrentNodePath(), buffer.substring(0, ctx.getArgumentsString().length() + cursor), false, ctx);
            } else {
                parsedCmd.parse(ctx.getCurrentNodePath(), buffer, false, ctx);
            }
        } catch (UnresolvedVariableException e) {
            unresolvedVariableException = true;
            final String variable = e.getExpression();
            if (buffer.endsWith(variable)) {
                for (String var : ctx.getVariables()) {
                    if (var.startsWith(variable)) {
                        candidates.add(var);
                    }
                }
                Collections.sort(candidates);
                op.addCompletionCandidates(candidates);
                op.setOffset(buffer.length() - variable.length());
                return;
            } else {
                // failed to resolve a variable in the middle of the line
            }
            return;
        } catch (CommandSubstitutionException e) {
            final String cmd = e.getExpression();
            if (buffer.endsWith(cmd)) {
                AeshCompleteOperation subsOp = new AeshCompleteOperation(op.getContext(), cmd, cmd.length());
                doComplete(ctx, subsOp, completer);
                if (subsOp.getCompletionCandidates().isEmpty()) {
                    return;
                }
                transferOperation(subsOp, op);
                op.setOffset(buffer.length() - cmd.length() + subsOp.getOffset());
                return;
            } else {
                // failed to substitute a command in the middle of the line
            }
            return;
        } catch (CommandFormatException e) {
            if (!parsedCmd.endsOnAddressOperationNameSeparator() || !parsedCmd.endsOnSeparator()) {
                return;
            }
        }

        completer.complete(ctx, parsedCmd, op);
        // Util.NOT_OPERATOR not supported in commands.
        if (parsedCmd.getFormat() != OperationFormat.INSTANCE) {
            int notIndex = candidates.indexOf(Util.NOT_OPERATOR);
            if (notIndex >= 0) {
                candidates.remove(notIndex);
            }
        }

        // https://issues.jboss.org/browse/WFCORE-1714
        if (!unresolvedVariableException) {
            int index = buffer.lastIndexOf('$');
            if (index != -1 && buffer.length() != index + 1) {
                String variable = buffer.substring(index + 1);
                if (buffer.endsWith(variable)) {
                    List<String> candidateVariables = new ArrayList<>();
                    for (String e : ctx.getVariables()) {
                        if (e.startsWith(variable) && !e.equals(variable)) {
                            candidateVariables.add(e);
                        }
                    }
                    if (!candidateVariables.isEmpty()) {
                        candidates.addAll(candidateVariables);
                        Collections.sort(candidates);
                        op.addCompletionCandidates(candidates);
                        op.setOffset(index + 1);
                        return;
                    }
                } else {
                    // failed to resolve a variable in the middle of the line
                }
            }
        }

        // if there is nothing else to suggest, check whether it could be a start of a variable
        if (candidates.isEmpty() && buffer.charAt(buffer.length() - 1) == '$' && !ctx.getVariables().isEmpty()) {
            candidates.addAll(ctx.getVariables());
            Collections.sort(candidates);
            op.addCompletionCandidates(candidates);
            op.setOffset(buffer.length());
        }
    }
}
