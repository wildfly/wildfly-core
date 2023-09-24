/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.logging.profiles;

import java.io.FilePermission;
import java.nio.file.Paths;
import java.security.Permission;
import java.util.PropertyPermission;

import org.jboss.as.test.integration.logging.Log4j2ServiceActivator;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(WildFlyRunner.class)
@ServerSetup(AbstractLoggingProfilesTestCase.LoggingProfilesTestCaseSetup.class)
public class Log4j2LoggingProfilesTestCase extends AbstractLoggingProfilesTestCase {

    public Log4j2LoggingProfilesTestCase() {
        super(Log4j2ServiceActivator.class, 2);
    }

    @Override
    protected void processDeployment(final JavaArchive deployment) {
        final Permission[] permissions = {
                // The getClassLoader permissions is required for the org.apache.logging.log4j.util.ProviderUtil.
                new RuntimePermission("getClassLoader"),
                // The FilePermissions is also for the org.apache.logging.log4j.util.ProviderUtil as it needs to read the JAR
                // for the service loader.
                new FilePermission(resolveFilePermissions(), "read"),
                // Required for the EnvironmentPropertySource System.getenv().
                new RuntimePermission("getenv.*"),
                // Required for the SystemPropertiesPropertySource System.getProperties().
                new PropertyPermission("*", "read,write"),
        };
        addPermissions(deployment, permissions);
    }

    private static String resolveFilePermissions() {
        // WildFly Core uses "thin" server so artifacts are resolved from maven coordinates.
        final String dir = System.getProperty("maven.repo.local");
        if (dir == null) {
            throw new RuntimeException("Failed to resolve system property maven.repo.local");
        }
        return Paths.get(dir)
                .resolve("org")
                .resolve("jboss")
                .resolve("logmanager")
                .resolve("log4j2-jboss-logmanager")
                .resolve("-")
                .toString();
    }
}
