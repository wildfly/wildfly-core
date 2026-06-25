/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.scripts.test;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.wildfly.common.test.ServerHelper;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;

/**
 * @author <a href="mailto:yborgess@redhat.com">Yeray Borges</a>
 */
@RunWith(Parameterized.class)
public class StandaloneSerialFilterScriptTestCase extends ScriptTestCase {
    private static final Function<ModelControllerClient, Boolean> STANDALONE_CHECK = ServerHelper::isStandaloneRunning;
    private static final String SCRIPT_NAME = "standalone";
    private static final PathAddress CORE_SERVICE_PLATFORM_MBEAN = PathAddress.pathAddress("core-service", "platform-mbean");
    private static final PathAddress TYPE_RUNTIME = PathAddress.pathAddress("type", "runtime");

    @Parameters
    public static Collection<Object> data() {
        if (TestSuiteEnvironment.isWindows()) {
            return List.of(
                    Map.of(),
                    Map.of("JAVA_OPTS", "-Djdk.serialFilter=maxbytes=10999999;maxdepth=127;maxarray=99999;maxrefs=299999"),
                    Map.of("DISABLE_JDK_SERIAL_FILTER", "true")
            );
        } else {
            return List.of(
                    Map.of(),
                    Map.of("JAVA_OPTS", "-Djdk.serialFilter=\"maxbytes=10999999;maxdepth=127;maxarray=99999;maxrefs=299999\""),
                    Map.of("DISABLE_JDK_SERIAL_FILTER", "true")
            );
        }
    }

    @Parameter
    public Map<String, String> env;

    public StandaloneSerialFilterScriptTestCase() throws IOException {
        super(SCRIPT_NAME);
    }

    @Override
    void testScript(ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        script.start(this::propertiesCheck, env, ServerHelper.DEFAULT_SERVER_JAVA_OPTS);
        final var stdout = script.getStdoutAsString();
        if (env.containsKey("JAVA_OPTS")) {
            boolean ok = stdout.contains(env.get("JAVA_OPTS"));
            Assert.assertTrue("Expected to find " + env.get("JAVA_OPTS") + " in the JVM parameters for a server started with " + script.getLastExecutedCmd() + "\nThe server output was: \n" + stdout, ok);
        } else if (env.containsKey("DISABLE_JDK_SERIAL_FILTER")) {
            boolean ok = !stdout.contains("-Djdk.serialFilter=");
            Assert.assertTrue("Expected not to find -Djdk.serialFilter= in the JVM parameters for a server started with " + script.getLastExecutedCmd() + "\nThe server output was: \n" + stdout, ok);
        } else {
            // Verify that there is a @jdk.serialFilter file argument but no -Djdk.serialFilter parameter
            String filterParameterPattern = String.format("(?s).*\\s+%s[^\"]+%s%s\"[\"\\s].*", TestSuiteEnvironment.isWindows() ? "\"@" : "@\"", Pattern.quote(File.separator), Pattern.quote("jdk.serialFilter"));
            boolean ok = stdout.matches(filterParameterPattern) && !stdout.contains("-Djdk.serialFilter=");
            Assert.assertTrue(filterParameterPattern + "\t" + "Expected to find the @jdk.serialFilter path in the JVM parameters for a server started with " + script.getLastExecutedCmd() + "\nThe server output was: \n" + stdout, ok);
        }
    }

    boolean propertiesCheck(final ModelControllerClient client) {
        boolean running = STANDALONE_CHECK.apply(client);
        if (!running) {
            return false;
        }
        if (env.isEmpty()) {
            // The default case, it loads serialFilter from bin/jdk.serialFilter
            try {
                ModelNode op = Util.getReadAttributeOperation(CORE_SERVICE_PLATFORM_MBEAN.append(TYPE_RUNTIME), "input-arguments");
                ModelNode result = client.execute(op);
                checkSuccessful(result);
                result = result.get(ClientConstants.RESULT);
                return result.asString().contains("-Djdk.serialFilter=");
            } catch (IOException | UnsuccessfulOperationException e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }

    void checkSuccessful(final ModelNode result) throws UnsuccessfulOperationException {
        if (!ClientConstants.SUCCESS.equals(result.get(ClientConstants.OUTCOME).asString())) {
            throw new UnsuccessfulOperationException(result.get(ClientConstants.FAILURE_DESCRIPTION).toString());
        }
    }
}
