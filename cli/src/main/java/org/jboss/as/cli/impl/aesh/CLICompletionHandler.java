/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
