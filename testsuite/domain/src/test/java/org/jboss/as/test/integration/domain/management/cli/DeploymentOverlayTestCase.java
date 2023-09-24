/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.management.cli;

import java.io.File;
import java.io.FileWriter;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.CLITestSuite;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.test.jmx.ServiceActivatorDeployment;

/**
 *
 * @author jdenise@redhat.com
 */
public class DeploymentOverlayTestCase {

    private interface TestOperations {

        void addOverlay(CommandContext cli, String depRuntimeName, File overlayContent) throws Exception;

        void removeOverlay(CommandContext cli, String depRuntimeName) throws Exception;
    }

    private static final PathAddress SERVER_ADDRESS = PathAddress.pathAddress(
            PathElement.pathElement(Util.HOST, "primary"),
            PathElement.pathElement(Util.SERVER, "main-one"));

    private static final String MAIN_GROUP = "main-server-group";
    private static final String OTHER_GROUP = "other-server-group";

    private static final String SERVER_GROUPS = MAIN_GROUP + "," + OTHER_GROUP;
    private static final Properties properties = new Properties();
    private static final Properties properties2 = new Properties();

    private static final String OVERLAY_NAME = "ov1";
    private static CommandContext cli;

    private static final String runtimeName = "runtime-test-deployment.jar";
    private static final String name = "test-deployment.jar";
    private static File overlayContent;
    private static File archiveFile;
    private static DomainTestSupport testSupport;

    @BeforeClass
    public static void setup() throws Exception {
        testSupport = CLITestSuite.createSupport(
                DeploymentOverlayTestCase.class.getSimpleName());
        cli = CLITestUtil.getCommandContext(testSupport);
        cli.connectController();

        properties.clear();
        properties.put("service", "is new");

        properties2.clear();
        properties2.put("service", "is overwritten");

        JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive(name, properties);
        archiveFile = new File(TestSuiteEnvironment.getTmpDir(), name);
        archiveFile.createNewFile();
        archiveFile.deleteOnExit();
        archive.as(ZipExporter.class).exportTo(archiveFile, true);
        deployContent();
        // overlay content
        overlayContent = new File(TestSuiteEnvironment.getTmpDir(), "test-properties-content.properties");
        overlayContent.createNewFile();
        overlayContent.deleteOnExit();
        try (FileWriter writer = new FileWriter(overlayContent)) {
            properties2.store(writer, "Overlay Content");
        }
    }

    private static void deployContent() throws Exception {
        cli.handle("deploy " + archiveFile.getAbsolutePath() + " --all-server-groups --runtime-name=" + runtimeName);
    }

    private static void undeployContent() throws Exception {
        cli.handle("undeploy " + name + " --all-relevant-server-groups");
    }

    @AfterClass
    public static void cleanup() throws Exception {
        // In case test leftover.
        try {
            cli.handleSafe("deployment-overlay remove --server-groups=" + SERVER_GROUPS + " --name=" + OVERLAY_NAME);
            cli.handleSafe("deployment-overlay remove --name=" + OVERLAY_NAME);
            cli.handleSafe("undeploy * --all-relevant-server-groups");
            cli.terminateSession();
        } finally {
            CLITestSuite.stopSupport();
        }
    }

    /**
     * Add overlay, content, deployments and redeploy in 1 operation. Remove
     * overlay and redeploy in 1 operation.
     *
     * @throws Exception
     */
    @Test
    public void testAddRemove1() throws Exception {
        test(new TestOperations() {
            @Override
            public void addOverlay(CommandContext cli, String depRuntimeName, File overlayContent) throws Exception {
                cli.handle("deployment-overlay add --server-groups=" + SERVER_GROUPS + " --name=" + OVERLAY_NAME + " --content="
                        + ServiceActivatorDeployment.PROPERTIES_RESOURCE + "="
                        + overlayContent.getAbsolutePath()
                        + " --deployments=" + depRuntimeName + " --redeploy-affected");
            }

            @Override
            public void removeOverlay(CommandContext cli, String depRuntimeName) throws Exception {
                // remove from server groups.
                cli.handle("deployment-overlay remove --server-groups=" + SERVER_GROUPS + " --name=" + OVERLAY_NAME + " --redeploy-affected");
                // remove from root
                cli.handle("deployment-overlay remove --name=" + OVERLAY_NAME);
            }
        });
    }

    /**
     * Add overlay, content, then link and redeploy. Remove overlay and redeploy
     * in 1 operation.
     *
     * @throws Exception
     */
    @Test
    public void testAddRemove2() throws Exception {
        test(new TestOperations() {
            @Override
            public void addOverlay(CommandContext cli, String depRuntimeName, File overlayContent) throws Exception {
                cli.handle("deployment-overlay add --name=" + OVERLAY_NAME + " --content="
                        + ServiceActivatorDeployment.PROPERTIES_RESOURCE + "="
                        + overlayContent.getAbsolutePath());
                cli.handle("deployment-overlay link --name=" + OVERLAY_NAME + " --server-groups=" + SERVER_GROUPS + " --deployments="
                        + depRuntimeName + " --redeploy-affected");
            }

            @Override
            public void removeOverlay(CommandContext cli, String depRuntimeName) throws Exception {
                // remove from server groups.
                cli.handle("deployment-overlay remove --server-groups=" + SERVER_GROUPS + " --name=" + OVERLAY_NAME + " --redeploy-affected");
                // remove from root
                cli.handle("deployment-overlay remove --name=" + OVERLAY_NAME);
            }
        });
    }

    /**
     * Add overlay and content, then link, then redeploy. Remove deployment and
     * redeploy then overlay.
     *
     * @throws Exception
     */
    @Test
    public void testAddRemove3() throws Exception {
        test(new TestOperations() {
            @Override
            public void addOverlay(CommandContext cli, String depRuntimeName, File overlayContent) throws Exception {
                cli.handle("deployment-overlay add --name=" + OVERLAY_NAME + " --content="
                        + ServiceActivatorDeployment.PROPERTIES_RESOURCE + "="
                        + overlayContent.getAbsolutePath());
                cli.handle("deployment-overlay link  --server-groups=" + SERVER_GROUPS + " --name=" + OVERLAY_NAME + " --deployments=" + depRuntimeName);
                cli.handle("deployment-overlay redeploy-affected --name=" + OVERLAY_NAME);
            }

            @Override
            public void removeOverlay(CommandContext cli, String depRuntimeName) throws Exception {
                cli.handle("deployment-overlay remove --server-groups=" + SERVER_GROUPS + " --name=" + OVERLAY_NAME + " --deployments="
                        + depRuntimeName + " --redeploy-affected");
                cli.handle("deployment-overlay remove --server-groups=" + SERVER_GROUPS + " --name=" + OVERLAY_NAME);
                cli.handle("deployment-overlay remove --name=" + OVERLAY_NAME);
            }
        });
    }

    /**
     * Add overlay and content, then link, then redeploy. Remove content then
     * redeploy then remove overlay.
     *
     * @throws Exception
     */
    @Test
    public void testAddRemove4() throws Exception {
        test(new TestOperations() {
            @Override
            public void addOverlay(CommandContext cli, String depRuntimeName, File overlayContent) throws Exception {
                cli.handle("deployment-overlay add --name=" + OVERLAY_NAME + " --content="
                        + ServiceActivatorDeployment.PROPERTIES_RESOURCE + "="
                        + overlayContent.getAbsolutePath());
                cli.handle("deployment-overlay link --server-groups=" + SERVER_GROUPS + " --name=" + OVERLAY_NAME + " --deployments=" + depRuntimeName);
                cli.handle("deployment-overlay redeploy-affected --name=" + OVERLAY_NAME);
            }

            @Override
            public void removeOverlay(CommandContext cli, String depRuntimeName) throws Exception {
                cli.handle("deployment-overlay remove --server-groups=" + SERVER_GROUPS + " --name=" + OVERLAY_NAME + " --content="
                        + ServiceActivatorDeployment.PROPERTIES_RESOURCE + " --redeploy-affected");
                cli.handle("deployment-overlay remove --server-groups=" + SERVER_GROUPS + " --name=" + OVERLAY_NAME);
                cli.handle("deployment-overlay remove --name=" + OVERLAY_NAME);
            }
        });
    }

    @Test
    public void testBatch() throws Exception {
        testBatch(SERVER_GROUPS);
        testBatch(MAIN_GROUP);
    }

    private void testBatch(String sg) throws Exception {
        // No overlay
        checkOverlay(false, sg);

        // no deployment in context.
        undeployContent();

        cli.handle("batch");

        try {
            // Deploy a new content in the same composite
            deployContent();
            // Add overlay
            cli.handle("deployment-overlay add --server-groups=" + sg + "  --name=" + OVERLAY_NAME + " --content="
                    + ServiceActivatorDeployment.PROPERTIES_RESOURCE + "="
                    + overlayContent.getAbsolutePath()
                    + " --deployments=" + runtimeName + " --redeploy-affected");
            cli.handle("run-batch --verbose");

            // Check that overlay properly applied
            checkOverlay(true, sg);
            ServiceActivatorDeploymentUtil.validateProperties(cli.getModelControllerClient(), SERVER_ADDRESS, properties2);
            cli.handle("deployment-overlay remove --server-groups=" + sg + " --name=" + OVERLAY_NAME + " --redeploy-affected");
            cli.handle("deployment-overlay remove --name=" + OVERLAY_NAME);
            ServiceActivatorDeploymentUtil.validateProperties(cli.getModelControllerClient(), SERVER_ADDRESS, properties);
        } finally {
            cli.handleSafe("discard-batch");
        }
    }

    private void test(TestOperations ops) throws Exception {
        // No overlay
        checkOverlay(false);

        ServiceActivatorDeploymentUtil.validateProperties(cli.getModelControllerClient(), SERVER_ADDRESS, properties);
        //add
        ops.addOverlay(cli, runtimeName, overlayContent);
        checkOverlay(true);
        ServiceActivatorDeploymentUtil.validateProperties(cli.getModelControllerClient(), SERVER_ADDRESS, properties2);
        //remove
        ops.removeOverlay(cli, runtimeName);
        ServiceActivatorDeploymentUtil.validateProperties(cli.getModelControllerClient(), SERVER_ADDRESS, properties);

        // Check that overlay has been properly removed
        checkOverlay(false);

        // Then test in a batch.
        cli.handle("batch");
        try {
            ops.addOverlay(cli, runtimeName, overlayContent);
            ServiceActivatorDeploymentUtil.validateProperties(cli.getModelControllerClient(), SERVER_ADDRESS, properties);
            ops.removeOverlay(cli, runtimeName);
            cli.handle("run-batch --verbose");
            checkOverlay(false);
            ServiceActivatorDeploymentUtil.validateProperties(cli.getModelControllerClient(), SERVER_ADDRESS, properties);
        } finally {
            cli.handleSafe("discard-batch");
        }
    }

    private void checkOverlay(boolean present) throws Exception {
        checkOverlay(present, (String) null);
    }

    private void checkOverlay(boolean present, String sg) throws Exception {
        checkOverlay(present, getOverlays(cli.getModelControllerClient(), null));
        if (sg == null) {
            checkOverlay(present, getOverlays(cli.getModelControllerClient(), MAIN_GROUP));
            checkOverlay(present, getOverlays(cli.getModelControllerClient(), OTHER_GROUP));
        } else {
            String[] arr = sg.split(",");
            for (String g : arr) {
                checkOverlay(present, getOverlays(cli.getModelControllerClient(), g));
            }
        }
    }

    private void checkOverlay(boolean present, List<String> overlays) throws Exception {
        if (present) {
            if (overlays.size() != 1 && !overlays.get(0).equals(OVERLAY_NAME)) {
                throw new Exception("Unexpected Overlays " + overlays);
            }
        } else if (!overlays.isEmpty()) {
            throw new Exception("Unexpected Overlays " + overlays);
        }
    }

    private static List<String> getOverlays(ModelControllerClient client, String serverGroup) throws Exception {

        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        if (serverGroup != null) {
            builder.addNode(Util.SERVER_GROUP, serverGroup);
        }
        builder.setOperationName(Util.READ_CHILDREN_NAMES);
        builder.addProperty(Util.CHILD_TYPE, Util.DEPLOYMENT_OVERLAY);
        request = builder.buildRequest();
        final ModelNode outcome = client.execute(request);
        if (Util.isSuccess(outcome)) {
            return Util.getList(outcome);
        }

        return Collections.emptyList();
    }
}
