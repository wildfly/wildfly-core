/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.test;


import static org.junit.Assert.assertEquals;

import org.jboss.as.cli.completion.mock.MockCommandContext;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.dmr.ModelNode;
import org.junit.Test;


/**
 *
 * @author Alexey Loubyansky
 */
public class SimplifiedOperationParameterFormatTestCase {

    private final MockCommandContext ctx = new MockCommandContext();
    private final OperationRequestAddress rootAddr = new DefaultOperationRequestAddress();
    private final DefaultCallbackHandler handler = new DefaultCallbackHandler(false);

    @Test
    public void testSimpleList() throws Exception {
        assertEquivalent("[\"a\",\"b\"]", "[a,b]");
    }

    @Test
    public void testSimpleObject() throws Exception {
        assertEquivalent("{\"a\"=>\"b\",\"c\"=>\"d\"}", "{a=b,c=d}");
    }

    @Test
    public void testVeryVerySimpleObject() throws Exception {
        assertEquivalent("{\"a\"=>\"b\"}", "{a=b}");
    }

    @Test
    public void testPropertyList() throws Exception {
        assertEquivalent("[(\"a\"=>\"b\"),(\"c\"=>\"d\")]", "[a=b,c=d]");
    }

    @Test
    public void testMix() throws Exception {
        assertEquivalent("{\"a\"=>\"b\",\"c\"=>[\"d\",\"e\"],\"f\"=>[(\"g\"=>\"h\"),(\"i\"=>\"j\")]}", "{a=b,c=[d,e],f=[g=h,i=j]}");
    }

    protected void assertEquivalent(String dmrParams, String simplifiedParams) throws Exception {
        handler.parseOperation(rootAddr, ":test(test=" + dmrParams + ")", ctx);
        final ModelNode dmrReq = handler.toOperationRequest(ctx);
        handler.parseOperation(rootAddr, ":test(test=" + simplifiedParams + ")", ctx);
        final ModelNode simplifiedReq = handler.toOperationRequest(ctx);
        assertEquals(dmrReq, simplifiedReq);
    }
}
