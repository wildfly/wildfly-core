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
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class AddUserTestCase extends ScriptTestCase {

    @Test
    public void testAddMgmtUserBatchScript() throws Exception {
        Assume.assumeTrue(Environment.isWindows());
        testAddMgmtUser(new ScriptProcess(getExecutable("add-user.bat"), WIN_CMD_PREFIX));
    }

    @Test
    public void testAddMgmtUserPowerShellScript() throws Exception {
        Assume.assumeTrue(Environment.isWindows() && isShellSupported("powershell", "-Help"));
        testAddMgmtUser(new ScriptProcess(getExecutable("add-user.ps1"), POWER_SHELL_PREFIX));
    }

    @Test
    public void testAddMgmtUserBashScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("bash", "-c", "echo", "test"));
        testAddMgmtUser(new ScriptProcess(getExecutable("add-user.sh")));
    }

    @Test
    @Ignore("Current tests do not work with csh")
    public void testAddMgmtUserCshScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("csh", "-c", "echo", "test"));
        testAddMgmtUser(new ScriptProcess(getExecutable("add-user.sh"), "csh"));
    }


    @Test
    public void testAddMgmtUserDashScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("dash", "-c", "echo", "test"));
        testAddMgmtUser(new ScriptProcess(getExecutable("add-user.sh"), "dash"));
    }

    @Test
    @Ignore("Current tests do not work with fish")
    public void testAddMgmtUserFishScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("fish", "-c", "echo", "test"));
        testAddMgmtUser(new ScriptProcess(getExecutable("add-user.sh"), "fish"));
    }

    @Test
    public void testAddMgmtUserKshScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("ksh", "-c", "echo", "test"));
        testAddMgmtUser(new ScriptProcess(getExecutable("add-user.sh"), "ksh"));
    }

    @Test
    @Ignore("Current tests do not work with tcsh")
    public void testAddMgmtUserTcshScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("tcsh", "-c", "echo", "test"));
        testAddMgmtUser(new ScriptProcess(getExecutable("add-user.sh"), "tcsh"));
    }

    @Test
    @Ignore("Current tests do not work with zsh")
    public void testAddMgmtUserZshScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("zsh", "-c", "echo", "test"));
        testAddMgmtUser(new ScriptProcess(getExecutable("add-user.sh"), "zsh"));
    }

    @Test
    public void testAddAppUserBatchScript() throws Exception {
        Assume.assumeTrue(Environment.isWindows());
        testAddAppUser(new ScriptProcess(getExecutable("add-user.bat"), WIN_CMD_PREFIX));
    }

    @Test
    public void testAddAppUserPowerShellScript() throws Exception {
        Assume.assumeTrue(Environment.isWindows() && isShellSupported("powershell", "-Help"));
        testAddAppUser(new ScriptProcess(getExecutable("add-user.ps1"), POWER_SHELL_PREFIX));
    }

    @Test
    public void testAddAppUserBashScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("bash", "-c", "echo", "test"));
        testAddAppUser(new ScriptProcess(getExecutable("add-user.sh")));
    }

    @Test
    @Ignore("Current tests do not work with csh")
    public void testAddAppUserCshScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("csh", "-c", "echo", "test"));
        testAddAppUser(new ScriptProcess(getExecutable("add-user.sh"), "csh"));
    }


    @Test
    public void testAddAppUserDashScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("dash", "-c", "echo", "test"));
        testAddAppUser(new ScriptProcess(getExecutable("add-user.sh"), "dash"));
    }

    @Test
    @Ignore("Current tests do not work with fish")
    public void testAddAppUserFishScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("fish", "-c", "echo", "test"));
        testAddAppUser(new ScriptProcess(getExecutable("add-user.sh"), "fish"));
    }

    @Test
    public void testAddAppUserKshScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("ksh", "-c", "echo", "test"));
        testAddAppUser(new ScriptProcess(getExecutable("add-user.sh"), "ksh"));
    }

    @Test
    @Ignore("Current tests do not work with tcsh")
    public void testAddAppUserTcshScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("tcsh", "-c", "echo", "test"));
        testAddAppUser(new ScriptProcess(getExecutable("add-user.sh"), "tcsh"));
    }

    @Test
    @Ignore("Current tests do not work with zsh")
    public void testAddAppUserZshScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("zsh", "-c", "echo", "test"));
        testAddAppUser(new ScriptProcess(getExecutable("add-user.sh"), "zsh"));
    }

    private void testAddMgmtUser(final ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        final Path standaloneMgmtUsers = Environment.getStandaloneConfig("mgmt-users.properties");
        final Path standaloneMgmtGroups = Environment.getStandaloneConfig("mgmt-groups.properties");
        final Path domainMgmtUsers = Environment.getDomainConfig("mgmt-users.properties");
        final Path domainMgmtGroups = Environment.getDomainConfig("mgmt-groups.properties");

        try {
            script.start("-p", "test.12345", "-u", "test-admin", "-g", "test-admin-1,test-admin-2");
            validateProcess(script);
            // TODO (jrp) will the password always be 4f973bec3e473f467b1b92d58909eeb5?

            // Test standalone
            validateValueAndClear(script, standaloneMgmtUsers, "test-admin", "4f973bec3e473f467b1b92d58909eeb5");
            validateValueAndClear(script, standaloneMgmtGroups, "test-admin", "test-admin-1,test-admin-2");

            // Test domain
            validateValueAndClear(script, domainMgmtUsers, "test-admin", "4f973bec3e473f467b1b92d58909eeb5");
            validateValueAndClear(script, domainMgmtGroups, "test-admin", "test-admin-1,test-admin-2");
        } finally {
            script.close();
        }
    }

    private void testAddAppUser(final ScriptProcess script) throws Exception {
        final Path standaloneAppUsers = Environment.getStandaloneConfig("application-users.properties");
        final Path standaloneAppRoles = Environment.getStandaloneConfig("application-roles.properties");
        final Path domainAppUsers = Environment.getDomainConfig("application-users.properties");
        final Path domainAppRoles = Environment.getDomainConfig("application-roles.properties");

        try {
            script.start("-a", "-r", "ApplicationRealm", "-p", "test.12345", "-u", "test-user", "-g", "test-user-1,test-user-2");
            validateProcess(script);
            // TODO (jrp) will the password always be 7afbc2dca908988b324b38290095bf23?

            // Test standalone
            validateValueAndClear(script, standaloneAppUsers, "test-user", "7afbc2dca908988b324b38290095bf23");
            validateValueAndClear(script, standaloneAppRoles, "test-user", "test-user-1,test-user-2");

            // Test domain
            validateValueAndClear(script, domainAppUsers, "test-user", "7afbc2dca908988b324b38290095bf23");
            validateValueAndClear(script, domainAppRoles, "test-user", "test-user-1,test-user-2");
        } finally {
            script.close();
        }
    }

    private void validateValueAndClear(final ScriptProcess script, final Path propertiesFile, final String key, final String expectedValue) throws IOException {
        Properties properties = load(propertiesFile);
        String password = properties.getProperty(key);
        Assert.assertEquals(getErrorMessage(script, String.format("Expected %s got %s", expectedValue, password)), expectedValue, password);

        // Clear the files
        clearFile(propertiesFile);
    }

    private static void clearFile(final Path file) throws IOException {
        try (OutputStream notUsed = Files.newOutputStream(file, StandardOpenOption.TRUNCATE_EXISTING)) {
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
