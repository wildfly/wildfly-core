/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.completion.operation.test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.completion.mock.MockCommandContext;
import org.jboss.as.cli.completion.mock.MockNode;
import org.jboss.as.cli.completion.mock.MockOperation;
import org.jboss.as.cli.completion.mock.MockOperationCandidatesProvider;
import org.jboss.as.cli.operation.OperationRequestCompleter;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class OperationNameCompletionTestCase {

    private MockCommandContext ctx;
    private OperationRequestCompleter completer;

    public OperationNameCompletionTestCase() {

        MockNode root = new MockNode("root");

        MockOperation op = new MockOperation("operation-no-properties");
        root.addOperation(op);

        op = new MockOperation("operation-property-a");
        root.addOperation(op);

        op = new MockOperation("operation-properties-a-b");
        root.addOperation(op);

        ctx = new MockCommandContext();
        ctx.setOperationCandidatesProvider(new MockOperationCandidatesProvider(root));
        completer = new OperationRequestCompleter();
    }

    @Test
    public void testAllCandidates() {

        List<String> candidates = fetchCandidates(":");
        assertNotNull(candidates);
        assertEquals(Arrays.asList("operation-no-properties", "operation-properties-a-b", "operation-property-a"), candidates);
    }

    @Test
    public void testSelectedCandidates() {

        List<String> candidates = fetchCandidates(":operation-p");
        assertNotNull(candidates);
        assertEquals(Arrays.asList("operation-properties-a-b", "operation-property-a"), candidates);
    }

    @Test
    public void testNoMatch() {

        List<String> candidates = fetchCandidates(":no-match");
        assertNotNull(candidates);
        assertEquals(Arrays.asList(), candidates);
    }

    protected List<String> fetchCandidates(String buffer) {
        ArrayList<String> candidates = new ArrayList<String>();
        try {
            ctx.parseCommandLine(buffer, false);
        } catch (CommandFormatException e) {
            return Collections.emptyList();
        }
        completer.complete(ctx, buffer, 0, candidates);
        return candidates;
    }
}
