/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

@RunWith(WildFlyRunner.class)
public class CtrlCTestCase {

    @Test
    public void ctrlCTestCase() throws IOException {
        CliProcessWrapper cli = new CliProcessWrapper();

        try {
            cli.executeInteractive();

            boolean closed = cli.ctrlCAndWaitForClose();

            assertTrue("Process did not terminate correctly. Output: '" + cli.getOutput() + "'", closed);
                    assertEquals("Cli process closed with unexpected exit value: " + cli.getProcessExitValue(), 0, cli.getProcessExitValue());
        }finally{
            cli.destroyProcess();
        }
    }

    @Test
    public void userCtrlCTestCase() throws IOException, InterruptedException {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort()))
                .addCliArgument("--no-local-auth");

        try{
            boolean promptFound = cli.executeInteractive("Username");
            assertTrue("Prompt isn't asking for username. Output: '" + cli.getOutput() + "'", promptFound);

            boolean closed = cli.ctrlCAndWaitForClose();

            assertTrue("Process did not terminate correctly. Output: '" + cli.getOutput()+ "'", closed);
            assertEquals("Cli process closed with unexpected exit value: " + cli.getProcessExitValue(), 1, cli.getProcessExitValue());
        }finally{
            cli.destroyProcess();
        }
    }

    @Test
    public void passCtrlCTestCase() throws IOException {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort()))
                .addCliArgument("--no-local-auth");

        try{
            boolean promptFound = cli.executeInteractive("Username");
            assertTrue("Prompt isn't asking for username. Output: '" + cli.getOutput() + "'", promptFound);

            promptFound = cli.pushLineAndWaitForResults("bob", "Password");
            assertTrue("Prompt isn't requesting password. Output: '" + cli.getOutput() + "'", promptFound);

            boolean closed = cli.ctrlCAndWaitForClose();

            assertTrue("Process did not terminate correctly. Output: '" + cli.getOutput() + "'", closed);
            assertEquals("Cli process closed with unexpected exit value: " + cli.getProcessExitValue(), 1, cli.getProcessExitValue());
        }finally{
            cli.destroyProcess();
        }
    }

}
