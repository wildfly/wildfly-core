/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class MiscStringContentTestCase extends BaseStateParserTest {

    @Test
    public void testDefault() throws Exception {

        parse("name");

        assertNotNull(result);
        assertEquals(1, result.children.size());
        assertNull(result.buffer);

        ParsedTerm child = result.children.get(0);
        assertNotNull(child);
        assertEquals(0, child.children.size());
        assertEquals("name", child.buffer.toString());
    }

    @Test
    public void testQuotes() throws Exception {
        parse("\"name\"");

        assertNotNull(result);
        assertEquals(1, result.children.size());
        assertNull(result.buffer);

        ParsedTerm child = result.children.get(0);
        assertNotNull(child);
        assertTrue(child.children.isEmpty());
        assertEquals("name", child.buffer.toString());
    }

    @Test
    public void testParathesis() throws Exception {
        parse("(name)");
        assertNotNull(result);
        assertEquals(1, result.children.size());
        assertNull(result.buffer);

        ParsedTerm child = result.children.get(0);
        assertNotNull(child);
        assertTrue(child.children.isEmpty());
        assertEquals("name", child.buffer.toString());
    }

    @Test
    public void testMix() throws Exception {
        parse("a (b) c [d[e(f{g}h)i]k]l ({[]})");

        assertNotNull(result);
        assertEquals(1, result.children.size());
        assertNull(result.buffer);

        ParsedTerm firstChild = result.children.get(0);
        assertNotNull(firstChild);
        assertEquals(3, firstChild.children.size());
        assertEquals("a  c l ", firstChild.buffer.toString());

        ParsedTerm child = firstChild.children.get(0);
        assertNotNull(child);
        assertTrue(child.children.isEmpty());
        assertEquals("b", child.buffer.toString());

        child = firstChild.children.get(1);
        assertNotNull(child);
        assertEquals(1, child.children.size());
        assertEquals("dk", child.buffer.toString());

        child = child.children.get(0);
        assertNotNull(child);
        assertEquals(1, child.children.size());
        assertEquals("ei", child.buffer.toString());

        child = child.children.get(0);
        assertNotNull(child);
        assertEquals(1, child.children.size());
        assertEquals("fh", child.buffer.toString());

        child = child.children.get(0);
        assertNotNull(child);
        assertEquals(0, child.children.size());
        assertEquals("g", child.buffer.toString());

        child = firstChild.children.get(2);
        assertNotNull(child);
        assertEquals(1, child.children.size());
        assertNull(child.buffer);

        child = child.children.get(0);
        assertNotNull(child);
        assertEquals(1, child.children.size());
        assertNull(child.buffer);

        child = child.children.get(0);
        assertNotNull(child);
        assertEquals(0, child.children.size());
        assertNull(child.buffer);
    }

    @Test
    public void testEscapingQuotesInQuotes() throws Exception {
        parse("\"a\\\"b\"");

        assertNotNull(result);
        assertEquals(1, result.children.size());
        assertNull(result.buffer);

        ParsedTerm child = result.children.get(0);
        assertNotNull(child);
        assertEquals(1, child.children.size());
        assertEquals("ab", child.buffer.toString());

        child = child.children.get(0);
        assertNotNull(child);
        assertEquals(0, child.children.size());
        assertEquals("\"", child.buffer.toString());
    }

    @Test
    public void testEscapingQuotesInUnquotedContent() throws Exception {
        parse("a\\\"b");

        assertNotNull(result);
        assertEquals(1, result.children.size());
        assertNull(result.buffer);

        ParsedTerm child = result.children.get(0);
        assertNotNull(child);
        assertEquals(1, child.children.size());
        assertEquals("ab", child.buffer.toString());

        child = child.children.get(0);
        assertNotNull(child);
        assertEquals(0, child.children.size());
        assertEquals("\"", child.buffer.toString());
    }

    @Test
    public void testBracketsInQuotes() throws Exception {
        parse("\"({[]})\"");
        assertNotNull(result);
        assertEquals(1, result.children.size());
        assertNull(result.buffer);

        ParsedTerm child = result.children.get(0);
        assertNotNull(child);
        assertEquals(0, child.children.size());
        assertEquals("({[]})", child.buffer.toString());
    }

    @Test
    public void testNewLineChar() throws Exception {
        parse("a\\nb");
        assertNotNull(result);
        assertEquals("a\nb", result.valueAsString.toString());
    }

    @Test
    public void testTabChar() throws Exception {
        parse("a\\tb");
        assertNotNull(result);
        assertEquals("a\tb", result.valueAsString.toString());
    }

    @Test
    public void testBackspaceChar() throws Exception {
        parse("a\\bb");
        assertNotNull(result);
        assertEquals("a\bb", result.valueAsString.toString());
    }

    @Test
    public void testCarriageReturnChar() throws Exception {
        parse("a\\rb");
        assertNotNull(result);
        assertEquals("a\rb", result.valueAsString.toString());
    }

    @Test
    public void testFormfeedChar() throws Exception {
        parse("a\\fb");
        assertNotNull(result);
        assertEquals("a\fb", result.valueAsString.toString());
    }
}
