/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.completion.operation.test;

import org.jboss.as.cli.CommandArgument;
import org.jboss.as.cli.CommandCompleter;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandRegistry;
import org.jboss.as.cli.completion.mock.MockCommandContext;
import org.jboss.as.cli.handlers.BaseOperationCommand;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

/**
 * @author Bartosz Spyrko-Smietanko
 */
public class PropertyValueCompletionTest {

    private MockCommandContext ctx;
    private CommandCompleter cmdCompleter;
    private CommandLineCompleter completer;

    public PropertyValueCompletionTest() throws Exception {
        ctx = new MockCommandContext();

        CommandRegistry cmdRegistry = new CommandRegistry();

        final BaseOperationCommand handler = new BaseOperationCommand(ctx, "test", false) {

            @Override
            protected ModelNode buildRequestWithoutHeaders(CommandContext ctx) throws CommandFormatException {
                return null;
            }


        };
        handler.addArgument(new TestCommandArgument("--arg0"));
        cmdRegistry.registerHandler(handler, "test");

        cmdCompleter = new CommandCompleter(cmdRegistry);
    }

    private ArrayList<String> candidates = new ArrayList<>();

    @Test
    public void replaceSuggestionWithSpaceIfUserEntryMatchesSuggestion() throws Exception {
        setSuggestion("retest", 0);

        assertEquals(" ", complete("test --arg0=retest"));
    }

    @Test
    public void replaceSuggestionWithSpaceIfUserEntryMatchesSuggestionAndSuggestionUsesOffset() throws Exception {
        setSuggestion("test", 2);

        assertEquals(" ", complete("test --arg0=retest"));
    }

    private String complete(String buffer) {
        cmdCompleter.complete(ctx, buffer, buffer.length(), candidates);
        return candidates.get(0);
    }

    private void setSuggestion(String result, int offset) {
        completer =  (ctx1, b, cursor, _candidates) -> {
            _candidates.add(result);
            return offset;
        };
    }

    private class TestCommandArgument implements CommandArgument {
        private final String name;

        TestCommandArgument(String name) {
            this.name = name;
        }

        @Override
        public String getFullName() {
            return name;
        }

        @Override
        public String getShortName() {
            return null;
        }

        @Override
        public int getIndex() {
            return 0;
        }

        @Override
        public CommandLineCompleter getValueCompleter() {

            return PropertyValueCompletionTest.this.completer;
        }

        @Override
        public boolean isPresent(ParsedCommandLine args) throws CommandFormatException {
            return false;
        }

        @Override
        public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
            return true;
        }

        @Override
        public String getValue(ParsedCommandLine args) throws CommandFormatException {
            return null;
        }

        @Override
        public String getValue(ParsedCommandLine args, boolean required) throws CommandFormatException {
            return null;
        }

        @Override
        public boolean isValueComplete(ParsedCommandLine args) throws CommandFormatException {
            return false;
        }

        @Override
        public boolean isValueRequired() {
            return true;
        }

    }
}
