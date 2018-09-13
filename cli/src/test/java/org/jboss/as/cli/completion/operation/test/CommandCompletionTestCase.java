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

package org.jboss.as.cli.completion.operation.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jboss.as.cli.CommandCompleter;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandRegistry;
import org.jboss.as.cli.completion.mock.MockCommandContext;
import org.jboss.as.cli.completion.mock.MockNode;
import org.jboss.as.cli.completion.mock.MockOperation;
import org.jboss.as.cli.completion.mock.MockOperationCandidatesProvider;
import org.junit.Test;

/**
 * @author wangc
 *
 */
public class CommandCompletionTestCase {

    private CommandRegistry cmdRegistry;
    private CommandCompleter cmdCompleter;
    private MockCommandContext ctx;

    private static final String OPERATION1 = "operation-no-properties";
    private static final String OPERATION2 = "operation-property-a";
    private static final String OPERATION3 = "operation-properties-a-b";

    private static final String OPERATION = ":operation-p";
    private static final String OPERATION_MULTILINES_FULL = ":opera\\ntion-p";
    private static final String OPERATION_MULTILINES_NEWLINE = "tion-p";

    public CommandCompletionTestCase() {

        MockNode root = new MockNode("root");

        MockOperation op = new MockOperation(OPERATION1);
        root.addOperation(op);

        op = new MockOperation(OPERATION2);
        root.addOperation(op);

        op = new MockOperation(OPERATION3);
        root.addOperation(op);

        ctx = new MockCommandContext();
        ctx.setOperationCandidatesProvider(new MockOperationCandidatesProvider(root));

        cmdRegistry = new CommandRegistry();
        cmdCompleter = new CommandCompleter(cmdRegistry);
    }

    @Test
    public void testSelectedCandidates() {
        // usual case, test cursor is next to "operation-p", it should find 2 candidates
        List<String> candidates = fetchCandidates(OPERATION, OPERATION.length(), false).getCandidates();
        assertNotNull(candidates);
        assertEquals(Arrays.asList(OPERATION3, OPERATION2), candidates);
    }

    @Test
    public void testSelectedCandidatesCursorMoveLeft() {
        // test cursor moves to left next to "operation", it should find 3 candidates
        List<String> candidates = fetchCandidates(OPERATION, 9, false).getCandidates();
        assertNotNull(candidates);
        assertEquals(Arrays.asList(OPERATION1, OPERATION3, OPERATION2), candidates);
    }

    @Test
    public void testSelectedCandidatesCursorMoveRight() {
        // test cursor moves to way right to "operation-p", it should find 2 candidates
        List<String> candidates = fetchCandidates(OPERATION, OPERATION.length() + 10, false).getCandidates();
        assertNotNull(candidates);
        assertEquals(Arrays.asList(OPERATION3, OPERATION2), candidates);
    }

    @Test
    public void testSelectedCandidatesWithMultipleLines() {
        // test cursor moves to new line in multiples lines context, it should find 2 candidates
        // The cursor value must be at the end of last line, this is how aesh completion set
        // the cursor. Eveything after the cursor is now not taken into account.
        List<String> candidates = fetchCandidates(OPERATION_MULTILINES_FULL, 12, true).getCandidates();
        assertNotNull(candidates);
        assertEquals(Arrays.asList(OPERATION3, OPERATION2), candidates);
    }

    @Test
    public void testSelectedCandidatesWithMultipleLinesCursorMoveLeft() {
        // test cursor moves to left in multiples lines context, it should find 3 candidates
        // The cursor value must be at the end of last line, this is how aesh completion set
        // the cursor. Eveything after the cursor is now not taken into account.
        List<String> candidates = fetchCandidates(OPERATION_MULTILINES_FULL, 7, true).getCandidates();
        assertNotNull(candidates);
        assertEquals(Arrays.asList(OPERATION1, OPERATION3, OPERATION2), candidates);
    }

    @Test
    public void testSelectedCandidatesWithMultipleLinesCursorMoveRight() {
        // test cursor moves to left in multiples lines context, it should find 2 candidates
        List<String> candidates = fetchCandidates(OPERATION_MULTILINES_FULL, OPERATION_MULTILINES_FULL.length() + 10, true).getCandidates();
        assertNotNull(candidates);
        assertEquals(Arrays.asList(OPERATION3, OPERATION2), candidates);
    }

    @Test
    public void testTrailingSpacesAtEnd() {
        //spaces plus common prefix - ":   operation-p[TAB]"
        String prefix = ":   ";
        String buffer = prefix + OPERATION.substring(1);
        FetchResult result = fetchCandidates(buffer, buffer.length(), false);
        assertNotNull(result.getCandidates());
        assertEquals(Arrays.asList(OPERATION3, OPERATION2), result.getCandidates());
        assertEquals(prefix.length(), result.getPosition());
    }

    @Test
    public void testTrailingSpacesWithSuffix() {
        //operation - prefix - non empty string = ":   operation-p[TAB]()"
        String prefix = ":   ";
        String buffer = prefix + OPERATION.substring(1) + "()";
        FetchResult result = fetchCandidates(buffer, buffer.length() - 2, false);
        assertNotNull(result.getCandidates());
        assertEquals(Arrays.asList(OPERATION3, OPERATION2), result.getCandidates());
        assertEquals(prefix.length(), result.getPosition());
    }

    protected FetchResult fetchCandidates(String buffer, int cursor, boolean multiLines) {
        ArrayList<String> candidates = new ArrayList<String>();
        int position = 0;
        try {
            ctx.parseCommandLine(buffer, false);
        } catch (CommandFormatException e) {
            return new FetchResult(Collections.emptyList(), 0);
        }
        if (multiLines) {
            position = cmdCompleter.complete(ctx, OPERATION_MULTILINES_NEWLINE, cursor, candidates);
        } else {
            position = cmdCompleter.complete(ctx, buffer, cursor, candidates);
        }

        return new FetchResult(candidates, position);
    }

    private static class FetchResult {
        private final List<String> candidates;
        private final int position;

        public FetchResult(List<String> candidates, int position) {
            this.candidates = candidates;
            this.position = position;
        }

        public List<String> getCandidates() {
            return candidates;
        }

        public int getPosition() {
            return position;
        }
    }

}
