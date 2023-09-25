/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.StateParser;
import org.jboss.as.cli.parsing.operation.PropertyListState;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class OperationParamListStateTestCase extends BaseStateParserTest {

    @Test
    public void testParamSimple() throws Exception {
        parseAsParamList("a", "b");
    }

    @Test
    public void testParamSimpleQuotes() throws Exception {
        parseAsParamList("simple-quotes", "\"simple quotes\"");
    }

    @Test
    public void testParamSimpleBrackets() {
        parseAsParamList("simple-brackets", "[simple brackets]");
    }

    @Test
    public void testParamSimpleParenthesis() {
        parseAsParamList("simple-parenthesis", "(simple parenthesis)");
    }

    @Test
    public void testParamSimpleBraces() {
        parseAsParamList("simple-braces", "{simple braces}");
    }

    @Test
    public void testParamSteps() {
        parseAsParamList("steps", "[{\"operation\"=>\"add-system-property\",\"name\"=>\"test\",\"value\"=\"newValue\"},{\"operation\"=>\"add-system-property\",\"name\"=>\"test2\",\"value\"=>\"test2\"}]");
    }

    @Test
    public void testAllParams() {
        parseAsParamList(Param.allParams());
        assertNotNull(result);
        assertNull(result.buffer);
        assertEquals(1, result.children.size());

        ParsedTerm params = result.children.get(0);
        assertNotNull(params);
        assertNull(params.buffer);
        assertEquals(Param.all.size(), params.children.size());

        for(int i = 0; i < Param.all.size(); ++i) {
            Param param = Param.all.get(i);
            assertParam(param.name, param.value, params.children.get(i));
        }
    }

    protected void parseAsParamList(String name, String value) {

        Param param = new Param(name, value);

        parseAsParamList('(' + param.name + '=' + param.value + ')');

        assertNotNull(result);
        assertNull(result.buffer);
        assertEquals(1, result.children.size());

        ParsedTerm params = result.children.get(0);
        assertNotNull(params);
        assertNull(params.buffer);
        assertEquals(1, params.children.size());

        assertParam(param.name, param.value, params.children.get(0));
    }

    protected void parseAsParamList(String str) {

        StateParser parser = new StateParser();
        parser.addState('(', PropertyListState.INSTANCE);
        try {
            parser.parse(str, callbackHandler);
        } catch (CommandFormatException e) {
            Assert.fail(e.getLocalizedMessage());
        }
    }

    protected void assertParam(String name, String value, ParsedTerm param) {
        assertNotNull(param);
        assertNotNull(param.buffer);
        assertEquals(name, param.buffer.toString().trim());
        assertEquals(1, param.children.size());
        ParsedTerm paramValue = param.children.get(0);
        assertNotNull(paramValue);
        assertEquals(value, paramValue.valueAsString());
        //assertEquals(0, paramValue.children.size());
    }

    static class Param {
        static final List<Param> all = new ArrayList<Param>();

        static String allParams() {
            StringBuilder builder = new StringBuilder();
            builder.append('(');
            for(int i = 0; i < all.size(); ++i) {
                Param p = all.get(i);
                if(i > 0) {
                    builder.append(", ");
                }
                builder.append(p.name).append('=').append(p.value);
            }
            builder.append(')');
            return builder.toString();
        }

        final String name;
        final String value;

        Param(String name, String value) {
            this.name = name;
            this.value = value;
            all.add(this);
        }
    }
}
