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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.aesh.complete.AeshCompleteOperation;
import org.aesh.readline.completion.Completion;
import org.aesh.readline.completion.CompletionHandler;
import org.aesh.readline.terminal.formatting.TerminalString;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.CLICommandCompleter;
import org.jboss.as.cli.impl.CLICommandCompleter.Completer;
import org.jboss.as.cli.impl.CommandContextImpl;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.parsing.StateParser;
import org.jboss.as.cli.parsing.operation.OperationFormat;
import org.jboss.logging.Logger;

/**
 * The main entry point for completion. Whatever the type of command and
 * operation, completion is done from this class. Variable completion is done in
 * CLICommandCompleter.
 *
 * @author jdenise@redhat.com
 */
class CLICompletionHandler extends CompletionHandler<AeshCompleteOperation> implements Completion<AeshCompleteOperation>,
        CommandLineCompleter, Completer {

    private static final Logger LOG = Logger.getLogger(CLICompletionHandler.class);

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

        LOG.debugf("Completing {0}", co.getBuffer());
        cliCompleter.complete(ctx, co, this);

        if (ctx.isColorOutput()) {
            List<TerminalString> completionCandidates = co.getCompletionCandidates();
            List<TerminalString> requiredCandidates = new ArrayList<>();
            for (TerminalString candidate : completionCandidates) {
                if (candidate.toString().endsWith("*") && !"*".equals(candidate.toString())) {
                    TerminalString newCandidate = Util.formatRequired(candidate);
                    requiredCandidates.add(newCandidate);
                }
            }
            completionCandidates.removeIf(new Predicate<TerminalString>() {
                @Override
                public boolean test(TerminalString candidate) {
                    return candidate.toString().endsWith("*");
                }
            });
            completionCandidates.addAll(requiredCandidates);
        }

        LOG.debugf("Completion candidates {0}", co.getCompletionCandidates());
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
        Collections.sort(candidates);
        return co.getOffset();
    }

    @Override
    public void addAllCommandNames(CommandContext ctx, AeshCompleteOperation op) {
        op.addCompletionCandidate(OperationFormat.INSTANCE.getAddressOperationSeparator());
        op.addCompletionCandidates(aeshCommands.getRegistry().getAvailableAeshCommands());

    }

    @Override
    public void complete(CommandContext ctx, DefaultCallbackHandler parsedCmd, AeshCompleteOperation op) {

        // Completion occurs in Aesh runtime. That is required to properly handle operators.
        StateParser.SubstitutedLine substitutions = parsedCmd.getSubstitutions();
        // Build a CompleteOperation with substituted content.
        AeshCompleteOperation co = new AeshCompleteOperation(aeshCommands.getAeshContext(), parsedCmd.getSubstitutedLine(),
                substitutions.getSubstitutedOffset(op.getCursor()));
        /**
         * Legacy and new Aesh commands have some whitespace separator handling
         * that differ. Must complete differently per kind of command.
         */
        if (parsedCmd.hasOperator()) {
            completeAeshCommands(co);
        } else if (parsedCmd.getFormat() == OperationFormat.INSTANCE) {
            completeLegacyCommands(ctx, parsedCmd, co);
        } else if (aeshCommands.getRegistry().isLegacyCommand(parsedCmd.getOperationName())) {
            //special case when there are no properties, we could have to complete a command name.
            // name that could be the prefix of a legacy or new command
            if (!parsedCmd.hasProperties() && !parsedCmd.getOriginalLine().endsWith(" ")) {
                completeAeshCommands(co);
            } else {
                completeLegacyCommands(ctx, parsedCmd, co);
            }
        } else {
            completeAeshCommands(co);
        }
        if (!co.getCompletionCandidates().isEmpty()) {
            int correctedValueOffset = substitutions.getOriginalOffset(co.getOffset());
            co.setOffset(correctedValueOffset);
            CLICommandCompleter.transferOperation(co, op);
        }
    }

    private void completeAeshCommands(AeshCompleteOperation co) {
        aeshCommands.complete(co);
    }

    private void completeLegacyCommands(CommandContext ctx, DefaultCallbackHandler parsedCmd, AeshCompleteOperation co) {
        legacyCommandCompleter.complete(ctx, parsedCmd, co);
        // DMR Operations and command handler completion doesn't work well with whitespace separator, so must not be appended.
        // whitespace only appended at the end of a command name.
        String buffer = ctx.getArgumentsString() == null ? co.getBuffer() : ctx.getArgumentsString() + co.getBuffer();
        if (co.getCompletionCandidates().size() == 1
                && co.getCompletionCandidates().get(0).getCharacters().startsWith(buffer)) {
            co.doAppendSeparator(true);
        } else if (!co.getCompletionCandidates().isEmpty()) {
            co.doAppendSeparator(false);
        }
    }
}
