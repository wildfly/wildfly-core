/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.completion.address.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.completion.mock.MockCommandContext;
import org.jboss.as.cli.completion.mock.MockNode;
import org.jboss.as.cli.completion.mock.MockOperationCandidatesProvider;
import org.jboss.as.cli.operation.OperationRequestCompleter;


/**
*
* @author Alexey Loubyansky
*/
public class AbstractAddressCompleterTest {

    protected MockCommandContext ctx;
    protected OperationRequestCompleter completer;
    protected MockNode root = new MockNode("root");

    public AbstractAddressCompleterTest() {
        super();
        init();
    }

    protected void init() {
        ctx = new MockCommandContext();
        ctx.setOperationCandidatesProvider(new MockOperationCandidatesProvider(root));
        completer = new OperationRequestCompleter();
    }

    protected List<String> fetchCandidates(String buffer) {
        List<String> candidates = new ArrayList<String>();
        try {
            ctx.parseCommandLine(buffer, false);
        } catch (CommandFormatException e) {
//            System.out.println(ctx.getPrefixFormatter().format(ctx.getPrefix()) + ", '" + buffer + "'");
//            e.printStackTrace();
            return Collections.emptyList();
        }
        completer.complete(ctx, buffer, 0, candidates);
        return candidates;
    }

    protected MockNode addRoot(String name) {
        return root.addChild(name);
    }

    protected MockNode removeRoot(String name) {
        return root.remove(name);
    }
}