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

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jboss.as.cli.Util;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.wildfly.core.launcher.CliCommandBuilder;
import org.wildfly.core.launcher.Launcher;


/**
 * @author Alexey Loubyansky
 */
public class CliScriptTestBase {

    private static final int CLI_PROC_TIMEOUT = 10000;
    private static final int STATUS_CHECK_INTERVAL = 25;

    private String cliOutput;


    protected void assertSuccess(String cmd) {
        assertEquals(0, execute(cmd, true));
    }

    protected void assertFailure(String cmd) {
        assertTrue(execute(cmd, false) != 0);
    }

    protected String getLastCommandOutput() {
        return cliOutput;
    }

    protected int execute(String cmd) {
        return execute(cmd, false);
    }

    protected int execute(String cmd, boolean logFailure) {
        return execute(true, cmd, logFailure);
    }

    protected int execute(boolean connect, String cmd) {
        return execute(connect, cmd, false);
    }

    protected int execute(boolean connect, String cmd, boolean logFailure) {
        return execute(TestSuiteEnvironment.getServerAddress(), TestSuiteEnvironment.getServerPort(), connect, cmd, logFailure);
    }

    protected int execute(boolean connect, String cmd, boolean logFailure, Map<String,String> props) {
        return execute(TestSuiteEnvironment.getServerAddress(), TestSuiteEnvironment.getServerPort(), connect, cmd, logFailure, props);
    }

    protected int execute(String host, int port, boolean connect, String cmd, boolean logFailure) {
        return execute(host, port, connect, cmd, logFailure, null);
    }

    protected int execute(String host, int port, boolean connect, String cmd, boolean logFailure, Map<String,String> props) {
        cliOutput = null;
        final String jbossDist = TestSuiteEnvironment.getSystemProperty("jboss.dist");
        if (jbossDist == null) {
            fail("jboss.dist system property is not set");
        }

        final CliCommandBuilder commandBuilder = CliCommandBuilder.of(jbossDist);

        String modulePath = TestSuiteEnvironment.getSystemProperty("module.path");
        if (modulePath != null) {
            commandBuilder.setModuleDirs(modulePath.split(Pattern.quote(File.pathSeparator)));
        }

        final List<String> ipv6Args = new ArrayList<>();
        TestSuiteEnvironment.getIpv6Args(ipv6Args);
        if (!ipv6Args.isEmpty()) {
            commandBuilder.addJavaOptions(ipv6Args);
        }

        // Set the CLI configuration path
        final Path path = Paths.get(jbossDist, "bin", "jboss-cli.xml");
        commandBuilder.addJavaOptions("-Djboss.cli.config=" + path);
        // Add additional system properties
        if (props != null && !props.isEmpty()) {
            props.forEach((key, value) -> commandBuilder.addJavaOption(String.format("-D%s=%s", key, value)));
        }
        if (connect) {
            commandBuilder.setConnection(host, port);
        } else {
            commandBuilder.setController(host, port);
        }
        commandBuilder.addCliArgument(cmd);

        Process cliProc = null;
        try {
            cliProc = Launcher.of(commandBuilder)
                    .setRedirectErrorStream(true)
                    .launch();
        } catch (IOException e) {
            fail("Failed to start CLI process: " + e.getLocalizedMessage());
        }

        final InputStream cliStream = cliProc.getInputStream();
        final StringBuilder cliOutBuf = new StringBuilder();
        boolean wait = true;
        int runningTime = 0;
        int exitCode = 0;
        do {
            try {
                Thread.sleep(STATUS_CHECK_INTERVAL);
            } catch (InterruptedException e) {
                fail("Interrupted");
            }
            runningTime += STATUS_CHECK_INTERVAL;
            readStream(cliOutBuf, cliStream);
            try {
                exitCode = cliProc.exitValue();
                wait = false;
                readStream(cliOutBuf, cliStream);
            } catch(IllegalThreadStateException e) {
                // cli still working
            }
            if(wait && runningTime >= CLI_PROC_TIMEOUT) {
                readStream(cliOutBuf, cliStream);
                cliOutput = cliOutBuf.toString();
                cliProc.destroy();
                wait = false;
                if(logFailure) {
                    logErrors(cmd, cliProc);
                }
                fail("The cli process has timed out in " + runningTime);
            }
        } while(wait);

        cliOutput = cliOutBuf.toString();
        if (logFailure && exitCode != 0) {
            logErrors(cmd, cliProc);
        }
        return exitCode;
    }

    protected void logErrors(String cmd, Process cliProc) {
        System.out.println("Failed to execute '" + cmd + "'");
        System.out.println("Command's output: '" + cliOutput + "'");

        java.io.InputStreamReader isr = new java.io.InputStreamReader(cliProc.getErrorStream());
        java.io.BufferedReader br = new java.io.BufferedReader(isr);
        String line=null;
        try {
            line = br.readLine();
            if(line == null) {
                System.out.println("No output data for the command.");
            } else {
                StringBuilder buf = new StringBuilder(line);
                while((line = br.readLine()) != null) {
                    buf.append(Util.LINE_SEPARATOR);
                    buf.append(line);
                }
                System.out.println("Command's error log: '" + buf + "'");

            }
        } catch (IOException e) {
            fail("Failed to read command's error output: " + e.getLocalizedMessage());
        }
    }

    protected void readStream(final StringBuilder cliOutBuf, InputStream cliStream) {
        java.io.InputStreamReader isr = new java.io.InputStreamReader(cliStream);
        java.io.BufferedReader br = new java.io.BufferedReader(isr);
        String line=null;
        try {
            while ((line = br.readLine()) != null) {
                cliOutBuf.append(line).append(Util.LINE_SEPARATOR);
            }
        } catch (IOException e) {
            fail("Failed to read command's output: " + e.getLocalizedMessage());
        }
    }
}
