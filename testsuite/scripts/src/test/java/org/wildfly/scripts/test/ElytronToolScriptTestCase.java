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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ElytronToolScriptTestCase extends ScriptTestCase {

    public ElytronToolScriptTestCase() {
        super("elytron-tool");
    }

    @Override
    void testScript(final ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        try {
            // Read an attribute
            script.start(MAVEN_JAVA_OPTS, "mask", "--salt", "12345678", "--iteration", "123", "--secret", "supersecretstorepassword");
            Assert.assertNotNull("The process is null and may have failed to start.", script);
            Assert.assertTrue("The process is not running and should be", script.isAlive());

            validateProcess(script);

            // Get the output and test the masked password
            try (BufferedReader reader = Files.newBufferedReader(script.getStdout(), StandardCharsets.UTF_8)) {
                String line = reader.readLine();
                // Skip lines like: "Picked up _JAVA_OPTIONS: ..."
                while (line.startsWith("Picked up _JAVA_")) {
                    line = reader.readLine();
                }
                Assert.assertEquals("MASK-8VzWsSNwBaR676g8ujiIDdFKwSjOBHCHgnKf17nun3v;12345678;123", line);
                // We should only have one line
                Assert.assertNull(reader.readLine());
            }
        } finally {
            script.close();
        }
    }
}
