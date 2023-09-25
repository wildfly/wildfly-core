/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.parsing.StateParser;
import org.jboss.as.cli.parsing.arguments.ArgumentValueCallbackHandler;
import org.jboss.as.cli.parsing.arguments.ArgumentValueInitialState;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class ArgumentValueParsingTestCase {

    @Test
    public void testExpressionOnly() throws Exception {
        final ModelNode value = parse("${test.expression}");
        assertNotNull(value);
        assertEquals(ModelType.STRING, value.getType());
        assertEquals("${test.expression}", value.asString());
    }

    @Test
    public void testExpressionInTheMiddleOnly() throws Exception {
        final ModelNode value = parse("test ${expression} in the middle");
        assertNotNull(value);
        assertEquals(ModelType.STRING, value.getType());
        assertEquals("test ${expression} in the middle", value.asString());
    }

    @Test
    public void testQuotedExpression() throws Exception {
        final ModelNode value = parse("\"${test.expression}\"");
        assertNotNull(value);
        assertEquals(ModelType.STRING, value.getType());
        assertEquals("${test.expression}", value.asString());
    }

    @Test
    public void testSimpleString() throws Exception {
        final ModelNode value = parse("text");
        assertNotNull(value);
        assertEquals(ModelType.STRING, value.getType());
        assertEquals("text", value.asString());
    }

    @Test
    public void testListInBrackets() throws Exception {
        final ModelNode value = parse("[a,b,c]");
        assertNotNull(value);
        assertEquals(ModelType.LIST, value.getType());
        final List<ModelNode> list = value.asList();
        assertEquals(3, list.size());
        assertNotNull(list.get(0));
        assertEquals("a", list.get(0).asString());
        assertNotNull(list.get(1));
        assertEquals("b", list.get(1).asString());
        assertNotNull(list.get(2));
        assertEquals("c", list.get(2).asString());
    }

    @Test
    public void testNestedList() throws Exception {
        final ModelNode value = parse("[a,b,[c,d]]");
        assertNotNull(value);
        assertEquals(ModelType.LIST, value.getType());
        List<ModelNode> list = value.asList();
        assertEquals(3, list.size());
        assertNotNull(list.get(0));
        assertEquals("a", list.get(0).asString());
        assertNotNull(list.get(1));
        assertEquals("b", list.get(1).asString());
        final ModelNode c = list.get(2);
        assertNotNull(c);
        assertEquals(ModelType.LIST, c.getType());
        list = c.asList();
        assertEquals(2, list.size());
        assertEquals("c", c.get(0).asString());
        assertEquals("d", c.get(1).asString());
    }

    @Test
    public void testListNoBrackets() throws Exception {
        final ModelNode value = parse("a,b,c");
        assertNotNull(value);
        assertEquals(ModelType.LIST, value.getType());
        final List<ModelNode> list = value.asList();
        assertEquals(3, list.size());
        assertNotNull(list.get(0));
        assertEquals("a", list.get(0).asString());
        assertNotNull(list.get(1));
        assertEquals("b", list.get(1).asString());
        assertNotNull(list.get(2));
        assertEquals("c", list.get(2).asString());
    }

    @Test
    public void testPropertyListInBrackets() throws Exception {
        final ModelNode value = parse("[a=b,c=d]");
        assertNotNull(value);
        assertEquals(ModelType.LIST, value.getType());
        final List<Property> list = value.asPropertyList();
        assertEquals(2, list.size());
        Property prop = list.get(0);
        assertNotNull(prop);
        assertEquals("a", prop.getName());
        assertEquals("b", prop.getValue().asString());
        prop = list.get(1);
        assertNotNull(prop);
        assertEquals("c", prop.getName());
        assertEquals("d", prop.getValue().asString());
    }

    @Test
    public void testObject() throws Exception {
        final ModelNode value = parse("a=b,c=d");
        assertNotNull(value);
        assertEquals(ModelType.OBJECT, value.getType());
        final List<Property> list = value.asPropertyList();
        assertEquals(2, list.size());
        Property prop = list.get(0);
        assertNotNull(prop);
        assertEquals("a", prop.getName());
        assertEquals("b", prop.getValue().asString());
        prop = list.get(1);
        assertNotNull(prop);
        assertEquals("c", prop.getName());
        assertEquals("d", prop.getValue().asString());
    }

    @Test
    public void testObjectInBraces() throws Exception {
        final ModelNode value = parse("{a=b,c=d}");
        assertNotNull(value);
        assertEquals(ModelType.OBJECT, value.getType());
        final List<Property> list = value.asPropertyList();
        assertEquals(2, list.size());
        Property prop = list.get(0);
        assertNotNull(prop);
        assertEquals("a", prop.getName());
        assertEquals("b", prop.getValue().asString());
        prop = list.get(1);
        assertNotNull(prop);
        assertEquals("c", prop.getName());
        assertEquals("d", prop.getValue().asString());
    }

    @Test
    public void testObjectWithList() throws Exception {
        final ModelNode value = parse("a=b,c=[d,e]");
        assertNotNull(value);
        assertEquals(ModelType.OBJECT, value.getType());
        final List<Property> list = value.asPropertyList();
        assertEquals(2, list.size());
        Property prop = list.get(0);
        assertNotNull(prop);
        assertEquals("a", prop.getName());
        assertEquals("b", prop.getValue().asString());
        prop = list.get(1);
        assertNotNull(prop);
        assertEquals("c", prop.getName());
        final ModelNode de = prop.getValue();
        assertEquals(ModelType.LIST, de.getType());
        final List<ModelNode> deList = de.asList();
        assertEquals(2, deList.size());
        assertEquals("d", deList.get(0).asString());
        assertEquals("e", deList.get(1).asString());
    }

    @Test
    public void testObjectWithChildObject() throws Exception {
        final ModelNode value = parse("a=b,c={d=e}");
        assertNotNull(value);
        assertEquals(ModelType.OBJECT, value.getType());
        final List<Property> list = value.asPropertyList();
        assertEquals(2, list.size());
        Property prop = list.get(0);
        assertNotNull(prop);
        assertEquals("a", prop.getName());
        assertEquals("b", prop.getValue().asString());
        prop = list.get(1);
        assertNotNull(prop);
        assertEquals("c", prop.getName());
        final ModelNode de = prop.getValue();
        assertEquals(ModelType.OBJECT, de.getType());
        assertEquals(1, de.keys().size());
        assertEquals("e", de.get("d").asString());
    }

    @Test
    public void testObjectWithPropertyList() throws Exception {
        final ModelNode value = parse("a=b,c=[d=e,f=g]");
        assertNotNull(value);
        assertEquals(ModelType.OBJECT, value.getType());
        final List<Property> list = value.asPropertyList();
        assertEquals(2, list.size());
        Property prop = list.get(0);
        assertNotNull(prop);
        assertEquals("a", prop.getName());
        assertEquals("b", prop.getValue().asString());
        prop = list.get(1);
        assertNotNull(prop);
        assertEquals("c", prop.getName());
        final ModelNode c = prop.getValue();
        assertEquals(ModelType.LIST, c.getType());
        final List<Property> propList = c.asPropertyList();
        assertEquals(2, propList.size());
        prop = propList.get(0);
        assertEquals("d", prop.getName());
        assertEquals("e", prop.getValue().asString());
        prop = propList.get(1);
        assertEquals("f", prop.getName());
        assertEquals("g", prop.getValue().asString());
    }

    @Test
    public void testListOfObjects() throws Exception {
        final ModelNode value = parse("[{a=b},{c=[d=e,f={g=h}]}]");
        assertNotNull(value);
        assertEquals(ModelType.LIST, value.getType());
        final List<ModelNode> list = value.asList();
        assertEquals(2, list.size());
        ModelNode item = list.get(0);
        assertNotNull(item);
        assertEquals(1, item.keys().size());
        assertEquals("b", item.get("a").asString());
        item = list.get(1);
        assertNotNull(item);
        assertEquals(1, item.keys().size());
        item = item.get("c");
        assertTrue(item.isDefined());
        assertEquals(ModelType.LIST, item.getType());
        final List<Property> propList = item.asPropertyList();
        assertEquals(2, propList.size());
        Property prop = propList.get(0);
        assertEquals("d", prop.getName());
        assertEquals("e", prop.getValue().asString());
        prop = propList.get(1);
        assertEquals("f", prop.getName());
        final ModelNode gh = prop.getValue();
        assertEquals(1, gh.keys().size());
        assertEquals("h", gh.get("g").asString());
    }

    @Test
    public void testMix() throws Exception {
        final ModelNode value = parse("a=b,c=[d=e,f={g=h}]");
        assertNotNull(value);
        assertEquals(ModelType.OBJECT, value.getType());
        final List<Property> list = value.asPropertyList();
        assertEquals(2, list.size());
        Property prop = list.get(0);
        assertNotNull(prop);
        assertEquals("a", prop.getName());
        assertEquals("b", prop.getValue().asString());
        prop = list.get(1);
        assertNotNull(prop);
        assertEquals("c", prop.getName());
        final ModelNode c = prop.getValue();
        assertEquals(ModelType.LIST, c.getType());
        final List<Property> propList = c.asPropertyList();
        assertEquals(2, propList.size());
        prop = propList.get(0);
        assertEquals("d", prop.getName());
        assertEquals("e", prop.getValue().asString());
        prop = propList.get(1);
        assertEquals("f", prop.getName());
        final ModelNode gh = prop.getValue();
        assertEquals(1, gh.keys().size());
        assertEquals("h", gh.get("g").asString());
    }

    @Test
    public void testOpeningCurlyBracesInValue() throws Exception {

        ModelNode value = parse(">{b\\=c}");
        assertNotNull(value);
        assertEquals(ModelType.STRING, value.getType());
        assertEquals(">{b=c}", value.asString());

        value = parse(">\\{b\\=c}");
        assertNotNull(value);
        assertEquals(ModelType.STRING, value.getType());
        assertEquals(">{b=c}", value.asString());
    }

    @Test
    public void testOpeningBracketInValue() throws Exception {
        ModelNode value = parse("a[bc");
        assertNotNull(value);
        assertEquals(ModelType.STRING, value.getType());
        assertEquals("a[bc", value.asString());

        value = parse("a\\[bc");
        assertNotNull(value);
        assertEquals(ModelType.STRING, value.getType());
        assertEquals("a[bc", value.asString());

        value = parse("a \\[ b c");
        assertNotNull(value);
        assertEquals(ModelType.STRING, value.getType());
        assertEquals("a [ b c", value.asString());
    }

    @Test
    public void testDeactivatedEqualsSign() throws Exception {
        final ModelNode value = parse("a=b{c[d=e]}]}");
        assertNotNull(value);
        assertEquals(ModelType.OBJECT, value.getType());
        assertEquals(1, value.keys().size());
        final ModelNode a = value.get("a");
        assertTrue(a.isDefined());
        assertEquals(ModelType.STRING, a.getType());
        assertEquals("b{c[d=e]}]}", a.asString());
    }

    @Test
    public void testAny() throws Exception {
        ModelNode value = parse("\"any(not(match(\\\"Prepared response is\\\")),match(\\\"Scanning\\\"))\"");
        assertNotNull(value);
        assertEquals(ModelType.STRING, value.getType());
        assertEquals("any(not(match(\"Prepared response is\")),match(\"Scanning\"))", value.asString());

        value = parse("any(not(match(\"Prepared response is\")),match(\"Scanning\"))");
        assertNotNull(value);
        assertEquals(ModelType.LIST, value.getType());
        final List<ModelNode> list = value.asList();
        assertEquals(2, list.size());

        ModelNode item = list.get(0);
        assertEquals(ModelType.STRING, item.getType());
        assertEquals("any(not(match(\"Prepared response is\"))", item.asString());

        item = list.get(1);
        assertEquals(ModelType.STRING, item.getType());
        assertEquals("match(\"Scanning\"))", item.asString());
    }

    @Test
    public void testQuotesAndSpaces() throws Exception {
        ModelNode value = parse("\" \"");
        assertNotNull(value);
        assertEquals(ModelType.STRING, value.getType());
        assertEquals(" ", value.asString());

        value = parse(" \" \"");
        assertNotNull(value);
        assertEquals(ModelType.STRING, value.getType());
        assertEquals(" ", value.asString());

        value = parse(" \" \\\"\"");
        assertNotNull(value);
        assertEquals(ModelType.STRING, value.getType());
        assertEquals(" \"", value.asString());
    }

    @Test
    public void testEscapedQuotes() throws Exception {
        ModelNode value = parse("\"substituteAll(\\\"JBAS\\\",\\\"DUMMY\\\")\"");
        assertNotNull(value);
        assertEquals(ModelType.STRING, value.getType());
        assertEquals("substituteAll(\"JBAS\",\"DUMMY\")", value.asString());
    }

    @Test
    public void testLoginModules() throws Exception {
        ModelNode value = parse("[{code=Database, flag=required, module-options=[unauthenticatedIdentity=guest," +
                "dsJndiName=java:jboss/jdbc/ApplicationDS," +
                "principalsQuery= select password from users where username=? ," +
                "rolesQuery = \"select name, 'Roles' FROM user_roless ur, roles r, user u WHERE u.username=? and u.id = ur.user_id and ur.role_id = r.id\" ," +
                "hashAlgorithm = MD5,hashEncoding = hex] }]");
        assertLoginModules(value);
    }

    @Test
    public void testLoginModulesWithLineBreaks() throws Exception {
        final StringBuilder buf = new StringBuilder();
        buf.append("[ \\").append(Util.LINE_SEPARATOR);
        buf.append("\t\t{ \\").append(Util.LINE_SEPARATOR);
        buf.append("\t\t\tcode=Database, \\").append(Util.LINE_SEPARATOR);
        buf.append("\t\t\tflag= \\").append(Util.LINE_SEPARATOR).append("required, \\").append(Util.LINE_SEPARATOR);
        buf.append("\t\t\t").append("module-options=[ \\").append(Util.LINE_SEPARATOR);
        buf.append("\t\t\t\t\t").append("unauthenticatedIdentity=guest, \\").append(Util.LINE_SEPARATOR);
        buf.append("\t\t\t\t\t").append("dsJndiName=java:jboss/jdbc/ApplicationDS, \\").append(Util.LINE_SEPARATOR);
        buf.append("\t\t\t\t\t").append("principalsQuery= select password from users where username=? , \\").append(Util.LINE_SEPARATOR);
        buf.append("\t\t\t\t\t").append("rolesQuery = \"select name, 'Roles' FROM user_roless ur, roles r, user u WHERE u.username=? and u.id = ur.user_id and ur.role_id = r.id\" , \\").append(Util.LINE_SEPARATOR);
        buf.append("\t\t\t\t\t").append("hashAlgorithm = MD5, \\").append(Util.LINE_SEPARATOR);
        buf.append("\t\t\t\t\t").append("hashEncoding = hex \\").append(Util.LINE_SEPARATOR);
        buf.append("\t\t\t\t").append("] \\").append(Util.LINE_SEPARATOR);
        buf.append("\t\t} \\").append(Util.LINE_SEPARATOR);
        buf.append(']');
        ModelNode value = parse(buf.toString());
        assertLoginModules(value);
    }

    @Test
    public void testWFCORE600() throws Exception {
        ModelNode value = parse("[{ c=[s], m=s, p=[{k=a,v=b},{k=b,v=c}] }]");

        assertEquals(ModelType.LIST, value.getType());
        List<ModelNode> list = value.asList();
        assertEquals(1, list.size());
        value = list.get(0);
        assertEquals(ModelType.OBJECT, value.getType());
        assertEquals(3, value.keys().size());

        assertTrue(value.hasDefined("c"));
        ModelNode prop = value.get("c");
        assertEquals(ModelType.LIST, prop.getType());
        list = prop.asList();
        assertEquals(1, list.size());
        assertEquals("s", list.get(0).asString());

        assertTrue(value.hasDefined("m"));
        prop = value.get("m");
        assertEquals(ModelType.STRING, prop.getType());
        assertEquals("s", prop.asString());

        assertTrue(value.hasDefined("p"));
        prop = value.get("p");
        assertEquals(ModelType.LIST, prop.getType());
        list = prop.asList();
        assertEquals(2, list.size());

        value = list.get(0);
        assertEquals(ModelType.OBJECT, value.getType());
        assertEquals(2, value.keys().size());
        assertTrue(value.hasDefined("k"));
        assertEquals("a", value.get("k").asString());
        assertTrue(value.hasDefined("v"));
        assertEquals("b", value.get("v").asString());

        value = list.get(1);
        assertEquals(ModelType.OBJECT, value.getType());
        assertEquals(2, value.keys().size());
        assertTrue(value.hasDefined("k"));
        assertEquals("b", value.get("k").asString());
        assertTrue(value.hasDefined("v"));
        assertEquals("c", value.get("v").asString());
    }

    protected void assertLoginModules(ModelNode value) {
        assertNotNull(value);
        assertEquals(ModelType.LIST, value.getType());
        List<ModelNode> list = value.asList();
        assertEquals(1, list.size());
        value = list.get(0);
        assertEquals("Database", value.get("code").asString());
        assertEquals("required", value.get("flag").asString());
        value = value.get("module-options");
        assertEquals(ModelType.LIST, value.getType());
        list = value.asList();
        assertEquals(6, list.size());
        assertEquals("guest", list.get(0).get("unauthenticatedIdentity").asString());
        assertEquals("java:jboss/jdbc/ApplicationDS", list.get(1).get("dsJndiName").asString());
        assertEquals("select password from users where username=?", list.get(2).get("principalsQuery").asString());
        assertEquals("select name, 'Roles' FROM user_roless ur, roles r, user u WHERE u.username=? and u.id = ur.user_id and ur.role_id = r.id", list.get(3).get("rolesQuery").asString());
        assertEquals("MD5", list.get(4).get("hashAlgorithm").asString());
        assertEquals("hex", list.get(5).get("hashEncoding").asString());
    }

    @Test
    public void testBytesValue() throws Exception {
        checkBytes(parse("{a=bytes{31,0x32,-99}}"));
        checkBytes(parse("{a=  bytes   {   31 ,  0x32  , -99 }}"));
        checkBytes(parse("{a=bytes{+31,0x32,-99}}"));
        checkBytes(parse("{a=  bytes   { +31 ,  0x32 , -99  }}"));
        ModelNode mn = parse("{a=bytes{}}");
        assertEquals(ModelType.BYTES, mn.get("a").getType());
        byte[] bytes = mn.get("a").asBytes();
        assertEquals(0, bytes.length);
    }

    @Test
    public void testBytesBorderValues() throws Exception {
        try {
            parse("{a=bytes{128}}");
            fail("Decimals larger than Byte.MAX_VALUE shouldn't be accepted.");
        } catch (CommandFormatException ignore) {}

        try {
            parse("{a=bytes{-129}}");
            fail("Decimals lower than Byte.MIN_VALUE shouldn't be accepted.");
        } catch (CommandFormatException ignore) {}

        try {
            parse("{a=bytes{-0x1}}");
            fail("Negative hexadecimals shouldn't be accepted.");
        } catch (CommandFormatException ignore) {}

        try {
            parse("{a=bytes{0x100}}");
            fail("Hexadecimals larger than 0xff shouldn't be accepted.");
        } catch (CommandFormatException ignore) {}

        ModelNode mn = parse("{a=bytes{0x0, 0x1, 0x01, 0xff, 0x0ff, 0x80, 0x7f}}");
        byte[] bytes = mn.get("a").asBytes();
        assertEquals(0, bytes[0]);
        assertEquals(1, bytes[1]);
        assertEquals(1, bytes[2]);
        assertEquals(-1, bytes[3]);
        assertEquals(-1, bytes[4]);
        assertEquals(Byte.MIN_VALUE, bytes[5]);
        assertEquals(Byte.MAX_VALUE, bytes[6]);
    }

    private void checkBytes(ModelNode value) {
        ModelNode a = value.get("a");
        assertEquals(ModelType.BYTES, a.getType());
        byte[] bytes = a.asBytes();
        assertEquals(31, bytes[0]);
        assertEquals(50, bytes[1]);
        assertEquals(-99, bytes[2]);
    }

    protected ModelNode parse(String str) throws CommandFormatException {
        final ArgumentValueCallbackHandler handler = new ArgumentValueCallbackHandler();
        StateParser.parse(str, handler, ArgumentValueInitialState.INSTANCE);
        return handler.getResult();
    }
}
