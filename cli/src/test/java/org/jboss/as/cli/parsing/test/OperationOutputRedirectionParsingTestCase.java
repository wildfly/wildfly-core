/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


import org.jboss.as.cli.operation.CommandLineParser;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestParser;
import org.junit.Test;


/**
 *
 * @author Alexey Loubyansky
 */
public class OperationOutputRedirectionParsingTestCase extends BaseStateParserTest {

    private CommandLineParser parser = DefaultOperationRequestParser.INSTANCE;

    @Test
    public void testOperationNameOnly() throws Exception {
        DefaultCallbackHandler handler = new DefaultCallbackHandler();

        parseOperation(":  read-resource > cli.log", handler);

        assertFalse(handler.hasAddress());
        assertTrue(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        assertEquals("read-resource", handler.getOperationName());
        assertTrue("No operator", handler.hasOperator());
    }

    @Test
    public void testOperationWithProps() throws Exception {
        DefaultCallbackHandler handler = new DefaultCallbackHandler();

        parseOperation(":  read-resource (recursive=true) > cli.log", handler);

        assertFalse(handler.hasAddress());
        assertTrue(handler.hasOperationName());
        assertTrue(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.hasHeaders());
        assertFalse(handler.isRequestComplete());

        assertEquals("read-resource", handler.getOperationName());
        assertTrue("No operator", handler.hasOperator());
        assertEquals("cli.log", handler.getOutputTarget());
    }

    @Test
    public void testOperationWithHeaders() throws Exception {
        DefaultCallbackHandler handler = new DefaultCallbackHandler();

        parseOperation(":  read-resource (recursive=true) {header_name=header_value} > cli.log", handler);

        assertFalse(handler.hasAddress());
        assertTrue(handler.hasOperationName());
        assertTrue(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertTrue(handler.hasHeaders());
        assertTrue(handler.isRequestComplete());

        assertEquals("read-resource", handler.getOperationName());
        assertTrue("No operator", handler.hasOperator());
        assertEquals("cli.log", handler.getOutputTarget());
    }

    protected void parseOperation(String operation, DefaultCallbackHandler handler)
            throws OperationFormatException {
        parser.parse(operation, handler);
        //ParsingUtil.parseOperation(operation, 0, handler);
    }
}
