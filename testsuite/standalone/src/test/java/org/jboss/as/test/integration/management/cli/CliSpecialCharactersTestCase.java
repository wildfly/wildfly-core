/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.integration.management.cli;

import org.jboss.as.cli.CommandLineException;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

import static org.junit.Assert.assertTrue;

/**
 * Tests for setting values containing special characters via CLI
 * Regression testing for https://issues.jboss.org/browse/JBEAP-4536
 *
 * @author Martin Schvarcbacher
 */
@RunWith(WildflyTestRunner.class)
public class CliSpecialCharactersTestCase {
    private static final String TEST_RESOURCE_NAME = "test_resource_special_chars";
    private CLIWrapper cli;

    private void removeTestResources() {
        cli.sendLine("/system-property=" + TEST_RESOURCE_NAME + ":remove", true);
    }

    @Before
    public void setup() throws Exception {
        cli = new CLIWrapper(true);
        removeTestResources();
    }

    @After
    public void cleanup() throws Exception {
        removeTestResources();
        cli.close();
    }

    /**
     * Tests whitespace in the middle of words
     * Regression test for https://issues.jboss.org/browse/JBEAP-4536
     *
     * @throws Exception
     */
    @Test
    public void testWhitespaceInMiddle() throws Exception {
        testSetResourceValue("Hello World!", "Hello World!", Delimiters.DOUBLE_QUOTE);
        testSetResourceValue("Hello World!", "Hello World!", Delimiters.CURLY_BRACE);
        testSetResourceValue("Hello\\ World!", "Hello World!", Delimiters.NONE);
    }

    /**
     * Tests whitespace at the start/end of strings and if it is trimmed
     * Double quotes preserve whitespace, curly braces and no delimiter trims
     *
     * @throws Exception
     */
    @Test
    public void testWhitespaceTrimming() throws Exception {
        testSetResourceValue("   Hello World!   ", "   Hello World!   ", Delimiters.DOUBLE_QUOTE);
        testSetResourceValue("   Hello World!   ", "Hello World!", Delimiters.CURLY_BRACE);
        testSetResourceValue("   Hello\\ World!   ", "Hello World!", Delimiters.NONE);
    }

    /**
     * Tests for single quote in a property name
     *
     * @throws Exception
     */
    @Test
    public void testSingleQuotes() throws Exception {
        testSetResourceValue("It's", "It's", Delimiters.DOUBLE_QUOTE);
        testSetResourceValue("It\\'s", "It's", Delimiters.NONE);
        testSetResourceValue("''It's''", "''It's''", Delimiters.DOUBLE_QUOTE);
    }

    /**
     * Tests the usage of commas inside double quotes
     *
     * @throws Exception
     */
    @Test
    public void testCommasInDoubleQuotes() throws Exception {
        testSetResourceValue("Last,First", "Last,First", Delimiters.DOUBLE_QUOTE);
        testSetResourceValue(",,,A,B,C,D,E,F,,,", ",,,A,B,C,D,E,F,,,", Delimiters.DOUBLE_QUOTE);
    }

    /**
     * Tests usage of parenthesis with all delimiter options
     *
     * @throws Exception
     */
    @Test
    public void testParenthesis() throws Exception {
        testSetResourceValue("one(1)", "one(1)", Delimiters.DOUBLE_QUOTE);
        testSetResourceValue("one(1)", "one(1)", Delimiters.CURLY_BRACE);
        testSetResourceValue("one\\(1\\)", "one(1)", Delimiters.NONE);
    }

    /**
     * Tests usage of braces inside double quotes
     *
     * @throws Exception
     */
    @Test
    public void testBraces() throws Exception {
        testSetResourceValue("{braces}", "{braces}", Delimiters.DOUBLE_QUOTE);
    }

    /**
     * Tests setting resource value and verifies it was saved successfully in non-interactive mode
     *
     * @param input     property value to set via CLI
     * @param expected  property value expected to be set
     * @param delimiter type of delimiter to use for property name escaping
     * @throws CommandLineException
     */
    private void testSetResourceValue(String input, String expected, Delimiters delimiter) throws CommandLineException {
        removeTestResources();
        cli.sendLine("/system-property=" + TEST_RESOURCE_NAME +
                ":add(value=" + delimiter.getStartDelimiter() + input + delimiter.getEndDelimiter() + ")");
        String setOutcome = cli.readOutput();
        assertTrue("failed to add resource", setOutcome.contains("success"));
        cli.sendLine("/system-property=" + TEST_RESOURCE_NAME + ":read-attribute(name=value)");
        String readResult = cli.readOutput();
        assertTrue("expected value not found", readResult.contains(expected));
        assertTrue("failed to read attribute", readResult.contains("success"));
        cli.sendLine("/system-property=" + TEST_RESOURCE_NAME + ":remove");
        String removeResult = cli.readOutput();
        assertTrue("failed to remove resource", removeResult.contains("success"));
    }

    private enum Delimiters {
        NONE("", ""),
        DOUBLE_QUOTE("\"", "\""),
        CURLY_BRACE("{", "}");

        private final String startDelimiter;
        private final String endDelimiter;

        Delimiters(String startDelimiter, String endDelimiter) {
            this.startDelimiter = startDelimiter;
            this.endDelimiter = endDelimiter;
        }

        public String getStartDelimiter() {
            return startDelimiter;
        }

        public String getEndDelimiter() {
            return endDelimiter;
        }
    }
}
