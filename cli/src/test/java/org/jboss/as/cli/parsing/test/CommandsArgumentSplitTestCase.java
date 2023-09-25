/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.test;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.jboss.as.cli.Util;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class CommandsArgumentSplitTestCase {

    @Test
    public void testSingleOperationWithParameters() throws Exception {
        final String op = ":read-resource(recursive=false,include-defaults=true)";
        final List<String> split = split(op);
        assertEquals(1, split.size());
        assertEquals(op, split.get(0));
    }

    @Test
    public void testCommaSeparatedWords() throws Exception {
        final List<String> words = split("one,two,three");
        assertEquals(3, words.size());
        assertEquals("one", words.get(0));
        assertEquals("two", words.get(1));
        assertEquals("three", words.get(2));
    }

    @Test
    public void testParenthesesCommaSeparatedWords() throws Exception {
        final List<String> words = split("(one,two),three");
        assertEquals(2, words.size());
        assertEquals("(one,two)", words.get(0));
        assertEquals("three", words.get(1));
    }

    @Test
    public void testCurliesCommaSeparatedWords() throws Exception {
        final List<String> words = split("{one,two},three");
        assertEquals(2, words.size());
        assertEquals("{one,two}", words.get(0));
        assertEquals("three", words.get(1));
    }

    @Test
    public void testBracketsCommaSeparatedWords() throws Exception {
        final List<String> words = split("[one,two],three");
        assertEquals(2, words.size());
        assertEquals("[one,two]", words.get(0));
        assertEquals("three", words.get(1));
    }

    @Test
    public void testQuotesCommaSeparatedWords() throws Exception {
        final List<String> words = split("\"one,two\",three");
        assertEquals(2, words.size());
        assertEquals("\"one,two\"", words.get(0));
        assertEquals("three", words.get(1));
    }

    @Test
    public void testEscapeCommaSeparatedWords() throws Exception {
        final List<String> words = split("one\\,two,three");
        assertEquals(2, words.size());
        assertEquals("one\\,two", words.get(0));
        assertEquals("three", words.get(1));
    }

    protected List<String> split(String line) {
        return Util.splitCommands(line);
    }

}
