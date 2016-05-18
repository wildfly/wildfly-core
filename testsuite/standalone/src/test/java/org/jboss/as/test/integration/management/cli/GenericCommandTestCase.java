/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.integration.management.cli;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Test generic command features of CLI.
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
@RunWith(WildflyTestRunner.class)
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
}
