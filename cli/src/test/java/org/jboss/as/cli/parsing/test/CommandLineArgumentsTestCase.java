/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.test;

import java.util.List;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class CommandLineArgumentsTestCase {

    @Test
    public void testDefault() throws Exception {

        ParsedCommandLine args = parse("deploy ../../../../testsuite/smoke/target/deployments/test-deployment.sar --name=my.sar --disabled --runtime-name=myrt.sar --force");
        assertTrue(args.hasProperties());
        assertTrue(args.hasProperty("--name"));
        assertTrue(args.hasProperty("--runtime-name"));
        assertTrue(args.hasProperty("--disabled"));
        assertTrue(args.hasProperty("--force"));

        List<String> otherArgs = args.getOtherProperties();
        assertEquals(1, otherArgs.size());
        assertEquals("../../../../testsuite/smoke/target/deployments/test-deployment.sar", otherArgs.get(0));

        assertNull(args.getOutputTarget());
    }

    @Test
    public void testOutputTarget() throws Exception {

        ParsedCommandLine args = parse("cmd --name=value value1 --name1 > output.target");
        assertTrue(args.hasProperties());
        assertTrue(args.hasProperty("--name"));
        assertEquals("value", args.getPropertyValue("--name"));
        assertTrue(args.hasProperty("--name1"));
        assertTrue(args.getPropertyValue("--name1").equals("true"));

        List<String> otherArgs = args.getOtherProperties();
        assertEquals(1, otherArgs.size());
        assertEquals("value1", otherArgs.get(0));

        assertTrue("No operator", args.hasOperator());
    }

    protected ParsedCommandLine parse(String line) {
        DefaultCallbackHandler args = new DefaultCallbackHandler();
        try {
            args.parse(null, line);
        } catch (CommandFormatException e) {
            e.printStackTrace();
            org.junit.Assert.fail(e.getLocalizedMessage());
        }
        return args;
    }
}
