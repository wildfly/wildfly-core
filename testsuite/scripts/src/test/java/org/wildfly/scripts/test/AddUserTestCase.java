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
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class AddUserTestCase extends ScriptTestCase {

    public AddUserTestCase() {
        super("add-user");
    }

    @Override
    void testScript(final ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        final Path standaloneMgmtUsers = script.getStandaloneConfig("mgmt-users.properties");
        final Path standaloneMgmtGroups = script.getStandaloneConfig("mgmt-groups.properties");
        final Path domainMgmtUsers = script.getDomainConfig("mgmt-users.properties");
        final Path domainMgmtGroups = script.getDomainConfig("mgmt-groups.properties");

        script.start(MAVEN_JAVA_OPTS, "-p", "test.12345", "-u", "test-admin", "-g", "test-admin-1,test-admin-2");
        validateProcess(script);

        // Test standalone
        validateValueAndClear(script, standaloneMgmtUsers, "test-admin", "4f973bec3e473f467b1b92d58909eeb5");
        validateValueAndClear(script, standaloneMgmtGroups, "test-admin", "test-admin-1,test-admin-2");

        // Test domain
        validateValueAndClear(script, domainMgmtUsers, "test-admin", "4f973bec3e473f467b1b92d58909eeb5");
        validateValueAndClear(script, domainMgmtGroups, "test-admin", "test-admin-1,test-admin-2");
    }

    private void validateValueAndClear(final ScriptProcess script, final Path propertiesFile,
                                       @SuppressWarnings("SameParameterValue") final String key,
                                       final String expectedValue) throws IOException {
        Properties properties = load(propertiesFile);
        String password = properties.getProperty(key);
        Assert.assertEquals(script.getErrorMessage(String.format("Expected %s got %s", expectedValue, password)), expectedValue, password);

        // Clear the files
        clearFile(propertiesFile);
    }

    @SuppressWarnings("EmptyTryBlock")
    private static void clearFile(final Path file) throws IOException {
        try (OutputStream ignored = Files.newOutputStream(file, StandardOpenOption.TRUNCATE_EXISTING)) {
            // do nothing, just clear the file
        }
        Assert.assertTrue(Files.readAllLines(file).isEmpty());
    }

    private static Properties load(final Path file) throws IOException {
        final Properties result = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            result.load(reader);
        }
        return result;
    }
}
