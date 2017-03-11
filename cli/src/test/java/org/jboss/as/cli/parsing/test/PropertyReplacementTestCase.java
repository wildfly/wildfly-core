/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.cli.parsing.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.util.CLIExpressionResolver;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * System property replacement tests.
 *
 * @author Alexey Loubyansky
 */
public class PropertyReplacementTestCase {

    private static final String NODE_TYPE_PROP_NAME = "test.node-type";
    private static final String NODE_TYPE_PROP_VALUE = "test-node-type";
    private static final String NODE_NAME_PROP_NAME = "test.node-name";
    private static final String NODE_NAME_PROP_VALUE = "test-node-name";
    private static final String OP_PROP_NAME = "test.op-name";
    private static final String OP_PROP_VALUE = "test-op";
    private static final String OP_PROP_PROP_NAME = "test.prop-name";
    private static final String OP_PROP_PROP_VALUE = "test-prop";

    private static final String PROP_PART1_NAME = "test.p1";
    private static final String PROP_PART1_VALUE = "test";
    private static final String PROP_PART2_NAME = "test.p2";
    private static final String PROP_PART2_VALUE = "op-name";

    private static final String PROP_RECURSIVE_NAME = "test.prop.recursive";
    private static final String PROP_RECURSIVE_VALUE = "${" + OP_PROP_NAME + "}";

    private static final String ENV_PROP_NAME = "env.test.prop";
    private static final String ENV_PROP_VALUE = "sysprop";

    private static final String EMPTY_PROP_NAME = "test.node-name-empty"; // an empty property
    private static final String EMPTY_PROP_VALUE = "";

    private static final String PREFIX = "prefix";

    @BeforeClass
    public static void setup() {
        WildFlySecurityManager.setPropertyPrivileged(NODE_TYPE_PROP_NAME, NODE_TYPE_PROP_VALUE);
        WildFlySecurityManager.setPropertyPrivileged(NODE_NAME_PROP_NAME, NODE_NAME_PROP_VALUE);
        WildFlySecurityManager.setPropertyPrivileged(OP_PROP_NAME, OP_PROP_VALUE);
        WildFlySecurityManager.setPropertyPrivileged(OP_PROP_PROP_NAME, OP_PROP_PROP_VALUE);

        WildFlySecurityManager.setPropertyPrivileged(PROP_PART1_NAME, PROP_PART1_VALUE);
        WildFlySecurityManager.setPropertyPrivileged(PROP_PART2_NAME, PROP_PART2_VALUE);

        WildFlySecurityManager.setPropertyPrivileged(PROP_RECURSIVE_NAME, PROP_RECURSIVE_VALUE);

        WildFlySecurityManager.setPropertyPrivileged(ENV_PROP_NAME, ENV_PROP_VALUE);

        WildFlySecurityManager.setPropertyPrivileged(EMPTY_PROP_NAME, EMPTY_PROP_VALUE);
    }

    @AfterClass
    public static void cleanup() {
        WildFlySecurityManager.clearPropertyPrivileged(NODE_TYPE_PROP_NAME);
        WildFlySecurityManager.clearPropertyPrivileged(NODE_NAME_PROP_NAME);
        WildFlySecurityManager.clearPropertyPrivileged(OP_PROP_NAME);
        WildFlySecurityManager.clearPropertyPrivileged(OP_PROP_PROP_NAME);

        WildFlySecurityManager.clearPropertyPrivileged(PROP_PART1_NAME);
        WildFlySecurityManager.clearPropertyPrivileged(PROP_PART2_NAME);

        WildFlySecurityManager.clearPropertyPrivileged(PROP_RECURSIVE_NAME);
        WildFlySecurityManager.clearPropertyPrivileged(EMPTY_PROP_NAME);
    }

    @Test
    public void testDefaultAsProperty() throws Exception {

        assertEquals("$test-op", CLIExpressionResolver.resolve("${unknown:$$${test.op-name}}"));
        assertEquals("test-op", CLIExpressionResolver.resolve("${unknown:${test.op-name}}"));
        assertEquals("test-op", CLIExpressionResolver.resolve("${test.op-name:${unknown}}"));
        assertEquals("${unknown1:${unknown2}}", CLIExpressionResolver.resolveOrOriginal("${unknown1:${unknown2}}"));
        assertEquals("test-op${unknown1}", CLIExpressionResolver.resolveLax("${test.op-name}${unknown1}"));
        assertEquals("${unknown1}test-op", CLIExpressionResolver.resolveLax("${unknown1}${test.op-name}"));
    }

    @Test
    public void testDollarAsEscape() throws Exception {
        assertEquals("$test-op", CLIExpressionResolver.resolve("$$${test.op-name}"));
    }

    @Test
    public void testRecursiveReplacement() throws Exception {
        final ParsedCommandLine parsed = parse("${" + PROP_RECURSIVE_NAME + "}");
        assertEquals(OP_PROP_VALUE, parsed.getOperationName());
    }

    @Test
    public void testNestedReplacement() throws Exception {
        final ParsedCommandLine parsed = parse("${${" + PROP_PART1_NAME + "}.${" + PROP_PART2_NAME + "}}");
        assertEquals(OP_PROP_VALUE, parsed.getOperationName());
    }

    @Test
    public void testUnresolvedOperationName() {
        assertFailedToParse(":${" + OP_PROP_NAME + "xxx}");
    }

    @Test
    public void testOperationName() throws Exception {
        final ParsedCommandLine parsed = parse(":${" + OP_PROP_NAME + "}");
        assertEquals(OP_PROP_VALUE, parsed.getOperationName());
    }

    @Test
    public void testUnresolvedNodeType() throws Exception {
        assertFailedToParse("/${" + NODE_TYPE_PROP_NAME + "xxx}=test:op");
    }

    @Test
    public void testNodeType() throws Exception {
        final ParsedCommandLine parsed = parse("/${" + NODE_TYPE_PROP_NAME + "}=test:op");
        final OperationRequestAddress address = parsed.getAddress();
        assertNotNull(address);
        assertEquals(NODE_TYPE_PROP_VALUE, address.getNodeType());
        assertEquals("test", address.getNodeName());
    }

    @Test
    public void testNodeTypeWithEmptyProperty() throws Exception {
        final ParsedCommandLine parsed = parse("/" + PREFIX + "${" + EMPTY_PROP_NAME + "}${" + NODE_TYPE_PROP_NAME + "}=test:op");
        final OperationRequestAddress address = parsed.getAddress();
        assertNotNull(address);
        assertEquals(PREFIX + NODE_TYPE_PROP_VALUE, address.getNodeType());
        assertEquals("test", address.getNodeName());
    }

    @Test
    public void testUnresolvedNodeName() throws Exception {
        assertFailedToParse("/test=${" + NODE_TYPE_PROP_NAME + "xxx}:op");
    }

    @Test
    public void testNodeName() throws Exception {
        final ParsedCommandLine parsed = parse("/test=${" + NODE_NAME_PROP_NAME + "}:op");
        final OperationRequestAddress address = parsed.getAddress();
        assertNotNull(address);
        assertEquals("test", address.getNodeType());
        assertEquals(NODE_NAME_PROP_VALUE, address.getNodeName());
    }

    @Test
    public void testNodeNameWithEmptyProperty() throws Exception {
        final ParsedCommandLine parsed = parse("/test=" + PREFIX +"${" + EMPTY_PROP_NAME + "}${" + NODE_NAME_PROP_NAME + "}:op");
        final OperationRequestAddress address = parsed.getAddress();
        assertNotNull(address);
        assertEquals("test", address.getNodeType());
        assertEquals(PREFIX + NODE_NAME_PROP_VALUE, address.getNodeName());
    }

    @Test
    public void testUnresolvedOperationPropertyName() {
        assertFailedToParse(":write-attribute(${" + OP_PROP_PROP_NAME + "xxx}=value)");
    }

    @Test
    public void testOperationPropertyName() throws Exception {
        final ParsedCommandLine parsed = parse(":write-attribute(${" + OP_PROP_PROP_NAME + "}=test)");
        assertEquals("write-attribute", parsed.getOperationName());
        assertEquals(parsed.getPropertyValue(OP_PROP_PROP_VALUE), "test");
    }

    @Test
    public void testOperationNameAndValue() throws Exception {
        final ParsedCommandLine parsed = parse(":write-attribute(${" + OP_PROP_PROP_NAME + "}=${" + OP_PROP_PROP_NAME + "})");
        assertEquals("write-attribute", parsed.getOperationName());
        // variables unlike system properties are always resolved
        assertEquals("${" + OP_PROP_PROP_NAME + "}", parsed.getPropertyValue(OP_PROP_PROP_VALUE));
    }

    @Test
    public void testQuotedParameterValue() throws Exception {
        final ParsedCommandLine parsed = parse(":op(name=\"${" + OP_PROP_PROP_NAME + "}\")");
        assertEquals("op", parsed.getOperationName());
        assertEquals("\"${" + OP_PROP_PROP_NAME + "}\"", parsed.getPropertyValue("name"));
    }

    @Test
    public void testComplexParameterValue() throws Exception {
        final ParsedCommandLine parsed = parse(":op(name={attr=${" + OP_PROP_PROP_NAME + "}})");
        assertEquals("op", parsed.getOperationName());
        assertEquals("{attr=${" + OP_PROP_PROP_NAME + "}}", parsed.getPropertyValue("name"));
    }

    @Test
    public void testListParameterValue() throws Exception {
        final ParsedCommandLine parsed = parse(":op(name=[${" + OP_PROP_PROP_NAME + "}])");
        assertEquals("op", parsed.getOperationName());
        assertEquals("[${" + OP_PROP_PROP_NAME + "}]", parsed.getPropertyValue("name"));
    }

    @Test
    public void testParameterValueInParenthesis() throws Exception {
        final ParsedCommandLine parsed = parse(":op(name=(${" + OP_PROP_PROP_NAME + "}))");
        assertEquals("op", parsed.getOperationName());
        assertEquals("(${" + OP_PROP_PROP_NAME + "})", parsed.getPropertyValue("name"));
    }

    @Test
    public void testDMRParameterValue() throws Exception {
        final ParsedCommandLine parsed = parse(":op(name = {\"attr\" => \"${" + OP_PROP_PROP_NAME + "}\"})");
        assertEquals("op", parsed.getOperationName());
        assertEquals("{\"attr\" => \"${" + OP_PROP_PROP_NAME + "}\"}", parsed.getPropertyValue("name"));
    }

    @Test
    public void testUnresolvedCommandName() {
        assertFailedToParse("${" + OP_PROP_NAME + "xxx}");
    }

    @Test
    public void testCommandName() throws Exception {
        final ParsedCommandLine parsed = parse("${" + OP_PROP_NAME + "}");
        assertEquals(OP_PROP_VALUE, parsed.getOperationName());
    }

    @Test
    public void testUnresolvedCommandArgumentName() {
        assertFailedToParse("command-name --${" + OP_PROP_PROP_NAME + "xxx}=value");
    }

    @Test
    public void testCommandArgumentName() throws Exception {
        final ParsedCommandLine parsed = parse("command-name --${" + OP_PROP_PROP_NAME + "}=test");
        assertEquals("command-name", parsed.getOperationName());
        assertEquals(parsed.getPropertyValue("--" + OP_PROP_PROP_VALUE), "test");
    }

    @Test
    public void testCommandArgumentNameAndValue() throws Exception {
        final ParsedCommandLine parsed = parse("command-name --${" + OP_PROP_PROP_NAME + "}=${" + OP_PROP_PROP_NAME + "}");
        assertEquals("command-name", parsed.getOperationName());
        // there is a different config option whether to resolve argument values
        assertEquals(parsed.getPropertyValue("--" + OP_PROP_PROP_VALUE), "${" + OP_PROP_PROP_NAME + "}");
    }

    @Test
    // there is a different config option whether to resolve argument values
    public void testUnresolvedCommandArgumentValue() throws Exception {
        final ParsedCommandLine parsed = parse("command-name ${" + OP_PROP_PROP_NAME + "}=test");
        assertEquals("command-name", parsed.getOperationName());
        assertFalse(parsed.hasProperty(OP_PROP_PROP_VALUE));
        assertFalse(parsed.hasProperty("--" + OP_PROP_PROP_VALUE));
        assertEquals(1, parsed.getOtherProperties().size());
        assertEquals("${" + OP_PROP_PROP_NAME + "}=test", parsed.getOtherProperties().get(0));
    }

    @Test
    public void testUnresolvedHeaderName() {
        assertFailedToParse(":write-attribute{${" + OP_PROP_PROP_NAME + "xxx}=value}");
    }

    @Test
    public void testHeaderNameAndValue() throws Exception {
        ParsedCommandLine parsed = parse(":write-attribute{${" + OP_PROP_PROP_NAME + "}=${" + OP_PROP_NAME + "}}");
        assertEquals("write-attribute", parsed.getOperationName());
        assertTrue(parsed.hasHeaders());
        assertTrue(parsed.hasHeader(OP_PROP_PROP_VALUE));
        ModelNode headers = new ModelNode();
        parsed.getLastHeader().addTo(null, headers);
        assertEquals(OP_PROP_VALUE, headers.get(OP_PROP_PROP_VALUE).asString());

        parsed = parse(":write-attribute{${" + OP_PROP_PROP_NAME + "} ${" + OP_PROP_NAME + "}}");
        assertEquals("write-attribute", parsed.getOperationName());
        assertTrue(parsed.hasHeaders());
        assertTrue(parsed.hasHeader(OP_PROP_PROP_VALUE));
        headers = new ModelNode();
        parsed.getLastHeader().addTo(null, headers);
        assertEquals(OP_PROP_VALUE, headers.get(OP_PROP_PROP_VALUE).asString());
    }

    @Test
    public void testSystemPropertiesInTheMix() throws Exception {
        final ParsedCommandLine parsed = parse("co${" + OP_PROP_NAME + "}${" + OP_PROP_NAME + "} "
                + "--${" + OP_PROP_PROP_NAME + "}_${" + OP_PROP_PROP_NAME + "}=${" + OP_PROP_PROP_NAME + "}");
        assertEquals("co" + OP_PROP_VALUE + OP_PROP_VALUE, parsed.getOperationName());
        assertTrue(parsed.getOtherProperties().isEmpty());
        assertEquals("${" + OP_PROP_PROP_NAME + "}", parsed.getPropertyValue("--" + OP_PROP_PROP_VALUE + "_" + OP_PROP_PROP_VALUE));
    }

    private void assertFailedToParse(String line) {
        try {
            parse(line);
            fail("should fail to resolve the property");
        } catch(CommandFormatException e) {
            // expected
        }
    }

    protected ParsedCommandLine parse(String line) throws CommandFormatException {
        DefaultCallbackHandler args = new DefaultCallbackHandler();
        args.parse(null, line, null);
        return args;
    }
}
