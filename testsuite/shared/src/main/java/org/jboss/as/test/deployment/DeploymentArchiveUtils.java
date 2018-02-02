/*
Copyright 2018 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.jboss.as.test.deployment;

import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.impl.base.exporter.zip.ZipExporterImpl;
import org.jboss.shrinkwrap.impl.base.path.BasicPath;

import java.io.File;

/**
 * Tool for creating WAR and EAR archive to testing of deploy applications deployments functionality
 *
 * @author Vratislav Marek (vmarek@redhat.com)
 * @since 30.1.18 13:00
 **/
public class DeploymentArchiveUtils {

    protected static final String DEFAULT_WAR_ARCHIVE_NAME = "default-cli-test-app-deplo.war";
    protected static final String DEFAULT_WAR_ARCHIVE_CONTENT = "Version0.00";
    protected static final String DEFAULT_CLI_ARCHIVE_NAME = "deploymentarchive.cli";
    protected static final String DEFAULT_CLI_ARCHIVE_CONTENT = "ls -l";
    protected static final String DEFAULT_ENTERPRISE_ARCHIVE_NAME = "cli-test-app-deploy-all.ear";
    protected static final String DEFAULT_ENTERPRISE_ARCHIVE_SUBNAME = "cli-test-app3-deploy-all.war";
    protected static final String DEFAULT_ENTERPRISE_ARCHIVE_CONTENT = "Version3.00";


    private DeploymentArchiveUtils() {
        //
    }

    /**
     * Create standard WAR archive for testing deploying with defaults settings
     *
     * @return Return created {@link File} instance
     */
    public static File createWarArchive() {
        return createWarArchive(DEFAULT_WAR_ARCHIVE_NAME, DEFAULT_WAR_ARCHIVE_CONTENT);
    }

    /**
     * Create standard WAR archive for testing deploying
     *
     * @param archiveName Name of archive
     * @param content Context of page.html file
     * @return Return created {@link File} instance
     */
    public static File createWarArchive(String archiveName, String content) {

        WebArchive war = ShrinkWrap.create(WebArchive.class, archiveName);
        war.addAsWebResource(new StringAsset(content), "page.html");

        final String tempDir = TestSuiteEnvironment.getTmpDir();
        File file = new File(tempDir, war.getName());
        new ZipExporterImpl(war).exportTo(file, true);
        return file;
    }

    /**
     * Create CLI archive for testing deploying
     *
     * @param content Content in archive file deploy.scr
     * @return Return created {@link File} instance
     */
    public static File createCliArchive(String content) {
        return createCliArchive(DEFAULT_CLI_ARCHIVE_NAME, content);
    }

    /**
     * Create CLI archive for testing deploying with defaults settings
     *
     * @return Return created {@link File} instance
     */
    public static File createCliArchive() {
        return createCliArchive(DEFAULT_CLI_ARCHIVE_NAME, DEFAULT_CLI_ARCHIVE_CONTENT);
    }

    /**
     * Create CLI archive for testing deploying
     *
     * @param archiveName Name of archive
     * @param content Content in archive file deploy.scr
     * @return Return created {@link File} instance
     */
    public static File createCliArchive(String archiveName, String content) {

        final GenericArchive cliArchive = ShrinkWrap.create(GenericArchive.class, archiveName);
        cliArchive.add(new StringAsset(content), "deploy.scr");

        final String tempDir = TestSuiteEnvironment.getTmpDir();
        final File file = new File(tempDir, cliArchive.getName());
        cliArchive.as(ZipExporter.class).exportTo(file, true);
        return file;
    }

    /**
     * Create enterprise EAR archive for testing deploying with defaults settings
     *
     * @return Return created {@link File} instance
     */
    public static File createEnterpriseArchive() {
        return createEnterpriseArchive(DEFAULT_ENTERPRISE_ARCHIVE_NAME,
                DEFAULT_ENTERPRISE_ARCHIVE_SUBNAME, DEFAULT_ENTERPRISE_ARCHIVE_CONTENT);
    }

    /**
     * Create enterprise EAR archive for testing deploying
     *
     * @param archiveName Name of archive
     * @param content Context of page.html file
     * @return Return created {@link File} instance
     */
    public static File createEnterpriseArchive(String archiveName, String subArchiveName, String content) {

        WebArchive war = ShrinkWrap.create(WebArchive.class, archiveName);
        war.addAsWebResource(new StringAsset(content), "page.html");
        final EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class,
                subArchiveName);
        ear.add(war, new BasicPath("/"), ZipExporter.class);

        final String tempDir = TestSuiteEnvironment.getTmpDir();
        File file = new File(tempDir, ear.getName());
        new ZipExporterImpl(ear).exportTo(file, true);
        return file;
    }
}
