/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.deployment;

import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.impl.base.exporter.zip.ZipExporterImpl;
import org.jboss.shrinkwrap.impl.base.path.BasicPath;
import org.junit.Assert;
import org.wildfly.core.testrunner.ManagementClient;

import static org.jboss.as.controller.client.helpers.ClientConstants.CONTENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT;
import static org.jboss.as.controller.client.helpers.Operations.createAddOperation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Tool for creating WAR, CLI and EAR archive to test deployment
 *
 * @author Vratislav Marek (vmarek@redhat.com)
 **/
public class DeploymentArchiveUtils {

    protected static final String DEFAULT_WAR_ARCHIVE_NAME = "default-cli-test-app-deploy.war";
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
     * Create standard WAR archive for deployment testing with defaults settings
     *
     * @return Return created {@link File} instance
     */
    public static File createWarArchive() {
        return createWarArchive(DEFAULT_WAR_ARCHIVE_NAME, DEFAULT_WAR_ARCHIVE_CONTENT);
    }

    /**
     * Create standard WAR archive for deployment testing
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
     * Create CLI archive for deployment testing
     *
     * @param content Content in archive file deploy.scr
     * @return Return created {@link File} instance
     */
    public static File createCliArchive(String content) {
        return createCliArchive(DEFAULT_CLI_ARCHIVE_NAME, content);
    }

    /**
     * Create CLI archive for deployment testing with defaults settings
     *
     * @return Return created {@link File} instance
     */
    public static File createCliArchive() {
        return createCliArchive(DEFAULT_CLI_ARCHIVE_NAME, DEFAULT_CLI_ARCHIVE_CONTENT);
    }

    /**
     * Create CLI archive for deployment testing
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
     * Create enterprise EAR archive for deployment testing with defaults settings
     *
     * @return Return created {@link File} instance
     */
    public static File createEnterpriseArchive() {
        return createEnterpriseArchive(DEFAULT_ENTERPRISE_ARCHIVE_NAME,
                DEFAULT_ENTERPRISE_ARCHIVE_SUBNAME, DEFAULT_ENTERPRISE_ARCHIVE_CONTENT);
    }

    /**
     * Create enterprise EAR archive for deployment testing
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


    /**
     * Deploys the archive to the running server.
     *
     * @param archive the archive to deploy
     * @throws IOException if an error occurs deploying the archive
     */
    public static void deploy(final Archive<?> archive, ManagementClient managementClient) throws IOException {
        // Use an operation to allow overriding the runtime name
        final ModelNode address = Operations.createAddress(DEPLOYMENT, archive.getName());
        final ModelNode addOp = createAddOperation(address);
        addOp.get("enabled").set(true);
        // Create the content for the add operation
        final ModelNode contentNode = addOp.get(CONTENT);
        final ModelNode contentItem = contentNode.get(0);
        contentItem.get(ClientConstants.INPUT_STREAM_INDEX).set(0);

        // Create an operation and add the input archive
        final OperationBuilder builder = OperationBuilder.create(addOp);
        builder.addInputStream(archive.as(ZipExporter.class).exportAsInputStream());

        // Deploy the content and check the results
        final ModelNode result = managementClient.getControllerClient().execute(builder.build());
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.fail(String.format("Failed to deploy %s: %s", archive, Operations.getFailureDescription(result).asString()));
        }
    }

    public static void undeploy(ManagementClient client, final String managementName) throws ServerDeploymentHelper.ServerDeploymentException {
        final ServerDeploymentHelper helper = new ServerDeploymentHelper(client.getControllerClient());
        final Collection<Throwable> errors = new ArrayList<>();
        try {
            final ModelNode op = Operations.createReadResourceOperation(Operations.createAddress("deployment", managementName));
            final ModelNode result = client.getControllerClient().execute(op);
            if (Operations.isSuccessfulOutcome(result))
                helper.undeploy(managementName);
        } catch (Exception e) {
            errors.add(e);
        }
        if (!errors.isEmpty()) {
            final RuntimeException e = new RuntimeException("Error undeploying: " + managementName);
            for (Throwable error : errors) {
                e.addSuppressed(error);
            }
            throw e;
        }
    }
}
