/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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
}
