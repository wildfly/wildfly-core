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

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertTrue;

import org.hamcrest.MatcherAssert;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
@RunWith(WildflyTestRunner.class)
public class HelpTestCase extends AbstractCliTestBase {

    private static final String[] COMMANDS = {
        "cn", "deploy", "help", "history", "ls", "pwn", "quit", "undeploy", "version"
    };

    @BeforeClass
    public static void before() throws Exception {
        AbstractCliTestBase.initCLI();
    }

    @AfterClass
    public static void after() throws Exception {
        AbstractCliTestBase.closeCLI();
    }

    @Test
    public void testLegacyHelpCommand() throws Exception {
        cli.sendLine("help");
        String help = cli.readOutput();
        for (String cmd : COMMANDS) assertTrue("Command '" + cmd + "' missing in help.", help.contains(cmd));
    }

    @Test
    public void testLegacyDeployHelp() throws Exception {
        testCmdHelp("deploy");
    }

    @Test
    public void testLegacyUndeployHelp() throws Exception {
        testCmdHelp("undeploy");
    }

    @Test
    public void testLegacyDataSourceHelp() throws Exception {
        testCmdHelp("data-source");
    }

    @Test
    public void testLegacyXaDataSourceHelp() throws Exception {
        testCmdHelp("xa-data-source");
    }

    @Test
    public void testLegacyCnHelp() throws Exception {
        testCmdHelp("cn");
    }

    private void testCmdHelp(String cmd) throws Exception {
        cli.sendLine(cmd + " --help");
        String help = cli.readOutput();
        assertTrue("Command " + cmd + " help does not have synopsis section.", help.contains("SYNOPSIS"));
        assertTrue("Command " + cmd + " help does not have description section.", help.contains("DESCRIPTION"));
        assertTrue("Command " + cmd + " help does not have arguments section.", help.contains("ARGUMENTS"));
    }

    /**
     * help command works correctly without any command or operation name
     */
    @Test
    public void pureHelpTest() {
        universalCliTest("help", false, true, true, true, false);
    }

    /**
     * help command works correctly with command name, but without action name
     */
    @Test
    public void helpForCommandTest() {
        universalCliTest("help deployment", true, false, false, false, false);
    }

    /**
     * help command works correctly with command and action name
     */
    @Test
    public void helpForCommandAndActionTest() {
        universalCliTest("help deployment info", false, false, true, true, false);
    }

    /**
     * help command works correctly with operation name
     */
    @Test
    public void helpForOperationTest() {
        universalCliTest("help :read-resource", false, false, true, false, true);
    }

    /**
     * checks output of help operation with invalid command
     */
    @Test
    public void invalidCommandTest() {
        testErrorHandling("help nonsence");
    }

    /**
     * checks output of help operation with invalid action
     */
    @Test
    public void invalidActionTest() {
        testErrorHandling("help deployment nonsence");
    }

    /**
     * checks output of help operation with invalid operation
     */
    @Test
    public void invalidOperationTest() {
        cli.sendLine("help :nonsence");
        MatcherAssert.assertThat("Wrong error message", cli.readOutput(), containsString("Error getting operation help"));
    }

    /**
     * Call help command for invalid command or action
     */
    private void testErrorHandling(String cmd) {
        try {
            cli.sendLineForValidation(cmd);
            throw new RuntimeException("CLI doesn't throw exception if help for non-existing command is called");
        } catch (CommandLineException e) {
            MatcherAssert.assertThat("Wrong error message", e.getMessage(), containsString("not exist"));
        }
    }

    /**
     * Check help message
     */
    private void universalCliTest(String cmd, boolean assertActions, boolean assertAliases, boolean assertOptions,
                                  boolean assertArgument, boolean assertReturnValue) {
        cli.sendLine(cmd);
        String help = cli.readOutput();
        assertTrue(getErrMsg(cmd, "synopsis"), help.contains("SYNOPSIS"));
        assertTrue(getErrMsg(cmd, "description"), help.contains("DESCRIPTION"));
        assertTrue(getErrMsg(cmd, "actions"), !assertActions || help.contains("ACTIONS"));
        assertTrue(getErrMsg(cmd, "aliases"), !assertAliases || help.contains("ALIASES"));
        assertTrue(getErrMsg(cmd, "options"), !assertOptions || help.contains("OPTIONS"));
        assertTrue(getErrMsg(cmd, "arguments"), !assertArgument || help.contains("ARGUMENT"));
        assertTrue(getErrMsg(cmd, "return value"), !assertReturnValue || help.contains("RETURN VALUE"));
    }

    /**
     * Generate assert error message. This method is used if some section is missing in help message.
     */
    private String getErrMsg(String cmd, String sectionName) {
        return String.format("Command %s does not have %s section.", cmd, sectionName);
    }
}
