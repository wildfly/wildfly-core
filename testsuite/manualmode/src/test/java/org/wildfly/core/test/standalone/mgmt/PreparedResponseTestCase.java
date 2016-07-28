/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.wildfly.core.test.standalone.mgmt;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SHUTDOWN;
import static org.wildfly.test.shutdown.SlowStopServiceActivator.SHUTDOWN_WAITING_TIME;

import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.inject.Inject;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.test.shutdown.SlowStopService;
import org.wildfly.test.shutdown.SlowStopServiceActivator;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class PreparedResponseTestCase {

    public static Logger LOGGER = Logger.getLogger(PreparedResponseTestCase.class);

    public static final String SLOW_STOP_JAR = "slow-stop.jar";
    private static final long FREQUENCY = TimeoutUtil.adjust(500);

    @Inject
    protected static ServerController controller;

    @BeforeClass
    public static void startAndSetupContainer() throws Exception {
        controller.start();
        ManagementClient managementClient = controller.getClient();
        deploy(managementClient);
    }

    @AfterClass
    public static void stopContainer() throws Exception {
        undeploy(controller.getClient());
        controller.stop();
    }

    public static void deploy(ManagementClient managementClient) throws Exception {
        ServerDeploymentHelper helper = new ServerDeploymentHelper(managementClient.getControllerClient());
        JavaArchive war = ShrinkWrap.create(JavaArchive.class, SLOW_STOP_JAR);
        war.addPackage(SlowStopService.class.getPackage());
        war.addClass(TimeoutUtil.class);
        war.addAsServiceProvider(ServiceActivator.class, SlowStopServiceActivator.class);
        war.addAsResource(new StringAsset("Dependencies: org.jboss.dmr, org.jboss.logging, org.jboss.as.controller, org.jboss.as.server,org.wildfly.extension.request-controller, org.jboss.as.network\n"), "META-INF/MANIFEST.MF");
        helper.deploy(SLOW_STOP_JAR, war.as(ZipExporter.class).exportAsInputStream());
    }

    public static void undeploy(ManagementClient managementClient) throws ServerDeploymentHelper.ServerDeploymentException {
        ServerDeploymentHelper helper = new ServerDeploymentHelper(managementClient.getControllerClient());
        helper.undeploy(SLOW_STOP_JAR);
    }

    @Test
    public void reloadServer() throws Exception {
        ManagementClient mgmtClient = getManagementClient();
        long timeout = SHUTDOWN_WAITING_TIME + System.currentTimeMillis();
        mgmtClient.executeForResult(Operations.createOperation(RELOAD, new ModelNode().setEmptyList()));
        while (System.currentTimeMillis() < timeout) {
            Thread.sleep(FREQUENCY);
            try {
                controller.getClient().isServerInRunningState();
            } catch (RuntimeException ex) {
                break;
            }
        }
        Assert.assertFalse(System.currentTimeMillis() < timeout);
        Thread.sleep(2 * FREQUENCY);
        Assert.assertTrue(mgmtClient.isServerInRunningState());
    }

    @Test
    public void shutdownServer() throws Exception {
        try (ManagementClient managementClient = getManagementClient()) {
            long timeout = SHUTDOWN_WAITING_TIME + System.currentTimeMillis();
            managementClient.executeForResult(Operations.createOperation(SHUTDOWN, new ModelNode().setEmptyList()));
            while (System.currentTimeMillis() < timeout) {
                Thread.sleep(FREQUENCY);
                try {
                    controller.getClient().isServerInRunningState();
                } catch (RuntimeException ex) {
                    break;
                }
            }
            Assert.assertFalse(System.currentTimeMillis() < timeout);
            Assert.assertFalse(controller.getClient().isServerInRunningState());
        }
        Assert.assertFalse(controller.getClient().isServerInRunningState());
        try {
            controller.stop(); //Server is already stopped but the ServerController doesn't know it
        } catch (RuntimeException ex) {
            //ignore the exception as it is expected
        }
        controller.start();
    }

    static ManagementClient getManagementClient() {
        ModelControllerClient client = null;
        try {
            client = ModelControllerClient.Factory.create("http-remoting", InetAddress.getByName(TestSuiteEnvironment.getServerAddress()),
                    TestSuiteEnvironment.getServerPort(), new org.wildfly.core.testrunner.Authentication.CallbackHandler());
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        return new ManagementClient(client, TestSuiteEnvironment.getServerAddress(), TestSuiteEnvironment.getServerPort(), "http-remoting");
    }
}
