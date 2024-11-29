/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.installationmanager;

import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

import java.nio.file.Path;

/**
 * An Installation Manager Factory used by the Installation Manager tests
 */
public class TestInstallationManagerFactory implements InstallationManagerFactory {
    public static InstallationManager installationManager;
    public static Path installationDir;
    public static MavenOptions mavenOptions;
    public static boolean validInstallation = true;

    @Override
    public InstallationManager create(Path installationDir, MavenOptions mavenOptions) throws Exception {
        this.installationDir = installationDir;
        this.mavenOptions = mavenOptions;
        installationManager = new TestInstallationManager(installationDir, mavenOptions);
        return installationManager;
    }

    @Override
    public String getName() {
        return "test";
    }

    @Override
    public boolean isManagedInstallation(Path installationDir) {
        return validInstallation;
    }
}
