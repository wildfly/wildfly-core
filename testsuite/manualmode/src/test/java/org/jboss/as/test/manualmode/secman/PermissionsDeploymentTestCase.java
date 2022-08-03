/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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
 *
 */

package org.jboss.as.test.manualmode.secman;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.manualmode.deployment.AbstractDeploymentScannerBasedTestCase;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

import jakarta.inject.Inject;
import java.io.File;

/**
 * Tests the processing of {@code permissions.xml} in deployments
 *
 * @author Jaikiran Pai
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class PermissionsDeploymentTestCase extends AbstractDeploymentScannerBasedTestCase {

    private static final String SYS_PROP_SERVER_BOOTSTRAP_MAX_THREADS = "-Dorg.jboss.server.bootstrap.maxThreads=1";

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Inject
    private ServerController container;

    private ModelControllerClient modelControllerClient;

    @Before
    public void before() throws Exception {
        modelControllerClient = TestSuiteEnvironment.getModelControllerClient();
    }

    @After
    public void after() throws Exception {
        modelControllerClient.close();
    }

    @Override
    protected File getDeployDir() {
        return this.tempDir.getRoot();
    }


    /**
     * Tests that when the server is booted with {@code org.jboss.server.bootstrap.maxThreads} system property
     * set (to whatever value), the deployment unit processors relevant for dealing with processing of {@code permissions.xml},
     * in deployments, do run and process the deployment
     * <p>
     * NOTE: This test just tests that the deployment unit processor(s) process the deployment unit for the {@code permissions.xml}
     * and it's not in the scope of this test to verify that the permissions configured in that file are indeed applied correctly
     * to the deployment unit
     *
     * @throws Exception
     * @see <a href="https://issues.jboss.org/browse/WFLY-6657">WFLY-6657</a>
     */
    @Test
    public void testWithConfiguredMaxBootThreads() throws Exception {
        // Test-runner's ServerController/Server uses prop jvm.args to control what args are passed
        // to the server process VM. So, we add the system property controlling the max boot threads,
        // here
        final String existingJvmArgs = System.getProperty("jvm.args");
        if (existingJvmArgs == null) {
            System.setProperty("jvm.args", SYS_PROP_SERVER_BOOTSTRAP_MAX_THREADS);
        } else {
            System.setProperty("jvm.args", existingJvmArgs + " " + SYS_PROP_SERVER_BOOTSTRAP_MAX_THREADS);
        }
        // start the container
        container.start();
        try {
            addDeploymentScanner(modelControllerClient,1000, false, true);
            this.testInvalidPermissionsXmlDeployment("test-permissions-xml-with-configured-max-boot-threads.jar");
        } finally {
            removeDeploymentScanner(modelControllerClient);
            container.stop();
            if (existingJvmArgs == null) {
                System.clearProperty("jvm.args");
            } else {
                System.setProperty("jvm.args", existingJvmArgs);
            }
        }
    }


    /**
     * Tests that when the server is booted *without* the {@code org.jboss.server.bootstrap.maxThreads} system property
     * set, the deployment unit processors relevant for dealing with processing of {@code permissions.xml},
     * in deployments, do run and process the deployment
     * <p>
     * NOTE: This test just tests that the deployment unit processor(s) process the deployment unit for the {@code permissions.xml}
     * and it's not in the scope of this test to verify that the permissions configured in that file are indeed applied correctly
     * to the deployment unit
     *
     * @throws Exception
     * @see <a href="https://issues.jboss.org/browse/WFLY-6657">WFLY-6657</a>
     */
    @Test
    public void testWithoutConfiguredMaxBootThreads() throws Exception {
        container.start();
        try {
            addDeploymentScanner(modelControllerClient,1000, false, true);
            this.testInvalidPermissionsXmlDeployment("test-permissions-xml-without-max-boot-threads.jar");
        } finally {
            removeDeploymentScanner(modelControllerClient);
            container.stop();
        }

    }

    private void testInvalidPermissionsXmlDeployment(final String deploymentName) throws Exception {
        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class);
        // add an empty (a.k.a invalid content) in permissions.xml
        jar.addAsManifestResource(new StringAsset(""), "permissions.xml");
        // "deploy" it by placing it in the deployment directory
        jar.as(ZipExporter.class).exportTo(new File(getDeployDir(), deploymentName));
        final PathAddress deploymentPathAddr = PathAddress.pathAddress(ModelDescriptionConstants.DEPLOYMENT, deploymentName);
        // wait for the deployment to be picked up and completed (either with success or failure)
        waitForDeploymentToFinish(deploymentPathAddr);
        // the deployment is expected to fail due to a parsing error in the permissions.xml
        Assert.assertEquals("Deployment was expected to fail", "FAILED", deploymentState(modelControllerClient, deploymentPathAddr));
    }

    private void waitForDeploymentToFinish(final PathAddress deploymentPathAddr) throws Exception {
        // Wait until deployed ...
        long timeout = System.currentTimeMillis() + TimeoutUtil.adjust(30000);
        while (!exists(modelControllerClient, deploymentPathAddr) && System.currentTimeMillis() < timeout) {
            Thread.sleep(100);
        }
        Assert.assertTrue(exists(modelControllerClient, deploymentPathAddr));
    }

}
