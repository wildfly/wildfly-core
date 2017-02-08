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

package org.jboss.as.cli.impl;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.completion.mock.MockCommandContext;
import org.jboss.as.cli.handlers.CommandHandlerWithArguments;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Bartosz Spyrko-Smietanko
 */
public class CommandArgumentTestCase {
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
    final MockCommandContext ctx = new MockCommandContext();

    @Test
    public void shouldAllowToAppearNextIfTheNameIsStarted() throws Exception {
        final ArgumentWithValue arg = new ArgumentWithValue(aHandler, "--foo");

        ctx.parseCommandLine("bar --fo", false);

        assertTrue(arg.canAppearNext(ctx));
    }

    @Test
    public void shouldAllowToAppearNextIfIndexIsCorect() throws Exception {
        final ArgumentWithValue arg = new ArgumentWithValue(aHandler, 0, "--foo");

        ctx.parseCommandLine("bar ", false);

        assertTrue(arg.canAppearNext(ctx));
    }

    @Test
    public void shouldAllowToAppearNextIfIndexIsCorectAndCommandIsExclusive() throws Exception {
        final ArgumentWithValue arg = new ArgumentWithValue(aHandler, 0, "--foo");
        arg.setExclusive(true);

        ctx.parseCommandLine("bar ", false);

        assertTrue(arg.canAppearNext(ctx));
    }

    @Test
    public void shouldNotAllowToIndexedArgToAppearNextIfPreviosArgumentForbitsIt() throws Exception {
        final ArgumentWithoutValue arg1 = new ArgumentWithoutValue(aHandler, 0, "exclusive");
        final ArgumentWithValue arg2 = new ArgumentWithValue(aHandler, 1, "--foo");
        arg2.addCantAppearAfter(arg1);

        ctx.parseCommandLine("bar exclusive a", false);
        System.out.println(arg2.getResolvedValue(ctx.getParsedCommandLine(), false));

        System.out.println(arg2.getValue(ctx.getParsedCommandLine(), false));

        assertFalse(arg2.canAppearNext(ctx));
    }

    @Test
    public void shouldAllowToAppearNextIfTheNameIsCompletedButValueIsNot() throws Exception {
        final ArgumentWithValue arg = new ArgumentWithValue(aHandler, "--foo");

        ctx.parseCommandLine("bar --foo=a", false);

        assertTrue(arg.canAppearNext(ctx));
    }

    @Test
    public void shouldAllowToAppearNextIfIndexIsCorrectAndValueIsIncomplete() throws Exception {
        final ArgumentWithValue arg = new ArgumentWithValue(aHandler, 0, "--foo");

        ctx.parseCommandLine("bar a", false);

        assertTrue(arg.canAppearNext(ctx));
    }

    @Test
    public void shouldAllowToAppearNextIfIndexIsCorrectAndCommandIsExclusiveAndValueIsIncomplete() throws Exception {
        final ArgumentWithValue arg = new ArgumentWithValue(aHandler, 0, "--foo");
        arg.setExclusive(true);

        ctx.parseCommandLine("bar a", false);

        assertTrue(arg.canAppearNext(ctx));
    }

    @Test
    public void shouldNotAllowToAppearNextIfTheArgumentIsAlreadyPresent() throws Exception {
        final ArgumentWithValue arg = new ArgumentWithValue(aHandler, "--foo");

        ctx.parseCommandLine("bar --foo=bar ", false);

        assertFalse(arg.canAppearNext(ctx));
    }

    @Test
    public void shouldNotAllowToAppearNextIfTheArgumentIsAlreadyPresentAndAnotherStarted() throws Exception {
        final ArgumentWithValue arg = new ArgumentWithValue(aHandler, "--foo");

        ctx.parseCommandLine("bar --foo=bar --dur=a", false);

        assertFalse(arg.canAppearNext(ctx));
    }

    @Test
    public void shouldNotAllowToAppearNextIfIndexIsWrong() throws Exception {
        final ArgumentWithValue arg = new ArgumentWithValue(aHandler, 0, "--foo");

        ctx.parseCommandLine("bar a ", false);

        assertFalse(arg.canAppearNext(ctx));
    }

    @Test
    public void shouldNotAllowArgumentTwice() throws Exception {
        final ArgumentWithoutValue arg = new ArgumentWithoutValue(aHandler, 0, "--foo");

        ctx.parseCommandLine("bar --foo ", false);

        assertFalse(arg.canAppearNext(ctx));
    }

    @Test
    public void shouldNotAllowArgumentIfRequiredPrecedingArgIsNotPresent() throws Exception {
        final ArgumentWithValue arg0 = new ArgumentWithValue(aHandler, -1, "--bar");
        final ArgumentWithValue arg1 = new ArgumentWithValue(aHandler, -1, "--foo");
        arg1.addRequiredPreceding(arg0);

        ctx.parseCommandLine("bar --foo=", false);

        assertFalse(arg1.canAppearNext(ctx));
    }

    @Test
    public void shouldAllowArgumentIfRequiredPrecedingArgIsPresent() throws Exception {
        final ArgumentWithValue arg0 = new ArgumentWithValue(aHandler, -1, "--bar");
        final ArgumentWithValue arg1 = new ArgumentWithValue(aHandler, -1, "--foo");
        arg1.addRequiredPreceding(arg0);

        ctx.parseCommandLine("bar --bar=bar --foo=", false);

        assertTrue(arg1.canAppearNext(ctx));
    }
}
