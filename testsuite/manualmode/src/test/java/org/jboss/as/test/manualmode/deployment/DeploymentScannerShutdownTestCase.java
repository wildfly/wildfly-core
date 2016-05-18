/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.manualmode.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;

import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.byteman.agent.submit.Submit;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Tests server reload during a deployment or undeployment to a wildfly core server by the filesystem scanner.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
@Ignore("this test relies too much on hacking internals (see the byteman script) so it's only here to allow " +
        "custom use if there is a need to diagnose a problem in this area")
public class DeploymentScannerShutdownTestCase {

    // Max time to wait for some action to complete, in ms
    private static final int TIMEOUT = TimeoutUtil.adjust(20000);
    // Pause time between checks whether some action has completed, in ms
    private static final int BACKOFF = 10;
    private static final String DEPLOYMENT_SCANNER_EXTENSION = "org.jboss.as.deployment-scanner";
    private static final String DEPLOYMENT_SCANNER_SUBSYSTEM = "deployment-scanner";
    private static final String DEPLOYMENT_NAME = "test-deployment.jar";

    @Inject
    private ServerController container;

    private File deployDir;
    private final String scannerName = "autoZips";
    private static final Map<String, String> properties = new HashMap<>();

    private final Submit bytemanSubmit = new Submit(
            System.getProperty("byteman.server.ipaddress", Submit.DEFAULT_ADDRESS),
            Integer.getInteger("byteman.server.port", Submit.DEFAULT_PORT));

    @BeforeClass
    public static void addDeploymentScanner() throws Exception {
        properties.clear();
        properties.put("service", "is new");
        properties.clear();
        properties.put("service", "is replaced");
    }

    @After
    public void cleanAll() throws Exception {
        try {
            removeRules();
        } catch (Exception e) {

        }
        try {
            removeDeploymentScanner(scannerName);
        } catch (Exception e) {

        }
        try {
            removeDeploymentScannerExtension();
        } catch (Exception e) {

        }
        cleanFile(deployDir);
        deployDir.delete();
        container.stop();
    }

    @Before
    public void prepareServer() throws Exception {
        deployDir = createDeploymentDir("auto-deployments");
        container.start();
        addDeploymentScannerExtension();
        addDeploymentScanner(deployDir, scannerName, true);
    }

    private void deployRules() throws Exception {
        bytemanSubmit.addRulesFromResources(Collections.singletonList(
                DeploymentScannerShutdownTestCase.class.getClassLoader().getResourceAsStream("byteman/DeploymentScannerShutdownTestCase.btm")));
    }

    private void removeRules() {
        try {
            bytemanSubmit.deleteAllRules();
        } catch (Exception ex) {

        }
    }

    @Test
    public void testFilesystemDeployment() throws Exception {
        deployRules();
        final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive(DEPLOYMENT_NAME, properties);
        final File dir = new File("target/archives");
        dir.mkdirs();
        final File deployed = new File(deployDir, DEPLOYMENT_NAME + ".deployed");
        final File target = new File(deployDir, DEPLOYMENT_NAME);
        final File file = new File(dir, DEPLOYMENT_NAME);
        archive.as(ZipExporter.class).exportTo(file, true);
        deploy(file, target, deployed);
        container.reload(TIMEOUT);
        Assert.assertTrue("We should have the deployed marker", deployed.exists());
        Assert.assertTrue(container.isStarted());
        Path serverLog = getAbsoluteLogFilePath("jboss.server.log.dir", "server.log");
        assertLogContains(serverLog, "rejected from java.util.concurrent.ScheduledThreadPoolExecutor", false);
        container.stop();
        deployed.delete();
        target.delete();
        container.start();
        removeRules();
    }

    @Test
    public void testFileSystemUndeployment() throws Exception {
        final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("test-deployment.jar", properties);
        final File dir = new File("target/archives");
        dir.mkdirs();
        final File deployed = new File(deployDir, DEPLOYMENT_NAME + ".deployed");
        final File undeployed = new File(deployDir, DEPLOYMENT_NAME + ".undeployed");
        if (undeployed.exists()) {
            undeployed.delete();
        }
        final File target = new File(deployDir, DEPLOYMENT_NAME);
        final File file = new File(dir,DEPLOYMENT_NAME);
        archive.as(ZipExporter.class).exportTo(file, true);
        removeRules();
        deploy(file, target, deployed);
        deployRules();
        undeploy(target, undeployed);
        container.reload(TIMEOUT);
        Assert.assertTrue("We should have the undeployed marker", undeployed.exists());
        Assert.assertTrue(container.isStarted());
        Path serverLog = getAbsoluteLogFilePath("jboss.server.log.dir", "server.log");
        assertLogContains(serverLog, "rejected from java.util.concurrent.ScheduledThreadPoolExecutor", false);
        //container.start();
    }

    private void assertLogContains(final Path logFile, final String msg, final boolean expected) throws Exception {
        try (final BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            boolean logFound = false;

            while ((line = reader.readLine()) != null) {
                if (line.contains(msg)) {
                    logFound = true;
                    break;
                }
            }
            Assert.assertTrue(logFound == expected);
        }
    }

    private void deploy(final File file, final File target, final File deployed) throws IOException {
        Assert.assertFalse(target.exists());
        // Copy file to deploy directory
        final InputStream in = new BufferedInputStream(new FileInputStream(file));
        try {
            final OutputStream out = new BufferedOutputStream(new FileOutputStream(target));
            try {
                int i = in.read();
                while (i != -1) {
                    out.write(i);
                    i = in.read();
                }
            } finally {
                StreamUtils.safeClose(out);
            }
        } finally {
            StreamUtils.safeClose(in);
        }
        Assert.assertTrue(file.exists());
        waitForMarkerFile(deployed);
    }

    private void undeploy(final File target, final File undeployed) throws IOException {
        // Delete file from deploy directory
        target.delete();
        waitForMarkerFile(undeployed);
    }

    private void waitForMarkerFile(File marker) {
        for (int i = 0; i < TIMEOUT / BACKOFF; i++) {
            if (marker.exists()) {
                break;
            }
            try {
                Thread.sleep(BACKOFF);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }

    private ModelNode addDeploymentScanner(final File deployDir, final String scannerName, final boolean autoDeployZipped)
            throws Exception {
        ModelNode add = new ModelNode();
        add.get(OP).set(ADD);
        ModelNode addr = new ModelNode();
        addr.add("subsystem", "deployment-scanner");
        addr.add("scanner", scannerName);
        add.get(OP_ADDR).set(addr);
        add.get("path").set(deployDir.getAbsolutePath());
        add.get("scan-enabled").set(true);
        add.get("scan-interval").set(1000);
        if (autoDeployZipped == false) {
            add.get("auto-deploy-zipped").set(false);
        }
        return container.getClient().executeForResult(add);
    }

    private void removeDeploymentScanner(final String scannerName) throws Exception {
        ModelNode addr = new ModelNode();
        addr.add("subsystem", "deployment-scanner");
        addr.add("scanner", scannerName);
        ModelNode remove = new ModelNode();
        remove.get(OP).set(REMOVE);
        remove.get(OP_ADDR).set(addr);
        container.getClient().executeForResult(remove);
    }

    private File createDeploymentDir(String dir) {
        deployDir = new File("target", dir);
        cleanFile(deployDir);
        deployDir.mkdirs();
        Assert.assertTrue(deployDir.exists());
        return deployDir;
    }

    private static void cleanFile(File toClean) {
        if (toClean.isDirectory()) {
            for (File child : toClean.listFiles()) {
                cleanFile(child);
            }
        }
        toClean.delete();
    }

    private Path getAbsoluteLogFilePath(final String relativePath, final String fileName) throws UnsuccessfulOperationException {
        final ModelNode address = PathAddress.pathAddress(
                PathElement.pathElement(ModelDescriptionConstants.PATH, relativePath)).toModelNode();
        final ModelNode result;
        try {
            final ModelNode op = Operations.createReadAttributeOperation(address, ModelDescriptionConstants.PATH);
            result = container.getClient().executeForResult(op);
            return Paths.get(result.asString(), fileName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private void addDeploymentScannerExtension() throws Exception {
        ModelNode addOp = Util.createAddOperation(PathAddress.pathAddress(PathElement.pathElement(EXTENSION, DEPLOYMENT_SCANNER_EXTENSION)));
        container.getClient().executeForResult(addOp);
        addOp = Util.createAddOperation(PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, DEPLOYMENT_SCANNER_SUBSYSTEM)));
        container.getClient().executeForResult(addOp);
    }

    private void removeDeploymentScannerExtension() throws Exception {
        try {
            ModelNode removeOp = Util.createRemoveOperation(PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, DEPLOYMENT_SCANNER_SUBSYSTEM)));
            container.getClient().executeForResult(removeOp);
        } finally {
            ModelNode removeOp = Util.createRemoveOperation(PathAddress.pathAddress(PathElement.pathElement(EXTENSION, DEPLOYMENT_SCANNER_EXTENSION)));
            container.getClient().executeForResult(removeOp);
        }
    }
}
