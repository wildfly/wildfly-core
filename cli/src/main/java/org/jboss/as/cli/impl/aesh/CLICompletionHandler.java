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
package org.jboss.as.cli.impl.aesh;

import java.util.List;
import java.util.logging.Level;
import org.aesh.complete.AeshCompleteOperation;
import org.aesh.readline.completion.Completion;
import org.aesh.readline.completion.CompletionHandler;
import org.aesh.readline.terminal.formatting.TerminalString;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.impl.CLICommandCompleter;
import org.jboss.as.cli.impl.CLICommandCompleter.Completer;
import org.jboss.as.cli.impl.CommandContextImpl;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.parsing.StateParser;
import org.jboss.logmanager.Logger;

/**
 *
 * @author jdenise@redhat.com
 */
class CLICompletionHandler extends CompletionHandler<AeshCompleteOperation> implements Completion<AeshCompleteOperation>,
        CommandLineCompleter, Completer {

    private static final Logger LOG = Logger.getLogger(CLICompletionHandler.class.getName());

    private final AeshCommands aeshCommands;
    private final CommandContextImpl ctx;
    private final CLICommandCompleter cliCompleter = new CLICommandCompleter();
    private Completer legacyCommandCompleter;

    CLICompletionHandler(AeshCommands aeshCommands, CommandContextImpl ctx) {
        this.aeshCommands = aeshCommands;
        this.ctx = ctx;
    }

    public void setLegacyCommandCompleter(Completer legacyCommandCompleter) {
        this.legacyCommandCompleter = legacyCommandCompleter;

    }
    @Override
    public void complete(AeshCompleteOperation co) {

        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, "Completing {0}", co.getBuffer());
        }
        cliCompleter.complete(ctx, co, this);
        String buffer = ctx.getArgumentsString() == null ? co.getBuffer() : ctx.getArgumentsString() + co.getBuffer();
        if (co.getCompletionCandidates().size() == 1
                && co.getCompletionCandidates().get(0).getCharacters().startsWith(buffer)) {
            co.doAppendSeparator(true);
        } else if (!co.getCompletionCandidates().isEmpty()) {
            co.doAppendSeparator(false);
        }
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, "Completion candidates {0}",
                    co.getCompletionCandidates());
        }
    }

    @Override
    public AeshCompleteOperation createCompleteOperation(String buffer, int cursor) {
        return new AeshCompleteOperation(aeshCommands.getAeshContext(), buffer, cursor);
    }

    @Override
    public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
        AeshCompleteOperation co = new AeshCompleteOperation(aeshCommands.getAeshContext(), buffer, cursor);
        complete(co);
        for (TerminalString ts : co.getCompletionCandidates()) {
            candidates.add(ts.getCharacters());
        }
        if (co.getCompletionCandidates().isEmpty()) {
            return -1;
        }
        return co.getOffset();
    }

    @Override
    public void addAllCommandNames(CommandContext ctx, AeshCompleteOperation op) {
        op.addCompletionCandidates(aeshCommands.getRegistry().getAvailableAeshCommands());
        legacyCommandCompleter.addAllCommandNames(ctx, op);
    }

    @Override
    public void complete(CommandContext ctx, DefaultCallbackHandler parsedCmd, AeshCompleteOperation op) {
        legacyCommandCompleter.complete(ctx, parsedCmd, op);
        boolean hasLegacyContent = !op.getCompletionCandidates().isEmpty();
        // Because Aesh parser doesn't handle variables, we need to ask completion with substituted command
        // then correct offset.
        StateParser.SubstitutedLine substitutions = parsedCmd.getSubstitutions();
        // Build a CompleteOperation with substituted content.
        AeshCompleteOperation co = new AeshCompleteOperation(aeshCommands.getAeshContext(), parsedCmd.getSubstitutedLine(),
                substitutions.getSubstitutedOffset(op.getCursor()));
        aeshCommands.complete(co);
        if (!co.getCompletionCandidates().isEmpty()) {
            int correctedValueOffset = substitutions.getOriginalOffset(co.getOffset());
            co.setOffset(correctedValueOffset);
            CLICommandCompleter.transferOperation(co, op);
        }
    }
}
