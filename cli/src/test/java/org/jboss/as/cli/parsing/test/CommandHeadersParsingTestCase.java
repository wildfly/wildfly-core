/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.completion.mock.MockCommandContext;
import org.jboss.as.cli.impl.HeadersArgumentValueConverter;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.parsing.ParserUtil;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class CommandHeadersParsingTestCase {

    private final MockCommandContext ctx = new MockCommandContext();
    private final DefaultCallbackHandler handler = new DefaultCallbackHandler();
    private final HeadersArgumentValueConverter converter = HeadersArgumentValueConverter.INSTANCE;

    @Test
    public void testSingleHeader() throws Exception {
        final ModelNode headers = converter.fromString(ctx, "{rollback-on-runtime-failure=false}");
        final ModelNode expected = new ModelNode();
        expected.get("rollback-on-runtime-failure").set("false");
        assertEquals(expected, headers);
    }

    @Test
    public void testTwoHeaders() throws Exception {
        final ModelNode headers = converter.fromString(ctx, "{rollback-on-runtime-failure=false;allow-resource-service-restart=true}");
        final ModelNode expected = new ModelNode();
        expected.get("rollback-on-runtime-failure").set("false");
        expected.get("allow-resource-service-restart").set("true");
        assertEquals(expected, headers);
    }

    @Test
    public void testArgumentValueConverter() throws Exception {

        final ModelNode node = converter.fromString(ctx, "{ rollout groupA rollback-across-groups; rollback-on-runtime-failure=false}");

        final ModelNode expectedHeaders = new ModelNode();
        final ModelNode rolloutPlan = expectedHeaders.get(Util.ROLLOUT_PLAN);
        final ModelNode inSeries = rolloutPlan.get(Util.IN_SERIES);

        ModelNode sg = new ModelNode();
        ModelNode group = sg.get(Util.SERVER_GROUP);
        group.get("groupA");
        inSeries.add().set(sg);

        rolloutPlan.get("rollback-across-groups").set("true");

        expectedHeaders.get("rollback-on-runtime-failure").set("false");

        assertEquals(expectedHeaders, node);
    }

    @Test
    public void testArgumentValueConverterWithCustomHeader() throws Exception {

        final ModelNode node = converter.fromString(ctx, "{ foo=\"1 2 3\"; rollback-on-runtime-failure=false}");

        final ModelNode expectedHeaders = new ModelNode();
        final ModelNode foo = expectedHeaders.get("foo");
        foo.set("1 2 3");
        final ModelNode rollback = expectedHeaders.get(Util.ROLLBACK_ON_RUNTIME_FAILURE);
        rollback.set("false");

        assertEquals(expectedHeaders, node);
    }

    @Test
    public void testNonRolloutCompletionCustomHeader() throws Exception {
        parse("read-attribute process-type --headers={rollout main-server-group; rollback-on-runtime-failure=false; allow-resource-service-restart=true;foo=\"1 2 3\"");

        assertFalse(handler.hasAddress());
        assertTrue(handler.hasOperationName());
        assertTrue(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        final String headers = handler.getPropertyValue("--headers");
        assertNotNull(headers);
        final ModelNode node = converter.fromString(ctx, headers);
        assertTrue(node.hasDefined(Util.ROLLOUT_PLAN));
        assertTrue(node.hasDefined(Util.ROLLBACK_ON_RUNTIME_FAILURE));
        assertTrue(node.hasDefined("foo"));
        assertEquals("1 2 3", node.get("foo").asString());
    }

    @Test
    public void testNonRolloutCompletion() throws Exception {
        parse("read-attribute process-type --headers={rollout main-server-group; rollback-on-runtime-failure=false; allow-resource-service-restart=tr");

        assertFalse(handler.hasAddress());
        assertTrue(handler.hasOperationName());
        assertTrue(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        final String headers = handler.getPropertyValue("--headers");
        assertNotNull(headers);
        final ModelNode node = converter.fromString(ctx, headers);
        assertTrue(node.hasDefined(Util.ROLLOUT_PLAN));
        assertTrue(node.hasDefined(Util.ROLLBACK_ON_RUNTIME_FAILURE));
        assertEquals("tr", node.get(Util.ALLOW_RESOURCE_SERVICE_RESTART).asString());
    }

    @Test
    public void testSimpleHeaderCompletion() throws Exception {
        parse(":do{allow-resource-service-restart =");

        assertFalse(handler.hasAddress());
        assertTrue(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        //assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());
        assertTrue(handler.endsOnSeparator());
    }

    protected void parse(String cmd) throws CommandFormatException {
        handler.reset();
        ParserUtil.parse(cmd, handler);
    }
}
