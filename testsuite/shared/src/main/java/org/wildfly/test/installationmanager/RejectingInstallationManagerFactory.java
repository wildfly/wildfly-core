package org.wildfly.test.installationmanager;

import java.nio.file.Path;

import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

public class RejectingInstallationManagerFactory implements InstallationManagerFactory {
    @Override
    public InstallationManager create(Path installationDir, MavenOptions mavenOptions) throws Exception {
        return null;
    }

    @Override
    public boolean isManagedInstallation(Path installationDir) {
        return false;
    }

    @Override
    public String getName() {
        return null;
    }
}
