/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.scripts.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.jboss.as.controller.client.ModelControllerClient;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.wildfly.common.test.ServerHelper;

@RunWith(Parameterized.class)
public class StandaloneDebugScriptTestCase extends ScriptTestCase {
    private static final Function<ModelControllerClient, Boolean> STANDALONE_CHECK = ServerHelper::isStandaloneRunning;
    private static final String DEBUG_LISTEN_PREFIX = "Listening for transport dt_socket at address: ";

    @Parameter(0)
    public String[] debugArgs;

    @Parameter(1)
    public String expectedPort;

    public StandaloneDebugScriptTestCase() {
        super("standalone");
    }

    @Parameters(name = "{0} -> port {1}")
    public static Collection<Object[]> data() {
        return List.of(
                new Object[] {new String[] {"--debug"}, "8787"},
                new Object[] {new String[] {"--debug", "9797"}, "9797"},
                new Object[] {new String[] {"-Dtest=test", "--debug"}, "8787"},
                new Object[] {new String[] {"-Dtest=test", "--debug", "9797"}, "9797"}
        );
    }

    @Override
    void testScript(final ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        final List<String> args = new ArrayList<>(Arrays.asList(ServerHelper.DEFAULT_SERVER_JAVA_OPTS));
        args.addAll(Arrays.asList(debugArgs));
        script.start(STANDALONE_CHECK, args.toArray(String[]::new));

        Assert.assertTrue("The process is not running and should be", script.isAlive());

        final var stdout = script.getStdoutAsString();
        final String expectedMessage = DEBUG_LISTEN_PREFIX + expectedPort;
        Assert.assertTrue("Expected to find '" + expectedMessage + "' for a server started with " + script.getLastExecutedCmd() + "\nThe server output was:\n" + stdout, stdout.contains(expectedMessage));
    }
}

