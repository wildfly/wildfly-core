/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

@RunWith(WildflyTestRunner.class)
public class ColorOutputTestCase {

    private static CliProcessWrapper cli;
    private static String hostAndPort = TestSuiteEnvironment.getServerAddress() + ":" + TestSuiteEnvironment.getServerPort();

    /**
     * Initialize CommandContext before all tests
     */
    @BeforeClass
    public static void init() throws Exception {
        cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + hostAndPort);
        cli.executeInteractive();
    }

    /**
     * Terminate CommandContext after all tests are executed
     */
    @AfterClass
    public static void close() throws Exception {
        cli.destroyProcess();
    }

    @Test
    public void requiredArguments() throws Exception {

        cli.clearOutput();
        cli.pushLineAndWaitForResults(":write-attribute(\t");
        String out = cli.getOutput();

        Assert.assertTrue("Required attribute is not colorized. The output was: " + out,
                out.contains("\u001B[1;95;109mname*\u001B[0m"));
    }

    @Test
    public void cliPrompt() {
        Assert.assertEquals("[\u001B[;94;109mstandalone\u001B[0m@\u001B[;94;109m" + hostAndPort + " \u001B[0m /]",
                cli.getCurrentPrompt().trim());
    }

    @Test
    public void batchPrompt() throws Exception {

        cli.pushLineAndWaitForResults("batch");
        try {
            Assert.assertEquals("[\u001B[;94;109mstandalone\u001B[0m@\u001B[;94;109m" + hostAndPort + " \u001B[0m /\u001B[;92;109m #\u001B[0m]",
                    cli.getCurrentPrompt().trim());
        } finally {
            cli.pushLineAndWaitForResults("discard-batch");
        }
    }

    @Test
    public void flowControlPrompt() throws Exception {

        cli.pushLineAndWaitForResults("if outcome==failed of /system-property=test:read-resource");
        try {
            cli.pushLineAndWaitForResults("echo \"Not Exists\"");
            Assert.assertEquals("[\u001B[;94;109mstandalone\u001B[0m@\u001B[;94;109m" + hostAndPort + " \u001B[0m /\u001B[;92;109m ...\u001B[0m]",
                    cli.getCurrentPrompt().trim());
        } finally {
            cli.pushLineAndWaitForResults("end-if");
        }
    }

}
