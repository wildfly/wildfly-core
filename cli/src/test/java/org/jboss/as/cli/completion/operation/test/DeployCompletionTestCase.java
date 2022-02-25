/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.cli.completion.operation.test;

import org.jboss.as.cli.CommandCompleter;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandRegistry;
import org.jboss.as.cli.completion.mock.MockCommandContext;
import org.jboss.as.cli.handlers.DeployHandler;
import org.jboss.as.cli.operation.ParsedOperationRequestHeader;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.operation.impl.ParsedRolloutPlanHeader;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.ParserUtil;
import org.jboss.as.cli.parsing.operation.HeaderListState;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * tests for WFCORE-1570
 *
 * @author Bartosz Spyrko-Smietanko
 */
public class DeployCompletionTestCase {
    private DefaultParsingState headerParsingInitialState;
    final DefaultCallbackHandler headerParsingCallback;

    private MockCommandContext ctx;
    private CommandCompleter cmdCompleter;

    public DeployCompletionTestCase() throws Exception {
        ctx = new MockCommandContext() {
            @Override
            public boolean isDomainMode() {
                return true;
            }
        };

        CommandRegistry cmdRegistry = new CommandRegistry();

        cmdRegistry.registerHandler(new DeployHandler(ctx) {
            @Override
            public boolean isAvailable(CommandContext ctx) {
                return true;
            }
        }, "deploy");

        cmdCompleter = new CommandCompleter(cmdRegistry);

        headerParsingInitialState = new DefaultParsingState("INITIAL_STATE");
        {
            headerParsingInitialState.enterState('{', HeaderListState.INSTANCE);
        }
        headerParsingCallback = new DefaultCallbackHandler();
    }

    @Before
    public void setUp() {
        headerParsingCallback.reset();
    }

    @Test
    public void testCompleteNameArgument() throws Exception {
        ArrayList<String> candidates = complete("deploy --headers={rollout n");

        assertThat("candidates should contain [name=]", candidates, hasItems("name="));
    }

    @Test
    public void testDontCompleteDeprecatedIdArgument() throws Exception {
        ArrayList<String> candidates = complete("deploy --headers={rollout i");

        assertTrue("candidates should be empty", candidates.isEmpty());
    }

    @Test
    public void testDontListDeprecatedIdArgumentWhenNoArgumentsAreStarted() throws Exception {
        ArrayList<String> candidates = complete("deploy --headers={rollout ");

        assertThat("candidates should contain [name=] but not [id=]", candidates, allOf(hasItems("name="), not(hasItems("id="))));
    }

    @Test
    public void testPopulateRolloutHeaderRefFromNameAttribute() throws Exception {
        final Collection<ParsedOperationRequestHeader> headers = parseHeaders("{rollout name=foo}");

        assertHasRolloutHeaderWithRef(headers, "foo");
    }

    @Test
    public void testPopulateRolloutHeaderRefFromDepracatedIdAttribute() throws Exception {
        final Collection<ParsedOperationRequestHeader> headers = parseHeaders("{rollout id=foo}");

        assertHasRolloutHeaderWithRef(headers, "foo");
    }

    private ArrayList<String> complete(String buffer) {
        ArrayList<String> candidates = new ArrayList<>();

        cmdCompleter.complete(ctx, buffer, buffer.length(), candidates);
        return candidates;
    }

    private Collection<ParsedOperationRequestHeader> parseHeaders(String headersArgumentValue) throws CommandFormatException {
        ParserUtil.parse(headersArgumentValue, headerParsingCallback, headerParsingInitialState);

        return headerParsingCallback.getHeaders();
    }

    private static void assertHasRolloutHeaderWithRef(Collection<ParsedOperationRequestHeader> headers, String planRef) {
        assertEquals("Should have one parsed header", 1, headers.size());
        ParsedOperationRequestHeader rolloutHeader = headers.iterator().next();
        assertEquals("The parsed header should be a rollout header", "rollout", rolloutHeader.getName());
        assertEquals("The rollout header has wrong planRef", planRef, ((ParsedRolloutPlanHeader) rolloutHeader).getPlanRef());
    }
}
