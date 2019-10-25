/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.scripts.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(Parameterized.class)
public class StandaloneScriptTestCase extends ScriptTestCase {
    private static final boolean MODULAR_JVM;

    static {
        final String javaSpecVersion = System.getProperty("java.specification.version");
        // Shouldn't happen, but we'll assume we're not a modular environment
        boolean modularJvm = false;
        if (javaSpecVersion != null) {
            final Matcher matcher = Pattern.compile("^(?:1\\.)?(\\d+)$").matcher(javaSpecVersion);
            if (matcher.find()) {
                modularJvm = Integer.parseInt(matcher.group(1)) >= 9;
            }
        }
        MODULAR_JVM = modularJvm;
    }

    @Parameter
    public Map<String, String> env;

    private static final Function<ModelControllerClient, Boolean> STANDALONE_CHECK = new Function<ModelControllerClient, Boolean>() {
        private final ModelNode op = Operations.createReadAttributeOperation(EMPTY_ADDRESS, "server-state");

        @Override
        public Boolean apply(final ModelControllerClient client) {

            try {
                final ModelNode result = client.execute(op);
                if (Operations.isSuccessfulOutcome(result) &&
                        ClientConstants.CONTROLLER_PROCESS_STATE_RUNNING.equals(Operations.readResult(result).asString())) {
                    return true;
                }
            } catch (IllegalStateException | IOException ignore) {
            }
            return false;
        }
    };

    public StandaloneScriptTestCase() {
        super("standalone", STANDALONE_CHECK);
    }

    @Parameters
    public static Collection<Object> data() {
        final Collection<Object> result = new ArrayList<>(2);
        result.add(Collections.emptyMap());
        result.add(Collections.singletonMap("GC_LOG", "true"));
        return result;
    }

    @Override
    void testScript(final ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        // This is an odd case for Windows where with the -Xlog:gc* where the file argument does not seem to work with
        // a directory that contains a space. It seems similar to https://bugs.openjdk.java.net/browse/JDK-8215398
        // however the workaround is to do something like file=`\`"C:\wildfly\standalong\logs\gc.log`\`". This does not
        // seem to work when a directory has a space. An error indicating the trailing quote cannot be found. Removing
        // the `\ parts and just keeping quotes ends in the error shown in JDK-8215398.
        Assume.assumeFalse(TestSuiteEnvironment.isWindows() && MODULAR_JVM && env.containsKey("GC_LOG") && script.getScript().toString().contains(" "));
        script.start(env, DEFAULT_SERVER_JAVA_OPTS);
        Assert.assertNotNull("The process is null and may have failed to start.", script);
        Assert.assertTrue("The process is not running and should be", script.isAlive());

        // Shutdown the server
        @SuppressWarnings("Convert2Lambda")
        final Callable<ModelNode> callable = new Callable<ModelNode>() {
            @Override
            public ModelNode call() throws Exception {
                try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {
                    return executeOperation(client, Operations.createOperation("shutdown"));
                }
            }
        };
        execute(callable);
        validateProcess(script);

        // Check if the gc.log exists assuming we have the GC_LOG environment variable added
        if ("true".equals(env.get("GC_LOG"))) {
            final Path logDir = script.getContainerHome().resolve("standalone").resolve("log");
            Assert.assertTrue(Files.exists(logDir));
            final String fileName;
            // The IBM J9 JVM does seems to just use the gc.log name format for the current file name.
            if (MODULAR_JVM || TestSuiteEnvironment.isJ9Jvm()) {
                fileName = "gc.log";
            } else {
                fileName = "gc.log.0.current";
            }
            Assert.assertTrue(String.format("Missing %s file in %s", fileName, logDir), Files.exists(logDir.resolve(fileName)));
        }
    }
}
