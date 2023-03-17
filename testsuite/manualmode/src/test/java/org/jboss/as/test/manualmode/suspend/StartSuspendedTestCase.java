/*
* JBoss, Home of Professional Open Source.
* Copyright 2015, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.manualmode.suspend;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND_STATE;

import java.net.HttpURLConnection;
import java.net.SocketPermission;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jakarta.inject.Inject;

import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.as.test.shared.PermissionUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.Server;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.test.suspendresumeendpoint.SuspendResumeHandler;
import org.wildfly.test.suspendresumeendpoint.TestSuspendServiceActivator;
import org.wildfly.test.suspendresumeendpoint.TestUndertowService;
import org.xnio.IoUtils;

/**
 * Tests for suspend/resume functionality
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class StartSuspendedTestCase {

    public static final String WEB_SUSPEND_JAR = "web-suspend.jar";

    @Inject
    private static ServerController serverController;

    @Inject
    private ServerController container;

    ManagementClient managementClient;

    @Before
    public void startContainer() throws Exception {
        // Start the server
        Assert.assertNotNull(container);
        container.startSuspended();
        managementClient = container.getClient();

        //ServerDeploymentHelper helper = new ServerDeploymentHelper(managementClient.getControllerClient());
        JavaArchive war = ShrinkWrap.create(JavaArchive.class, WEB_SUSPEND_JAR);
        war.addPackage(SuspendResumeHandler.class.getPackage());
        war.addAsServiceProvider(ServiceActivator.class, TestSuspendServiceActivator.class);
        war.addAsResource(new StringAsset("Dependencies: org.jboss.dmr, org.jboss.as.controller, io.undertow.core, org.jboss.as.server,org.wildfly.extension.request-controller, org.jboss.as.network\n"), "META-INF/MANIFEST.MF");
        war.addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(
            new RuntimePermission("createXnioWorker"),
            new SocketPermission(TestSuiteEnvironment.getServerAddress() + ":8080", "listen,resolve"),
            new SocketPermission("*", "accept,resolve")
        ), "permissions.xml");
        //helper.deploy(WEB_SUSPEND_JAR, war.as(ZipExporter.class).exportAsInputStream());
        Assert.assertNotNull(serverController);
        Assert.assertTrue(serverController.isStarted()); // if container is not started, we get a NPE in container.getClient() within deploy
        serverController.deploy(war, WEB_SUSPEND_JAR);
    }

    @After
    public void stopContainer() throws Exception {
        Assert.assertNotNull(serverController);
        Assert.assertTrue(serverController.isStarted()); // if container is not started, we get a NPE in container.getClient() within deploy
        serverController.undeploy(WEB_SUSPEND_JAR);
        try {
            // Stop the container
            Assert.assertNotNull(container);
            container.stop();
        } finally {
            IoUtils.safeClose(managementClient);
        }
    }

    @Test
    public void testStartSuspended() throws Exception {

        final String address = "http://" + TestSuiteEnvironment.getServerAddress() + ":8080/web-suspend";
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {


            ModelNode op;
            op = new ModelNode();
            op.get(OP).set(READ_ATTRIBUTE_OPERATION);
            op.get(NAME).set(SUSPEND_STATE);
            Assert.assertEquals("SUSPENDED", serverController.getClient().executeForResult(op).asString());

            HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
            try {
                conn.setDoInput(true);
                int responseCode = conn.getResponseCode();
                Assert.assertEquals(503, responseCode);
            } finally {
                conn.disconnect();
            }

            op = new ModelNode();
            op.get(OP).set("resume");
            serverController.getClient().getControllerClient().execute(op);
            HttpRequest.get(address + "?" + TestUndertowService.SKIP_GRACEFUL + "=true", 10, TimeUnit.SECONDS);

            Assert.assertEquals(SuspendResumeHandler.TEXT, HttpRequest.get(address, 60, TimeUnit.SECONDS));

            serverController.reload(Server.StartMode.SUSPEND);

            op = new ModelNode();
            op.get(OP).set(READ_ATTRIBUTE_OPERATION);
            op.get(NAME).set(SUSPEND_STATE);
            Assert.assertEquals("SUSPENDED", serverController.getClient().executeForResult(op).asString());

            conn = (HttpURLConnection) new URL(address).openConnection();
            try {
                conn.setDoInput(true);
                int responseCode = conn.getResponseCode();
                Assert.assertEquals(503, responseCode);
            } finally {
                conn.disconnect();
            }

            op = new ModelNode();
            op.get(OP).set("resume");
            serverController.getClient().getControllerClient().execute(op);
            HttpRequest.get(address + "?" + TestUndertowService.SKIP_GRACEFUL + "=true", 10, TimeUnit.SECONDS);

            Assert.assertEquals(SuspendResumeHandler.TEXT, HttpRequest.get(address, 60, TimeUnit.SECONDS));

            serverController.reload(Server.StartMode.NORMAL);

            HttpRequest.get(address + "?" + TestUndertowService.SKIP_GRACEFUL + "=true", 10, TimeUnit.SECONDS);
            Assert.assertEquals(SuspendResumeHandler.TEXT, HttpRequest.get(address, 60, TimeUnit.SECONDS));

        } finally {
            executorService.shutdown();
        }
    }
}
