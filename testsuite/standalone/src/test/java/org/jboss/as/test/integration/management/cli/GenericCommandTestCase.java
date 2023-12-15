/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Test generic command features of CLI.
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
@RunWith(WildFlyRunner.class)
public class GenericCommandTestCase extends AbstractCliTestBase {

    @BeforeClass
    public static void before() throws Exception {
        AbstractCliTestBase.initCLI();
    }

    @AfterClass
    public static void after() throws Exception {
        cli.sendLine("reload");
        AbstractCliTestBase.closeCLI();
    }

    @Test
    public void testMain() throws Exception {

        // add generic socket-binding command
        cli.sendLine("command add --node-type=/socket-binding-group=standard-sockets/socket-binding --command-name=socket-binding");

        // test created socket-binding add command
        cli.sendLine("socket-binding add --name=cli-test --fixed-port=false --interface=management --port=${jboss.management.http.port:9990}");

        assertTrue(cli.isValidPath("socket-binding-group", "standard-sockets", "socket-binding", "cli-test"));

        cli.sendLine("socket-binding read-resource --name=cli-test");

        final String readOutput = cli.readOutput();
        assertTrue(readOutput.contains("fixed-port=false"));
        assertTrue(readOutput.contains("interface=management"));
        assertTrue(readOutput.contains("name=cli-test"));
        assertTrue(readOutput.contains("port=expression \"${jboss.management.http.port:9990}\""));

        // test socket-binding remove command
        cli.sendLine("socket-binding remove --name=cli-test");

        assertFalse(cli.isValidPath("socket-binding-group", "standard-sockets", "socket-binding", "cli-test"));

        cli.sendLine("command remove --command-name=socket-binding");
    }

    @Test
    public void testNodeChild() throws Exception {

        // add generic authorization command
        cli.sendLine("command add --node-child=/core-service=management/access=authorization --command-name=authorization");

        // test read-resource
        cli.sendLine("authorization read-resource");

        final String readOutput = cli.readOutput();
        assertTrue(readOutput.contains("use-identity-roles=false"));
        assertTrue(readOutput.contains("permission-combination-policy=permissive"));

        // test write attributes
        try {
            cli.sendLine("authorization --use-identity-roles=true --permission-combination-policy=rejecting");

            // test read-resource
            cli.sendLine("authorization read-resource");

            final String readOutput2 = cli.readOutput();
            assertTrue(readOutput2.contains("use-identity-roles=true"));
            assertTrue(readOutput2.contains("permission-combination-policy=rejecting"));
        } finally {
            cli.sendLine("/core-service=management/access=authorization:undefine-attribute(name=use-identity-roles");
            cli.sendLine("/core-service=management/access=authorization:undefine-attribute(name=permission-combination-policy");
            cli.sendLine("command remove --command-name=authorization");
        }
    }

    @Test
    public void testNoNodeChild() throws Exception {

        // Attempt to add a command for non existing resource.
        boolean success = cli.sendLine("command add --node-child=/system-property=foo --command-name=foo", true);
        assertFalse(success);

        // Attempt to add a child command for a type
        success = cli.sendLine("command add --node-child=/system-property --command-name=foo", true);
        assertFalse(success);

        cli.sendLine("/system-property=foo:add(value=foo");
        try {
            cli.sendLine("command add --node-child=/system-property=foo --command-name=foo");
            cli.sendLine("foo read-resource");
            final String readOutput = cli.readOutput();
            assertTrue(readOutput.contains("value=foo"));
        } finally {
            cli.sendLine("/system-property=foo:remove");
            cli.sendLine("command remove --command-name=foo");
        }

    }
}
