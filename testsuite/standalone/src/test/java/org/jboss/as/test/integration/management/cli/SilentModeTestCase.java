/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.logmanager.LogManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;


/**
 *
 * @author Alexey Loubyansky
 */
@RunWith(WildFlyRunner.class)
public class SilentModeTestCase {

    private static ByteArrayOutputStream cliOut;
    public static final String CLI_LOG_CFG = "/jboss-cli-logging.properties";
    public static final String CLI_LOG_FILE = "target" + File.separator + "jboss-cli.log";

    @BeforeClass
    public static void setup() throws Exception {
        cliOut = new ByteArrayOutputStream();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        cliOut = null;
    }

    @Test
    public void testMain() throws Exception {
        cliOut.reset();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.handle("help");
            assertFalse(cliOut.toString().isEmpty());

            cliOut.reset();
            ctx.setSilent(true);
            ctx.handle("help");
            assertTrue(cliOut.toString().isEmpty());

            cliOut.reset();
            ctx.setSilent(false);
            ctx.handle("help");
            assertFalse(cliOut.toString().isEmpty());
        } finally {
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testLogging() throws Exception {
        setupCliLogging();
        cliOut.reset();

        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);

        try {
            ctx.setCurrentDir(new File("."));
            ctx.setSilent(true);
            ctx.connectController();
            ctx.handleSafe(":read-resource");
            assertTrue(cliOut.toString().isEmpty());
            assertFalse(checkIfEmpty(new File(CLI_LOG_FILE)));
        } finally {
            ctx.terminateSession();
            cliOut.reset();
            tearDownCLiLogging();
        }
    }

    @Test
    public void testOutputTarget() throws Exception {
        cliOut.reset();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);

        File target = new File("cli_output");

        try {
            ctx.setCurrentDir(new File("."));
            ctx.setSilent(true);
            ctx.handleSafe("help > " + target.getAbsolutePath());
            assertTrue(cliOut.toString().isEmpty());
            assertFalse(checkIfEmpty(target));
        } finally {
            ctx.terminateSession();
            cliOut.reset();
            target.delete();
        }
    }

    private void setupCliLogging() throws Exception {
        File cliLogFile = new File(CLI_LOG_FILE);
        if (cliLogFile.exists()) {
            cliLogFile.delete();
        }

        InputStream is = null;
        try {
            is = getClass().getResourceAsStream(CLI_LOG_CFG);
            LogManager.getLogManager().readConfiguration(is);
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    private void tearDownCLiLogging() throws Exception {
        LogManager.getLogManager().reset();
        LogManager.getLogManager().readConfiguration();
    }

    private boolean checkIfEmpty(File file) throws Exception {
        boolean empty = false;
        FileInputStream fis = null;
        assertTrue(file.exists());
        try {
            fis = new FileInputStream(file);
            empty = fis.read() == -1;
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
        return empty;
    }
}
