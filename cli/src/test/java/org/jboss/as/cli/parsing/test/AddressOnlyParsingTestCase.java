/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.operation.CommandLineParser;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.OperationRequestAddress.Node;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestParser;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class AddressOnlyParsingTestCase {

    private CommandLineParser parser = DefaultOperationRequestParser.INSTANCE;

    @Test
    public void testNodeTypeOnly() throws Exception {
        DefaultCallbackHandler handler = new DefaultCallbackHandler();

        parser.parse("subsystem", handler);

        assertTrue(handler.hasAddress());
        assertFalse(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        OperationRequestAddress address = handler.getAddress();
        assertTrue(address.endsOnType());
        Iterator<Node> i = address.iterator();
        assertTrue(i.hasNext());
        Node node = i.next();
        assertNotNull(node);
        assertEquals("subsystem", node.getType());
        assertNull(node.getName());
        assertFalse(i.hasNext());
    }

    @Test
    public void testNodeTypeNameSeparator() throws Exception {
        DefaultCallbackHandler handler = new DefaultCallbackHandler();

        parser.parse("subsystem=", handler);

        assertTrue(handler.hasAddress());
        assertFalse(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertTrue(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        OperationRequestAddress address = handler.getAddress();
        assertTrue(address.endsOnType());
        Iterator<Node> i = address.iterator();
        assertTrue(i.hasNext());
        Node node = i.next();
        assertNotNull(node);
        assertEquals("subsystem", node.getType());
        assertNull(node.getName());
        assertFalse(i.hasNext());
    }

    @Test
    public void testNodeTypeOnlyWithPrefixNode() throws Exception {
        OperationRequestAddress prefix = new DefaultOperationRequestAddress();
        prefix.toNode("a", "b");
        DefaultCallbackHandler handler = new DefaultCallbackHandler(prefix);

        parser.parse("subsystem", handler);

        assertTrue(handler.hasAddress());
        assertFalse(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        OperationRequestAddress address = handler.getAddress();
        assertTrue(address.endsOnType());
        Iterator<Node> i = address.iterator();
        assertTrue(i.hasNext());
        Node node = i.next();
        assertNotNull(node);
        assertEquals("a", node.getType());
        assertEquals("b", node.getName());
        assertTrue(i.hasNext());
        node = i.next();
        assertNotNull(node);
        assertEquals("subsystem", node.getType());
        assertNull(node.getName());
        assertFalse(i.hasNext());
    }

    @Test
    public void testChildNodeTypeOnlyWithPrefixNode() throws Exception {
        OperationRequestAddress prefix = new DefaultOperationRequestAddress();
        prefix.toNode("a", "b");
        DefaultCallbackHandler handler = new DefaultCallbackHandler(prefix);

        parser.parse("./subsystem", handler);

        assertTrue(handler.hasAddress());
        assertFalse(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        OperationRequestAddress address = handler.getAddress();
        assertTrue(address.endsOnType());
        Iterator<Node> i = address.iterator();
        assertTrue(i.hasNext());
        Node node = i.next();
        assertNotNull(node);
        assertEquals("a", node.getType());
        assertEquals("b", node.getName());
        assertTrue(i.hasNext());
        node = i.next();
        assertNotNull(node);
        assertEquals("subsystem", node.getType());
        assertNull(node.getName());
        assertFalse(i.hasNext());
    }

    @Test
    public void testRootNodeTypeOnlyWithPrefixNode() throws Exception {
        OperationRequestAddress prefix = new DefaultOperationRequestAddress();
        prefix.toNode("a", "b");
        DefaultCallbackHandler handler = new DefaultCallbackHandler(prefix);

        parser.parse("/subsystem", handler);

        assertTrue(handler.hasAddress());
        assertFalse(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        OperationRequestAddress address = handler.getAddress();
        assertTrue(address.endsOnType());
        Iterator<Node> i = address.iterator();
        assertTrue(i.hasNext());
        Node node = i.next();
        assertNotNull(node);
        assertEquals("subsystem", node.getType());
        assertNull(node.getName());
        assertFalse(i.hasNext());
    }

    @Test
    public void testNodeNameOnly() throws Exception {
        OperationRequestAddress prefix = new DefaultOperationRequestAddress();
        prefix.toNodeType("a");
        DefaultCallbackHandler handler = new DefaultCallbackHandler(prefix);

        parser.parse("b", handler);

        assertTrue(handler.hasAddress());
        assertFalse(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        OperationRequestAddress address = handler.getAddress();
        assertFalse(address.endsOnType());
        Iterator<Node> i = address.iterator();
        assertTrue(i.hasNext());
        Node node = i.next();
        assertNotNull(node);
        assertEquals("a", node.getType());
        assertEquals("b", node.getName());
        assertFalse(i.hasNext());
    }

    @Test
    public void testNodeNameOnlyWithNodeSeparator() throws Exception {
        OperationRequestAddress prefix = new DefaultOperationRequestAddress();
        prefix.toNodeType("a");
        DefaultCallbackHandler handler = new DefaultCallbackHandler(prefix);

        parser.parse("b/", handler);

        assertTrue(handler.hasAddress());
        assertFalse(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertTrue(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        OperationRequestAddress address = handler.getAddress();
        assertFalse(address.endsOnType());
        Iterator<Node> i = address.iterator();
        assertTrue(i.hasNext());
        Node node = i.next();
        assertNotNull(node);
        assertEquals("a", node.getType());
        assertEquals("b", node.getName());
        assertFalse(i.hasNext());
    }

    @Test
    public void testOneNode() throws Exception {
        DefaultCallbackHandler handler = new DefaultCallbackHandler();

        parser.parse("subsystem=logging", handler);

        assertTrue(handler.hasAddress());
        assertFalse(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        OperationRequestAddress address = handler.getAddress();
        assertFalse(address.endsOnType());
        Iterator<Node> i = address.iterator();
        assertTrue(i.hasNext());
        Node node = i.next();
        assertNotNull(node);
        assertEquals("subsystem", node.getType());
        assertEquals("logging", node.getName());
        assertFalse(i.hasNext());
    }

    @Test
    public void testColonAndSlashInTheNodeName() throws Exception {
        DefaultCallbackHandler handler = new DefaultCallbackHandler();

        parser.parse("data-source=\"java:/H2DS\"", handler);

        assertTrue(handler.hasAddress());
        assertFalse(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        OperationRequestAddress address = handler.getAddress();
        assertFalse(address.endsOnType());
        Iterator<Node> i = address.iterator();
        assertTrue(i.hasNext());
        Node node = i.next();
        assertNotNull(node);
        assertEquals("data-source", node.getType());
        assertEquals("java:/H2DS", node.getName());
        assertFalse(i.hasNext());
    }

    @Test
    public void testOneNodeWithNodeSeparator() throws Exception {
        DefaultCallbackHandler handler = new DefaultCallbackHandler();

        parser.parse("subsystem=logging/", handler);

        assertTrue(handler.hasAddress());
        assertFalse(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertTrue(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        OperationRequestAddress address = handler.getAddress();
        assertFalse(address.endsOnType());
        Iterator<Node> i = address.iterator();
        assertTrue(i.hasNext());
        Node node = i.next();
        assertNotNull(node);
        assertEquals("subsystem", node.getType());
        assertEquals("logging", node.getName());
        assertFalse(i.hasNext());
    }

    @Test
    public void testEndsOnType() throws Exception {
        DefaultCallbackHandler handler = new DefaultCallbackHandler();

        parser.parse("a=b/c", handler);

        assertTrue(handler.hasAddress());
        assertFalse(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        OperationRequestAddress address = handler.getAddress();
        assertTrue(address.endsOnType());
        Iterator<Node> i = address.iterator();
        assertTrue(i.hasNext());
        Node node = i.next();
        assertNotNull(node);
        assertEquals("a", node.getType());
        assertEquals("b", node.getName());
        assertTrue(i.hasNext());
        node = i.next();
        assertNotNull(node);
        assertEquals("c", node.getType());
        assertNull(node.getName());
        assertFalse(i.hasNext());
    }

    @Test
    public void testNodeWithPrefix() throws Exception {
        OperationRequestAddress prefix = new DefaultOperationRequestAddress();
        prefix.toNode("a", "b");
        DefaultCallbackHandler handler = new DefaultCallbackHandler(prefix);

        parser.parse("c=d", handler);

        assertTrue(handler.hasAddress());
        assertFalse(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        OperationRequestAddress address = handler.getAddress();
        assertFalse(address.endsOnType());
        Iterator<Node> i = address.iterator();
        assertTrue(i.hasNext());
        Node node = i.next();
        assertNotNull(node);
        assertEquals("a", node.getType());
        assertEquals("b", node.getName());
        assertTrue(i.hasNext());
        node = i.next();
        assertNotNull(node);
        assertEquals("c", node.getType());
        assertEquals("d", node.getName());
        assertFalse(i.hasNext());
    }

    @Test
    public void testRootOnly() throws Exception {

        OperationRequestAddress prefix = new DefaultOperationRequestAddress();
        prefix.toNode("a", "b");
        DefaultCallbackHandler handler = new DefaultCallbackHandler(prefix);

        parser.parse("/", handler);

        assertTrue(handler.hasAddress());
        assertFalse(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        // root is also a separator
        assertTrue(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        OperationRequestAddress address = handler.getAddress();
        assertFalse(address.endsOnType());
        Iterator<Node> i = address.iterator();
        assertFalse(i.hasNext());
    }

    @Test
    public void testRootInCombination() throws Exception {

        OperationRequestAddress prefix = new DefaultOperationRequestAddress();
        prefix.toNode("a", "b");
        DefaultCallbackHandler handler = new DefaultCallbackHandler(prefix);

        //parser.parse("c=d,~,e=f", handler);
        parser.parse("/e=f", handler);

        assertTrue(handler.hasAddress());
        assertFalse(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        OperationRequestAddress address = handler.getAddress();
        assertFalse(address.endsOnType());
        Iterator<Node> i = address.iterator();
        assertTrue(i.hasNext());
        Node node = i.next();
        assertNotNull(node);
        assertEquals("e", node.getType());
        assertEquals("f", node.getName());
        assertFalse(i.hasNext());
    }

    @Test
    public void testParentOnly() throws Exception {

        OperationRequestAddress prefix = new DefaultOperationRequestAddress();
        prefix.toNode("a", "b");
        DefaultCallbackHandler handler = new DefaultCallbackHandler(prefix);

        parser.parse("..", handler);

        assertTrue(handler.hasAddress());
        assertFalse(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        OperationRequestAddress address = handler.getAddress();
        assertFalse(address.endsOnType());
        Iterator<Node> i = address.iterator();
        assertFalse(i.hasNext());
    }

    @Test
    public void testParentInCombination() throws Exception {

        DefaultCallbackHandler handler = new DefaultCallbackHandler();

        parser.parse("c=d/../e=f", handler);

        assertTrue(handler.hasAddress());
        assertFalse(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        OperationRequestAddress address = handler.getAddress();
        assertFalse(address.endsOnType());
        Iterator<Node> i = address.iterator();
        assertTrue(i.hasNext());
        Node node = i.next();
        assertNotNull(node);
        assertEquals("e", node.getType());
        assertEquals("f", node.getName());
        assertFalse(i.hasNext());
    }

    @Test
    public void testToTypeOnly() throws Exception {

        OperationRequestAddress prefix = new DefaultOperationRequestAddress();
        prefix.toNode("a", "b");
        DefaultCallbackHandler handler = new DefaultCallbackHandler(prefix);

        parser.parse(".type", handler);

        assertTrue(handler.hasAddress());
        assertFalse(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        OperationRequestAddress address = handler.getAddress();
        assertTrue(address.endsOnType());
        Iterator<Node> i = address.iterator();
        assertTrue(i.hasNext());
        Node node = i.next();
        assertNotNull(node);
        assertEquals("a", node.getType());
        assertNull(node.getName());
    }

    @Test
    public void testToTypeInCombination() throws Exception {

        OperationRequestAddress prefix = new DefaultOperationRequestAddress();
        prefix.toNodeType("a");
        DefaultCallbackHandler handler = new DefaultCallbackHandler(prefix);

        parser.parse("b/.type/c", handler);

        assertTrue(handler.hasAddress());
        assertFalse(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertFalse(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        OperationRequestAddress address = handler.getAddress();
        assertFalse(address.endsOnType());
        Iterator<Node> i = address.iterator();
        assertTrue(i.hasNext());
        Node node = i.next();
        assertNotNull(node);
        assertEquals("a", node.getType());
        assertEquals("c", node.getName());
    }

    public void testEndsOnSlashWhichIsPartOfName() throws Exception {

        DefaultCallbackHandler handler = new DefaultCallbackHandler();
        parser.parse("/subsystem=mail/mail-session=java\\:\\/", handler);

        OperationRequestAddress address = handler.getAddress();
        assertNotNull(address);
        Iterator<Node> nodes = address.iterator();
        assertTrue(nodes.hasNext());
        Node node = nodes.next();
        assertEquals("subsystem", node.getType());
        assertEquals("mail", node.getName());

        assertTrue(nodes.hasNext());
        node = nodes.next();
        assertEquals("mail-session", node.getType());
        assertEquals("java:/", node.getName());

        assertFalse(handler.endsOnNodeSeparator());
    }

    @Test
    public void testDataSourceName() throws Exception {

        DefaultCallbackHandler handler = new DefaultCallbackHandler();

        parser.parse("/subsystem=datasources/data-source=java\\:\\/H2DS", handler);

        OperationRequestAddress address = handler.getAddress();
        assertNotNull(address);
        Iterator<Node> nodes = address.iterator();
        assertTrue(nodes.hasNext());
        Node node = nodes.next();
        assertEquals("subsystem", node.getType());
        assertEquals("datasources", node.getName());

        assertTrue(nodes.hasNext());
        node = nodes.next();
        assertEquals("data-source", node.getType());
        assertEquals("java:/H2DS", node.getName());
    }

    @Test
    public void testRootCharInTheMiddle() throws Exception {

        OperationRequestAddress prefix = new DefaultOperationRequestAddress();
        prefix.toNode("a", "b");
        DefaultCallbackHandler handler = new DefaultCallbackHandler(prefix);

        parser.parse("/", handler);

        assertTrue(handler.hasAddress());
        assertFalse(handler.hasOperationName());
        assertFalse(handler.hasProperties());
        assertFalse(handler.endsOnAddressOperationNameSeparator());
        assertFalse(handler.endsOnPropertyListStart());
        assertFalse(handler.endsOnPropertySeparator());
        assertFalse(handler.endsOnPropertyValueSeparator());
        assertTrue(handler.endsOnNodeSeparator());
        assertFalse(handler.endsOnNodeTypeNameSeparator());
        assertFalse(handler.isRequestComplete());

        OperationRequestAddress address = handler.getAddress();
        address.isEmpty();

        try {
            handler.reset();
            parser.parse("//", handler);
            Assert.fail("Shouldn't allow root character in the middle of the path");
        } catch(CommandFormatException e) {
            // expected
        }

        try {
            handler.reset();
            parser.parse("/a/", handler);
            Assert.fail("Shouldn't allow root character in the middle of the path");
        } catch(CommandFormatException e) {
            // expected
        }

        handler.reset();
        parser.parse("/a=b/", handler);

        try {
            handler.reset();
            parser.parse("/a=b//", handler);
            Assert.fail("Shouldn't allow root character in the middle of the path");
        } catch(CommandFormatException e) {
            // expected
        }

        try {
            handler.reset();
            parser.parse("/a=b//a=b", handler);
            Assert.fail("Shouldn't allow root character in the middle of the path");
        } catch(CommandFormatException e) {
            // expected
        }
    }

    @Test
    public void testSlashNodeName() throws Exception {

        DefaultCallbackHandler handler = new DefaultCallbackHandler();

        parser.parse("/subsystem=undertow/server=default-server/host=default-host/location=\\/", handler);

        OperationRequestAddress address = handler.getAddress();
        assertNotNull(address);
        Iterator<Node> nodes = address.iterator();
        assertTrue(nodes.hasNext());
        Node node = nodes.next();
        assertEquals("subsystem", node.getType());
        assertEquals("undertow", node.getName());

        assertTrue(nodes.hasNext());
        node = nodes.next();
        assertEquals("server", node.getType());
        assertEquals("default-server", node.getName());

        assertTrue(nodes.hasNext());
        node = nodes.next();
        assertEquals("host", node.getType());
        assertEquals("default-host", node.getName());

        assertTrue(nodes.hasNext());
        node = nodes.next();
        assertEquals("location", node.getType());
        assertEquals("/", node.getName());

        assertFalse(nodes.hasNext());
    }
}
