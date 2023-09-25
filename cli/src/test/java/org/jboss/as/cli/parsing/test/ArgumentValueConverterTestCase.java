/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.jboss.as.cli.ArgumentValueConverter;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.completion.mock.MockCommandContext;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class ArgumentValueConverterTestCase {

    private final CommandContext ctx = new MockCommandContext();

    @Test
    public void testDefault_String() throws Exception {
        final ModelNode value = parseObject("text");
        assertNotNull(value);
        assertEquals(ModelType.STRING, value.getType());
        assertEquals("text", value.asString());
    }

    @Test
    public void testEmptyObject() throws Exception {
        final ModelNode value = parseObject("{}");
        assertNotNull(value);
        assertEquals(ModelType.OBJECT, value.getType());
    }

    @Test
    public void testNestedEmptyObject() throws Exception {
        final ModelNode value = parseObject("{toto={}}");
        final List<Property> list = value.asPropertyList();
        Property prop = list.get(0);
        assertEquals(ModelType.OBJECT, prop.getValue().getType());
    }

    @Test
    public void testDefault_List() throws Exception {
        final ModelNode value = parseObject("[\"item1\",\"item2\"]");
        assertNotNull(value);
        assertEquals(ModelType.LIST, value.getType());
        final List<ModelNode> list = value.asList();
        assertEquals(2, list.size());
        assertEquals("item1", list.get(0).asString());
        assertEquals("item2", list.get(1).asString());
    }

    @Test
    public void testDefault_Object() throws Exception {
        final ModelNode value = parseObject("{\"item1\"=>\"value1\",\"item2\"=>\"value2\"}");
        assertNotNull(value);
        assertEquals(ModelType.OBJECT, value.getType());
        final List<Property> list = value.asPropertyList();
        assertEquals(2, list.size());
        assertEquals("item1", list.get(0).getName());
        assertEquals("value1", list.get(0).getValue().asString());
        assertEquals("item2", list.get(1).getName());
        assertEquals("value2", list.get(1).getValue().asString());
    }

    @Test
    public void testList_NoBrackets() throws Exception {
        final ModelNode value = parseList("a,b,c");
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
    public void testList_NoBracketsOneItem() throws Exception {
        final ModelNode value = parseList("a");
        assertNotNull(value);
        assertEquals(ModelType.LIST, value.getType());
        final List<ModelNode> list = value.asList();
        assertEquals(1, list.size());
        assertNotNull(list.get(0));
        assertEquals("a", list.get(0).asString());
    }

    @Test
    public void testList_WithBrackets() throws Exception {
        final ModelNode value = parseList("[a,b,c]");
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
    public void testList_DMR() throws Exception {
        final ModelNode value = parseList("[\"a\",\"b\",\"c\"]");
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
    public void testPropertyList_DMR() throws Exception {
        final ModelNode value = parseProperties("[(\"a\"=>\"b\"),(\"c\"=>\"d\")]");
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
    public void testPropertyList_SimpleCommaSeparated() throws Exception {
        final ModelNode value = parseProperties("a=b,c=d");
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
    public void testPropertyList_SimpleCommaSeparatedInBrackets() throws Exception {
        final ModelNode value = parseProperties("[a=b,c=d]");
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
    public void testList_Object() throws Exception {
        final ModelNode value = parseList("[{a1=vala1,a2=[{a3=vala3}]},{b1=valb1,b2=[{b3=valb3}]}]");
        assertNotNull(value);
        assertEquals(ModelType.LIST, value.getType());
        final List<ModelNode> list = value.asList();
        assertEquals(2, list.size());

        ModelNode obj1 = list.get(0);
        assertEquals(ModelType.OBJECT, obj1.getType());
        assertNotNull(obj1);
        assertEquals("vala1", obj1.get("a1").asString());
        ModelNode obj2 = obj1.get("a2");
        assertNotNull(obj2);
        assertEquals(ModelType.LIST, obj2.getType());
        final List<ModelNode> list2 = obj2.asList();
        assertEquals(1, list2.size());
        ModelNode obj3 = list2.get(0);
        assertNotNull(obj3);
        assertEquals(ModelType.OBJECT, obj3.getType());
        assertEquals("vala3", obj3.get("a3").asString());

        ModelNode obj4 = list.get(1);
        assertEquals(ModelType.OBJECT, obj4.getType());
        assertNotNull(obj4);
        assertEquals("valb1", obj4.get("b1").asString());
        ModelNode obj5 = obj4.get("b2");
        assertNotNull(obj5);
        assertEquals(ModelType.LIST, obj5.getType());
        final List<ModelNode> list4 = obj5.asList();
        assertEquals(1, list4.size());
        ModelNode obj6 = list4.get(0);
        assertNotNull(obj6);
        assertEquals(ModelType.OBJECT, obj6.getType());
        assertEquals("valb3", obj6.get("b3").asString());
    }

    @Test
    public void testObject_DMR() throws Exception {
        final ModelNode value = parseObject("{\"a\"=>\"b\",\"c\"=>\"d\"}");
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
    public void testObject_SimpleCommaSeparated() throws Exception {
        final ModelNode value = parseObject("a=b,c=d");
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
    public void testObject_SimpleCommaSeparatedInCurlyBraces() throws Exception {
        final ModelNode value = parseObject("{a=b,c=d}");
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
    public void testObject_TextValue() throws Exception {
        final ModelNode value = parseObject("\"text\"");
        assertNotNull(value);
        assertEquals(ModelType.STRING, value.getType());
        assertEquals("text", value.asString());
    }

    @Test
    public void testObject_TextEmptyValue() throws Exception {
        final ModelNode obj = parseObject("{x=\"\"}");
        assertNotNull(obj);
        assertEquals(ModelType.OBJECT, obj.getType());
        ModelNode val = obj.get("x");
        assertEquals(ModelType.STRING, val.getType());
        assertEquals("", val.asString());
    }

    protected ModelNode parseObject(String value) throws CommandFormatException {
        return ArgumentValueConverter.DEFAULT.fromString(ctx, value);
    }

    protected ModelNode parseList(String value) throws CommandFormatException {
        return ArgumentValueConverter.LIST.fromString(ctx, value);
    }

    protected ModelNode parseProperties(String value) throws CommandFormatException {
        return ArgumentValueConverter.PROPERTIES.fromString(ctx, value);
    }
}
