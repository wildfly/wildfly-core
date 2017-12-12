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
package org.jboss.as.cli;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.as.cli.impl.CommandCandidatesProvider;
import org.jboss.as.cli.operation.OperationCandidatesProvider;
import org.jboss.as.cli.operation.OperationRequestCompleter;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.parsing.CommandSubstitutionException;
import org.jboss.as.cli.parsing.UnresolvedVariableException;
import org.jboss.as.cli.parsing.command.CommandFormat;
import org.jboss.as.cli.parsing.operation.OperationFormat;


/**
 * Tab-completer for commands starting with '/'.
 *
 * @author Alexey Loubyansky
 */
public class CommandCompleter implements CommandLineCompleter {

    private final CommandRegistry cmdRegistry;
    private final CommandCandidatesProvider cmdProvider;

    public CommandCompleter(CommandRegistry cmdRegistry) {
        if(cmdRegistry == null)
            throw new IllegalArgumentException("Command registry can't be null.");
        this.cmdRegistry = cmdRegistry;
        this.cmdProvider = new CommandCandidatesProvider(cmdRegistry);
    }

    @Override
    public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
        // support for commands and operations spread across multiple lines
        int offset = 0;
        if(ctx.getArgumentsString() != null) {
            offset = ctx.getArgumentsString().length();
            buffer = ctx.getArgumentsString() + buffer;
        }
        final int result = doComplete(ctx, buffer, cursor, candidates);
        if(result < 0) {
            return result;
        }

        return result - offset;
    }

    protected int doComplete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {

        if(buffer.isEmpty()) {
            for(String cmd : cmdRegistry.getTabCompletionCommands()) {
                CommandHandler handler = cmdRegistry.getCommandHandler(cmd);
                if(handler.isAvailable(ctx)) {
                    candidates.add(cmd);
                }
            }
            Collections.sort(candidates);
            candidates.add(OperationFormat.INSTANCE.getAddressOperationSeparator());
            return 0;
        }

        final DefaultCallbackHandler parsedCmd = (DefaultCallbackHandler) ctx.getParsedCommandLine();
        boolean unresolvedVariableException = false;
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
        } catch(UnresolvedVariableException e) {
            unresolvedVariableException = true;
            final String variable = e.getExpression();
            if(buffer.endsWith(variable)) {
                for(String var : ctx.getVariables()) {
                    if(var.startsWith(variable)) {
                        candidates.add(var);
                    }
                }
                Collections.sort(candidates);
                return buffer.length() - variable.length();
            } else {
                // failed to resolve a variable in the middle of the line
            }
            return -1;
        } catch(CommandSubstitutionException e) {
            final String cmd = e.getExpression();
            if(buffer.endsWith(cmd)) {
                int i = doComplete(ctx, cmd, cmd.length(), candidates);
                if(i < 0) {
                    return -1;
                }
                return buffer.length() - cmd.length() + i;
            } else {
                // failed to substitute a command in the middle of the line
            }
            return -1;
        } catch(CommandFormatException e) {
            if(!parsedCmd.endsOnAddressOperationNameSeparator() || !parsedCmd.endsOnSeparator()) {
                return -1;
            }
        }

        final OperationCandidatesProvider candidatesProvider;
        if(buffer.isEmpty() || parsedCmd.getFormat() == CommandFormat.INSTANCE) {
            candidatesProvider = cmdProvider;
        } else  {
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

        // https://issues.jboss.org/browse/WFCORE-1714
        if (!unresolvedVariableException) {
            int index = buffer.lastIndexOf('$');
            if (index != -1 && buffer.length() != index + 1) {
                String variable = buffer.substring(index + 1);
                if (buffer.endsWith(variable)) {
                    List<String> candidateVariables = new ArrayList<>();
                    for (String e : ctx.getVariables()) {
                        if (e.startsWith(variable) && ! e.equals(variable)) {
                            candidateVariables.add(e);
                        }
                    }
                    if (!candidateVariables.isEmpty()) {
                        candidates.addAll(candidateVariables);
                        Collections.sort(candidates);
                        return index + 1;
                    }
                } else {
                    // failed to resolve a variable in the middle of the line
                }
            }
        }

        // if there is nothing else to suggest, check whether it could be a start of a variable
        if(candidates.isEmpty() && buffer.charAt(buffer.length() - 1) == '$' && !ctx.getVariables().isEmpty()) {
            candidates.addAll(ctx.getVariables());
            Collections.sort(candidates);
            return buffer.length();
        }
        return result;
    }
}
