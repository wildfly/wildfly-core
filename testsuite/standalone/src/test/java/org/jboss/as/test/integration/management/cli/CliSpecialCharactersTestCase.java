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

import java.io.ByteArrayOutputStream;
import java.util.function.Supplier;
import javax.inject.Inject;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.impl.CommandContextImpl;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

import static org.junit.Assert.assertTrue;
import org.wildfly.core.testrunner.ManagementClient;

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

    @Inject
    protected ManagementClient managementClient;

    private void removeTestResources(CommandContext ctx) {
        ctx.handleSafe("/system-property=" + TEST_RESOURCE_NAME + ":remove");
    }

    @Before
    public void setup() throws Exception {
        cli = new CLIWrapper(true);
        removeTestResources(cli.getCommandContext());
    }

    @After
    public void cleanup() throws Exception {
        removeTestResources(cli.getCommandContext());
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
        testSetResourceValue("Hello World!", "Hello World!", Delimiters.DOUBLE_QUOTE, cli.getCommandContext(), () -> {
            return cli.readOutput();
        });
        testSetResourceValue("Hello World!", "Hello World!", Delimiters.CURLY_BRACE, cli.getCommandContext(), () -> {
            return cli.readOutput();
        });
        testSetResourceValue("Hello\\ World!", "Hello World!", Delimiters.NONE, cli.getCommandContext(), () -> {
            return cli.readOutput();
        });
    }

    /**
     * Tests whitespace in the middle of words Regression test for
     * https://issues.jboss.org/browse/JBEAP-4536
     *
     * @throws Exception
     */
    @Test
    public void testWhitespaceInMiddleBoot() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CommandContext ctx = new CommandContextImpl(out);
        ctx.bindClient(managementClient.getControllerClient());
        Supplier<String> str = () -> {
            String s = out.toString();
            out.reset();
            return s;
        };
        testSetResourceValue("Hello World!", "Hello World!", Delimiters.DOUBLE_QUOTE, ctx, str);
        testSetResourceValue("Hello World!", "Hello World!", Delimiters.CURLY_BRACE, ctx, str);
        testSetResourceValue("Hello\\ World!", "Hello World!", Delimiters.NONE, ctx, str);
    }

    /**
     * Tests whitespace at the start/end of strings and if it is trimmed
     * Double quotes preserve whitespace, curly braces and no delimiter trims
     *
     * @throws Exception
     */
    @Test
    public void testWhitespaceTrimming() throws Exception {
        testSetResourceValue("   Hello World!   ", "   Hello World!   ", Delimiters.DOUBLE_QUOTE, cli.getCommandContext(), () -> {
            return cli.readOutput();
        });
        testSetResourceValue("   Hello World!   ", "Hello World!", Delimiters.CURLY_BRACE, cli.getCommandContext(), () -> {
            return cli.readOutput();
        });
        testSetResourceValue("   Hello\\ World!   ", "Hello World!", Delimiters.NONE, cli.getCommandContext(), () -> {
            return cli.readOutput();
        });
    }

    @Test
    public void testWhitespaceTrimmingBoot() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CommandContext ctx = new CommandContextImpl(out);
        ctx.bindClient(managementClient.getControllerClient());
        Supplier<String> str = () -> {
            String s = out.toString();
            out.reset();
            return s;
        };
        testSetResourceValue("   Hello World!   ", "   Hello World!   ", Delimiters.DOUBLE_QUOTE, ctx, str);
        testSetResourceValue("   Hello World!   ", "Hello World!", Delimiters.CURLY_BRACE, ctx, str);
        testSetResourceValue("   Hello\\ World!   ", "Hello World!", Delimiters.NONE, ctx, str);
    }

    /**
     * Tests for single quote in a property name
     *
     * @throws Exception
     */
    @Test
    public void testSingleQuotes() throws Exception {
        testSetResourceValue("It's", "It's", Delimiters.DOUBLE_QUOTE, cli.getCommandContext(), () -> {
            return cli.readOutput();
        });
        testSetResourceValue("It\\'s", "It's", Delimiters.NONE, cli.getCommandContext(), () -> {
            return cli.readOutput();
        });
        testSetResourceValue("''It's''", "''It's''", Delimiters.DOUBLE_QUOTE, cli.getCommandContext(), () -> {
            return cli.readOutput();
        });
    }

    @Test
    public void testSingleQuotesBoot() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CommandContext ctx = new CommandContextImpl(out);
        ctx.bindClient(managementClient.getControllerClient());
        Supplier<String> str = () -> {
            String s = out.toString();
            out.reset();
            return s;
        };
        testSetResourceValue("It's", "It's", Delimiters.DOUBLE_QUOTE, ctx, str);
        testSetResourceValue("It\\'s", "It's", Delimiters.NONE, ctx, str);
        testSetResourceValue("''It's''", "''It's''", Delimiters.DOUBLE_QUOTE, ctx, str);
    }

    /**
     * Tests the usage of commas inside double quotes
     *
     * @throws Exception
     */
    @Test
    public void testCommasInDoubleQuotes() throws Exception {
        testSetResourceValue("Last,First", "Last,First", Delimiters.DOUBLE_QUOTE, cli.getCommandContext(), () -> {
            return cli.readOutput();
        });
        testSetResourceValue(",,,A,B,C,D,E,F,,,", ",,,A,B,C,D,E,F,,,", Delimiters.DOUBLE_QUOTE, cli.getCommandContext(), () -> {
            return cli.readOutput();
        });
    }

    @Test
    public void testCommasInDoubleQuotesBoot() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CommandContext ctx = new CommandContextImpl(out);
        ctx.bindClient(managementClient.getControllerClient());
        Supplier<String> str = () -> {
            String s = out.toString();
            out.reset();
            return s;
        };
        testSetResourceValue("Last,First", "Last,First", Delimiters.DOUBLE_QUOTE, ctx, str);
        testSetResourceValue(",,,A,B,C,D,E,F,,,", ",,,A,B,C,D,E,F,,,", Delimiters.DOUBLE_QUOTE, ctx, str);
    }

    /**
     * Tests usage of parenthesis with all delimiter options
     *
     * @throws Exception
     */
    @Test
    public void testParenthesis() throws Exception {
        testSetResourceValue("one(1)", "one(1)", Delimiters.DOUBLE_QUOTE, cli.getCommandContext(), () -> {
            return cli.readOutput();
        });
        testSetResourceValue("one(1)", "one(1)", Delimiters.CURLY_BRACE, cli.getCommandContext(), () -> {
            return cli.readOutput();
        });
        testSetResourceValue("one\\(1\\)", "one(1)", Delimiters.NONE, cli.getCommandContext(), () -> {
            return cli.readOutput();
        });
    }

    @Test
    public void testParenthesisBoot() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CommandContext ctx = new CommandContextImpl(out);
        ctx.bindClient(managementClient.getControllerClient());
        Supplier<String> str = () -> {
            String s = out.toString();
            out.reset();
            return s;
        };
        testSetResourceValue("one(1)", "one(1)", Delimiters.DOUBLE_QUOTE, ctx, str);
        testSetResourceValue("one(1)", "one(1)", Delimiters.CURLY_BRACE, ctx, str);
        testSetResourceValue("one\\(1\\)", "one(1)", Delimiters.NONE, ctx, str);
    }

    /**
     * Tests usage of braces inside double quotes
     *
     * @throws Exception
     */
    @Test
    public void testBraces() throws Exception {
        testSetResourceValue("{braces}", "{braces}", Delimiters.DOUBLE_QUOTE, cli.getCommandContext(), () -> {
            return cli.readOutput();
        });
    }

    @Test
    public void testBracesBoot() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CommandContext ctx = new CommandContextImpl(out);
        ctx.bindClient(managementClient.getControllerClient());
        Supplier<String> str = () -> {
            String s = out.toString();
            out.reset();
            return s;
        };
        testSetResourceValue("{braces}", "{braces}", Delimiters.DOUBLE_QUOTE, ctx, str);
    }

    /**
     * Tests setting resource value and verifies it was saved successfully in non-interactive mode
     *
     * @param input     property value to set via CLI
     * @param expected  property value expected to be set
     * @param delimiter type of delimiter to use for property name escaping
     * @throws CommandLineException
     */
    private void testSetResourceValue(String input, String expected, Delimiters delimiter,
            CommandContext ctx, Supplier<String> provider) throws Exception {
        removeTestResources(ctx);
        ctx.handleSafe("/system-property=" + TEST_RESOURCE_NAME
                +                ":add(value=" + delimiter.getStartDelimiter() + input + delimiter.getEndDelimiter() + ")");
        String setOutcome = provider.get();
        assertTrue("failed to add resource " + setOutcome, setOutcome.contains("success"));
        ctx.handleSafe("/system-property=" + TEST_RESOURCE_NAME + ":read-attribute(name=value)");
        String readResult = provider.get();
        assertTrue("expected value not found", readResult.contains(expected));
        assertTrue("failed to read attribute", readResult.contains("success"));
        ctx.handleSafe("/system-property=" + TEST_RESOURCE_NAME + ":remove");
        String removeResult = provider.get();
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
