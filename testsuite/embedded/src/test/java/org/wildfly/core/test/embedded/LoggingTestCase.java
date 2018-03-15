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

package org.wildfly.core.test.embedded;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.junit.Assert;
import org.wildfly.core.embedded.EmbeddedManagedProcess;
import org.wildfly.core.embedded.EmbeddedProcessFactory;
import org.wildfly.core.embedded.EmbeddedProcessStartException;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class LoggingTestCase extends AbstractTestCase {

    protected void testStandalone(final String logPackage, final String filename) throws Exception {
        final String[] systemPkgs = logPackage == null ? null : new String[] {logPackage};
        test(EmbeddedProcessFactory.createStandaloneServer(Environment.JBOSS_HOME.toString(),
                Environment.MODULE_PATH.toString(), systemPkgs, null), filename, STANDALONE_CHECK);
    }

    protected void testHostController(final String logPackage, final String filename) throws Exception {
        final String[] systemPkgs = logPackage == null ? null : new String[] {logPackage};
        test(EmbeddedProcessFactory.createHostController(Environment.JBOSS_HOME.toString(),
                Environment.MODULE_PATH.toString(), systemPkgs, null), filename, HOST_CONTROLLER_CHECK);
    }

    private void test(final EmbeddedManagedProcess server, final String filename,
                      final Function<EmbeddedManagedProcess, Boolean> check) throws EmbeddedProcessStartException,
            IOException, TimeoutException, InterruptedException {
        final Path logFile = Environment.LOG_DIR.resolve(filename);
        try {
            startAndWaitFor(server, check);
            // Check for existence of the file and just ensure it's not empty
            Assert.assertTrue(String.format("Expected file \"%s\" to exist", logFile), Files.exists(logFile));
            try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
                Assert.assertNotNull(String.format("Expected file \"%s\" to not be empty", logFile), reader.readLine());
            }
        } finally {
            server.stop();
        }
    }
}
