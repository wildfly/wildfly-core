/*
Copyright 2016 Red Hat, Inc.

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
package org.jboss.as.cli.completion.operation.test;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.completion.mock.MockCommandContext;
import org.jboss.as.cli.completion.mock.MockNode;
import org.jboss.as.cli.completion.mock.MockOperation;
import org.jboss.as.cli.completion.mock.MockOperationCandidatesProvider;
import org.jboss.as.cli.operation.OperationRequestCompleter;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PropertiesWithIndexCompletionTestCase {

    private MockCommandContext ctx;
    private OperationRequestCompleter completer;

    public PropertiesWithIndexCompletionTestCase() {
        MockNode root = new MockNode("root");

        MockOperation op = new MockOperation("operation-no-properties");
        root.addOperation(op);

        op = new MockOperation("operation-properties-one-two-three");
        op.setProperties(Arrays.asList(new MockOperation.Property("one", 1)));
        root.addOperation(op);

        ctx = new MockCommandContext();
        ctx.setOperationCandidatesProvider(new MockOperationCandidatesProvider(root));
        completer = new OperationRequestCompleter();
    }

    @Test
    public void testAllCandidates() {
        List<String> candidates = fetchCandidates(":operation-properties-one-two-three(");
        assertEquals(Arrays.asList("one"), candidates);
    }

    @Test
    public void testOneCandidate() {
        List<String> candidates = fetchCandidates(":operation-properties-one-two-three(o");
        assertEquals(Arrays.asList("one"), candidates);
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
