/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.scripts.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.common.test.ServerConfigurator;
import org.wildfly.common.test.ServerHelper;

/**
 * Tests for the Windows service.bat script.
 *
 */
public class ServiceScriptTestCase extends ScriptTestCase {

    private static final String TEST_SERVICE_NAME = "WildFlyTest";
    private static final String POWERSHELL = "powershell";
    private static final List<Path> copiedScripts = new ArrayList<>();
    private String installedServiceName = null;

    public ServiceScriptTestCase() {
        super("service");
    }

    @BeforeClass
    public static void setupServiceScript() throws IOException {
        for (Path containerHome : ServerConfigurator.PATHS) {
            final Path sourceScript = containerHome.resolve("docs").resolve("contrib").resolve("scripts")
                    .resolve("service").resolve("service.bat");
            final Path targetScript = containerHome.resolve("bin").resolve("service.bat");

            if (Files.exists(sourceScript) && !Files.exists(targetScript)) {
                Files.copy(sourceScript, targetScript);
                copiedScripts.add(targetScript);
            }
        }
    }

    @AfterClass
    public static void cleanupServiceScript() throws IOException {
        for (Path script : copiedScripts) {
            if (Files.exists(script)) {
                Files.delete(script);
            }
        }
        copiedScripts.clear();
    }

    @After
    public void cleanupService() throws InterruptedException, TimeoutException, IOException {
        if (installedServiceName != null && TestSuiteEnvironment.isWindows()) {
            try {
                if (isServiceInstalled(installedServiceName)) {
                    uninstallService(installedServiceName);
                }
            } finally {
                installedServiceName = null;
            }
        }
    }

    @Test
    public void testBatchScript() throws Exception {
        Assume.assumeTrue("Service script doesnt run without parameters", false);
    }

    @Test
    public void testPowerShellScript() throws Exception {
        Assume.assumeTrue("Service script doesnt have powershell variant", false);
    }

    @Override
    void testScript(final ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        Assume.assumeTrue("Service script only runs on Windows", TestSuiteEnvironment.isWindows());
    }

    @Test
    public void testActualServiceInstallWithAutoStartupParameter() throws Exception {
        Assume.assumeTrue("Service installation test only runs on Windows", TestSuiteEnvironment.isWindows());
        Assume.assumeTrue("PowerShell must be available", Shell.POWERSHELL.isSupported());
        Assume.assumeTrue("Service installation requires administrator privileges", isRunningAsAdmin());

        testServiceInstallationWithStartupType("auto", "Auto");
    }

    @Test
    public void testActualServiceInstallWithEmptyStartupParameter() throws Exception {
        Assume.assumeTrue("Service installation test only runs on Windows", TestSuiteEnvironment.isWindows());
        Assume.assumeTrue("PowerShell must be available", Shell.POWERSHELL.isSupported());
        Assume.assumeTrue("Service installation requires administrator privileges", isRunningAsAdmin());

        testServiceInstallationWithStartupType("", "Auto");
    }

    @Test
    public void testActualServiceInstallWithDelayedStartupParameter() throws Exception {
        Assume.assumeTrue("Service installation test only runs on Windows", TestSuiteEnvironment.isWindows());
        Assume.assumeTrue("PowerShell must be available", Shell.POWERSHELL.isSupported());
        Assume.assumeTrue("Service installation requires administrator privileges", isRunningAsAdmin());

        testServiceInstallationWithStartupType("delayed", "Delayed");
    }

    @Test
    public void testActualServiceInstallWithManualStartupParameter() throws Exception {
        Assume.assumeTrue("Service installation test only runs on Windows", TestSuiteEnvironment.isWindows());
        Assume.assumeTrue("PowerShell must be available", Shell.POWERSHELL.isSupported());
        Assume.assumeTrue("Service installation requires administrator privileges", isRunningAsAdmin());

        testServiceInstallationWithStartupType("manual", "Manual");
    }

    @Test
    public void testActualServiceInstallWithoutStartupParameter() throws Exception {
        Assume.assumeTrue("Service installation test only runs on Windows", TestSuiteEnvironment.isWindows());
        Assume.assumeTrue("PowerShell must be available", Shell.POWERSHELL.isSupported());
        Assume.assumeTrue("Service installation requires administrator privileges", isRunningAsAdmin());

        final String serviceName = TEST_SERVICE_NAME + "_none";
        installedServiceName = serviceName;

        try (ScriptProcess script = new ScriptProcess(ServerHelper.JBOSS_HOME, "service", Shell.BATCH, 60, true)) {
            script.start("install", "/name", serviceName);
            validateProcess(script);

            Assert.assertTrue("Service should be installed", isServiceInstalled(serviceName));

            final String actualStartMode = getServiceStartupType(serviceName);

            Assert.assertEquals(
                    String.format("Service startup type should be %s but was %s", "Manual", actualStartMode),
                    "Manual",
                    actualStartMode);
        }
    }

    private void testServiceInstallationWithStartupType(final String startupParam, String expectedStartMode)
            throws InterruptedException, TimeoutException, IOException {

        final String serviceName = TEST_SERVICE_NAME + "_" + startupParam;
        installedServiceName = serviceName;

        try (ScriptProcess script = new ScriptProcess(ServerHelper.JBOSS_HOME, "service", Shell.BATCH, 60, true)) {
            script.start("install", "/startup", startupParam, "/name", serviceName);
            validateProcess(script);

            Assert.assertTrue("Service should be installed", isServiceInstalled(serviceName));

            final String actualStartMode = getServiceStartupType(serviceName);

            Assert.assertEquals(
                    String.format("Service startup type should be %s but was %s", expectedStartMode, actualStartMode),
                    expectedStartMode,
                    actualStartMode);
        }
    }

    private boolean isServiceInstalled(final String serviceName) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(
            POWERSHELL,
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy", "Bypass",
            "-Command",
            "Get-Service -Name " + serviceName + " -ErrorAction SilentlyContinue | select -property name"
        );

        Process powerShellProcess = builder.start();
        powerShellProcess.getOutputStream().close();

        BufferedReader stdout = new BufferedReader(new InputStreamReader(powerShellProcess.getInputStream()));
        String line;
        while ((line = stdout.readLine()) != null) {
            if (line.contains(serviceName)) {
                return true;
            }
        }

        return false;
    }

    private String getServiceStartupType(final String serviceName) throws IOException {
        String command = String.format(
            "$svc = Get-CimInstance -ClassName Win32_Service | Where-Object {$_.Name -eq '%s'}; " +
            "if ($svc) { " +
            "Write-Host \"StartMode:$($svc.StartMode)\"; " +
            "Write-Host \"DelayedAutoStart:$($svc.DelayedAutoStart)\" " +
            "}",
            serviceName
        );

        ProcessBuilder builder = new ProcessBuilder(
            POWERSHELL,
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy", "Bypass",
            "-Command",
            command
        );
        final Process process = builder.start();
        process.getOutputStream().close();

        String startMode = null;
        Boolean delayedAutoStart = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final String trimmed = line.trim();
                if (trimmed.startsWith("StartMode:")) {
                    startMode = trimmed.substring("StartMode:".length()).trim();
                } else if (trimmed.startsWith("DelayedAutoStart:")) {
                    String value = trimmed.substring("DelayedAutoStart:".length()).trim();
                    delayedAutoStart = Boolean.parseBoolean(value);
                }
            }
        }

        if (startMode != null) {
            if ("Auto".equalsIgnoreCase(startMode)) {
                if (Boolean.TRUE.equals(delayedAutoStart)) {
                    return "Delayed";
                } else {
                    return "Auto";
                }
            } else if ("Manual".equalsIgnoreCase(startMode)) {
                return "Manual";
            } else if ("Disabled".equalsIgnoreCase(startMode)) {
                return "Disabled";
            } else {
                return startMode;
            }
        }

        return "Unknown";
    }

    private void uninstallService(final String serviceName) throws InterruptedException, TimeoutException, IOException {
        try (ScriptProcess script = new ScriptProcess(ServerHelper.JBOSS_HOME, "service", Shell.BATCH, 60)) {
            script.start("uninstall", "/name", serviceName);
            script.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    private boolean isRunningAsAdmin() throws IOException {
        ProcessBuilder builder = new ProcessBuilder(
            POWERSHELL,
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy", "Bypass",
            "-Command",
            "([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)"
        );

        Process process = builder.start();
        process.getOutputStream().close();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.equalsIgnoreCase("True")) {
                    return true;
                } else if (trimmed.equalsIgnoreCase("False")) {
                    return false;
                }
            }
        }

        return false;
    }
}
