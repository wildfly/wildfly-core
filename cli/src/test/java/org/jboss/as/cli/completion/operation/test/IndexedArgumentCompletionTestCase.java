/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.cli.completion.operation.test;

import org.jboss.as.cli.CommandArgument;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.completion.mock.MockCommandContext;
import org.jboss.as.cli.handlers.CommandHandlerWithArguments;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.operation.OperationCandidatesProvider;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.OperationRequestCompleter;
import org.jboss.as.cli.operation.OperationRequestHeader;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author Bartosz Spyrko-Smietanko
 */
public class IndexedArgumentCompletionTestCase {
    private OperationRequestCompleter operationRequestCompleter = new OperationRequestCompleter();
    private MockCommandContext ctx = new MockCommandContext();
    private CommandHandlerWithArguments aHandler = new CommandHandlerWithArguments() {
        @Override
        public boolean isAvailable(CommandContext ctx) {
            return false;
        }

        @Override
        public boolean isBatchMode(CommandContext ctx) {
            return false;
        }

        @Override
        public void handle(CommandContext ctx) throws CommandLineException {

        }
    };

    @Test
    public void doNotSuggestIndexedArgumentIfInWrongPosition() throws Exception {
        final CommandArgument arg0 = argument("arg0", 23);

        assertEquals("arg0 should be ignored",
                Collections.emptyList(), complete("op ", argumentProvider(arg0)));
    }

    @Test
    public void suggestIndexedArgumentIfThePositionIsCorrect() throws Exception {
        final CommandArgument arg0 = argument("arg0", 0);
        final CommandArgument arg1 = argument("arg1", 1);

        assertEquals("arg0 should be suggested",
                Arrays.asList("arg0"), complete("op ", argumentProvider(arg0, arg1)));

        assertEquals("arg1 should be suggested",
                Arrays.asList("arg1"), complete("op arg0 ", argumentProvider(arg0, arg1)));

        assertEquals("arg0 should be suggested",
                Arrays.asList("arg0"), complete("op --foo=bar ", argumentProvider(arg0, arg1)));
    }

    @Test
    public void suggestIndexedArgumentIfInWrongPositionAndValueIsStarted() throws Exception {
        final CommandArgument arg0 = argument("arg0", 0);

        assertEquals("arg0 should be suggested",
                Arrays.asList("arg0"), complete("op a", argumentProvider(arg0)));
    }

    @Test
    public void doNotSuggestIndexedArgumentIfInWrongPositionAndValueIsStarted() throws Exception {
        final CommandArgument arg0 = argument("arg0", 23);

        assertEquals("arg0 should be ignored",
                Collections.emptyList(), complete("op a", argumentProvider(arg0)));
    }

    @Test
    public void suggestIndexedArgumentOnlyIfItCanAppearInGivenContext() throws Exception {
        final CommandArgument arg0 = argument("arg0", 0);
        final ArgumentWithValue arg1 = argument("arg1", 1);
        final CommandArgument arg2 = argument("arg2", 1);
        arg1.addCantAppearAfter(arg0);

        assertEquals("arg1 should be ignored",
                Arrays.asList("arg2"), complete("op arg0 a", argumentProvider(arg1, arg2)));
    }

    @Test
    public void pickFirstMatchingArgumentIfTwoIndexedArgumentAreAvailable() throws Exception {
        final CommandArgument arg1 = argument("arg1", 0);
        final CommandArgument arg2 = argument("arg2", 0);

        assertEquals("The first completer of argument list should be used",
                Arrays.asList("arg1"), complete("op a", argumentProvider(arg1, arg2)));
    }

    @Test
    public void alwaysPickArgumentWithMaxValueIndex() throws Exception {
        final CommandArgument arg1 = argument("arg1", 0);
        final CommandArgument arg2 = argument("arg2", Integer.MAX_VALUE);

        assertEquals("The first completer of argument list should be used",
                Arrays.asList("arg2"), complete("op a ", argumentProvider(arg1, arg2)));
    }

    @Test
    public void alwaysPickArgumentWithMaxValueIndexWhenNoPropertiesAreSpecified() throws Exception {
        final CommandArgument arg1 = argument("arg1", 0);
        final CommandArgument arg2 = argument("arg2", Integer.MAX_VALUE);

        assertEquals("The first completer of argument list should be used",
                Arrays.asList("arg1", "arg2"), complete("op ", argumentProvider(arg1, arg2)));
    }

    private List<String> complete(String buffer, OperationCandidatesProvider candidatesProvider) throws CommandFormatException {
        List<String> result = new ArrayList<>();
        ctx.parseCommandLine(buffer, false);
        operationRequestCompleter.complete(ctx, candidatesProvider, buffer, buffer.length(), result);
        return result;
    }

    private ArgumentWithValue argument(final String name, final int index) {
        return new ArgumentWithValue(aHandler, new CommandLineCompleter() {

                @Override
                public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
                    candidates.add(name);
                    return 0;
                }
            }, index, "--" + name);
    }

    private OperationCandidatesProvider argumentProvider(CommandArgument ... args) {
        return new OperationCandidatesProvider() {

                @Override
                public Collection<String> getNodeNames(CommandContext ctx, OperationRequestAddress prefix) {
                    return null;
                }

                @Override
                public Collection<String> getNodeTypes(CommandContext ctx, OperationRequestAddress prefix) {
                    return null;
                }

                @Override
                public Collection<String> getOperationNames(CommandContext ctx, OperationRequestAddress prefix) {
                    return null;
                }

                @Override
                public Collection<CommandArgument> getProperties(CommandContext ctx, String operationName, OperationRequestAddress address) {
                    return Arrays.asList(args);

                }

                @Override
                public Map<String, OperationRequestHeader> getHeaders(CommandContext ctx) {
                    return null;
                }
            };
    }
}
