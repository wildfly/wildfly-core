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

package org.jboss.as.test.integration.domain.suspendresume;

import static org.jboss.as.controller.client.helpers.ClientConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND_STATE;
import static org.jboss.as.test.integration.domain.suspendresume.HostSuspendResumeTestCase.SUSPENDING;

import java.lang.reflect.ReflectPermission;
import java.net.HttpURLConnection;
import java.net.SocketPermission;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DeploymentPlan;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.DomainDeploymentManager;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.domain.suites.DomainTestSuite;
import org.jboss.as.test.shared.PermissionUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.test.suspendresumeendpoint.SuspendResumeHandler;
import org.wildfly.test.suspendresumeendpoint.TestSuspendServiceActivator;
import org.wildfly.test.suspendresumeendpoint.TestUndertowService;

/**
 * Test of suspend/resume in domain mode
 *
 * @author Stuart Douglas
 */
public class DomainGracefulShutdownTestCase {
    public static final String WEB_SUSPEND_JAR = "web-suspend.jar";
    public static final String MAIN_SERVER_GROUP = "main-server-group";

    public static final PathAddress MASTER_ADDR = PathAddress.pathAddress(HOST, "primary");
    public static final PathAddress SERVER_MAIN_ONE = PathAddress.pathAddress(SERVER, "main-one");

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(DomainGracefulShutdownTestCase.class.getSimpleName());
        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainMasterLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Before
    public void deployApp() throws Exception {
        DomainClient client = domainMasterLifecycleUtil.getDomainClient();

        DomainDeploymentManager deploymentManager = client.getDeploymentManager();
        DeploymentPlan plan = deploymentManager.newDeploymentPlan().add(WEB_SUSPEND_JAR, createDeployment().as(ZipExporter.class).exportAsInputStream())
                .andDeploy().toServerGroup(MAIN_SERVER_GROUP)
                .build();
        deploymentManager.execute(plan).get();
    }

    @After
    public void undeployApp() throws Exception {
        DomainClient client = domainMasterLifecycleUtil.getDomainClient();

        DomainDeploymentManager deploymentManager = client.getDeploymentManager();
        DeploymentPlan plan = deploymentManager.newDeploymentPlan().undeploy(WEB_SUSPEND_JAR)
                .andRemoveUndeployed()
                .toServerGroup(MAIN_SERVER_GROUP)
                .build();

        deploymentManager.execute(plan).get();
    }

    @Test
    public void testGracefulShutdownDomainLevel() throws Exception {
        DomainClient client = domainMasterLifecycleUtil.getDomainClient();

        final String address = "http://" + TestSuiteEnvironment.getServerAddress() + ":8080/web-suspend";
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<Object> result = executorService.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    return HttpRequest.get(address, 60, TimeUnit.SECONDS);
                }
            });

            Thread.sleep(1000); //nasty, but we need to make sure the HTTP request has started

            ModelNode op = new ModelNode();
            op.get(ModelDescriptionConstants.OP).set("stop-servers");
            op.get(ModelDescriptionConstants.TIMEOUT).set(60);
            op.get(ModelDescriptionConstants.BLOCKING).set(false);
            client.execute(op);

            //make sure requests are being rejected
            final HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
            try {
                conn.setDoInput(true);
                int responseCode = conn.getResponseCode();
                Assert.assertEquals(503, responseCode);
            } finally {
                conn.disconnect();
            }

            //make sure the server is still up, and trigger the actual shutdown
            HttpRequest.get(address + "?" + TestUndertowService.SKIP_GRACEFUL + "=true", 10, TimeUnit.SECONDS);
            Assert.assertEquals(SuspendResumeHandler.TEXT, result.get());

            //make sure our initial request completed
            Assert.assertEquals(SuspendResumeHandler.TEXT, result.get());


        } finally {
            executorService.shutdown();
        }
    }

    @Test
    public void testStartSuspendedDomainMode() throws Exception {
        DomainClient client = domainMasterLifecycleUtil.getDomainClient();

        ModelNode op = new ModelNode();
        op.get(ModelDescriptionConstants.OP).set("reload-servers");
        op.get(ModelDescriptionConstants.BLOCKING).set(true);
        op.get(ModelDescriptionConstants.START_MODE).set(SUSPEND);
        client.execute(op);

        op = new ModelNode();
        op.get(ADDRESS).set(PathAddress.parseCLIStyleAddress("/host=primary/server=main-one").toModelNode());
        op.get(OP).set(READ_ATTRIBUTE_OPERATION);
        op.get(NAME).set(SUSPEND_STATE);
        Assert.assertEquals("SUSPENDED", client.execute(op).get(RESULT).asString());

        final String address = "http://" + TestSuiteEnvironment.getServerAddress() + ":8080/web-suspend";
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        try {
            conn.setDoInput(true);
            int responseCode = conn.getResponseCode();
            Assert.assertEquals(503, responseCode);
        } finally {
            conn.disconnect();
        }

        op = new ModelNode();
        op.get(OP).set("resume-servers");
        client.execute(op);
        HttpRequest.get(address + "?" + TestUndertowService.SKIP_GRACEFUL + "=true", 10, TimeUnit.SECONDS);
        Assert.assertEquals(SuspendResumeHandler.TEXT, HttpRequest.get(address, 60, TimeUnit.SECONDS));

        op = new ModelNode();
        op.get(ModelDescriptionConstants.OP).set("stop-servers");
        op.get(ModelDescriptionConstants.TIMEOUT).set(60);
        op.get(ModelDescriptionConstants.BLOCKING).set(true);
        client.execute(op);


        op.get(ModelDescriptionConstants.OP).set("start-servers");
        op.get(ModelDescriptionConstants.BLOCKING).set(true);
        op.get(ModelDescriptionConstants.START_MODE).set(SUSPEND);
        client.execute(op);
        conn = (HttpURLConnection) new URL(address).openConnection();
        try {
            conn.setDoInput(true);
            int responseCode = conn.getResponseCode();
            Assert.assertEquals(503, responseCode);
        } finally {
            conn.disconnect();
        }
        op = new ModelNode();
        op.get(ADDRESS).set(PathAddress.parseCLIStyleAddress("/host=primary/server=main-one").toModelNode());
        op.get(OP).set(READ_ATTRIBUTE_OPERATION);
        op.get(NAME).set(SUSPEND_STATE);
        Assert.assertEquals("SUSPENDED", client.execute(op).get(RESULT).asString());

        op = new ModelNode();
        op.get(OP).set("resume-servers");
        client.execute(op);
        HttpRequest.get(address + "?" + TestUndertowService.SKIP_GRACEFUL + "=true", 10, TimeUnit.SECONDS);
        Assert.assertEquals(SuspendResumeHandler.TEXT, HttpRequest.get(address, 60, TimeUnit.SECONDS));
    }

    @Test
    public void testHostGracefulShutdown() throws Exception {
        DomainClient client = domainMasterLifecycleUtil.getDomainClient();

        final String address = "http://" + TestSuiteEnvironment.getServerAddress() + ":8080/web-suspend";
        final ExecutorService executorService = Executors.newFixedThreadPool(2);

        Future<ModelNode> shutdownResult = null;
        boolean appLocked = false;
        try {
            Future<Object> result = executorService.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    return HttpRequest.get(address, TimeoutUtil.adjust(60), TimeUnit.SECONDS);
                }
            });
            appLocked = true;
            TimeUnit.SECONDS.sleep(TimeoutUtil.adjust(1));

            shutdownResult = executorService.submit(new Callable<ModelNode>() {
                @Override
                public ModelNode call() throws Exception {
                    ModelNode op = new ModelNode();
                    op.get(OP).set("shutdown");
                    op.get(OP_ADDR).set(MASTER_ADDR.toModelNode());
                    op.get(ModelDescriptionConstants.SUSPEND_TIMEOUT).set(TimeoutUtil.adjust(60));
                    op.get(ModelDescriptionConstants.RESTART).set(false);
                    return domainMasterLifecycleUtil.executeAwaitConnectionClosed(op);
                }
            });

            DomainTestUtils.waitUntilSuspendState(client, MASTER_ADDR.append(SERVER_MAIN_ONE), SUSPENDING);

            //make sure requests are being rejected
            final HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
            try {
                conn.setDoInput(true);
                int responseCode = conn.getResponseCode();
                Assert.assertEquals(503, responseCode);
            } finally {
                conn.disconnect();
            }

            //make sure the server is still up, and trigger the actual shutdown
            HttpRequest.get(address + "?" + TestUndertowService.SKIP_GRACEFUL + "=true", TimeoutUtil.adjust(10), TimeUnit.SECONDS);
            appLocked = false;

            //make sure our initial request completed
            Assert.assertEquals(SuspendResumeHandler.TEXT, result.get());

            ModelNode shutdownOpResult = shutdownResult.get(TimeoutUtil.adjust(10), TimeUnit.SECONDS);
            Assert.assertTrue("There was a failure executing the shutdown operation", SUCCESS.equals(shutdownOpResult.get(OUTCOME).asString()));

        } finally {
            if (appLocked) {
                HttpRequest.get(address + "?" + TestUndertowService.SKIP_GRACEFUL + "=true", TimeoutUtil.adjust(10), TimeUnit.SECONDS);
            }

            if (shutdownResult != null) {
                try {
                    shutdownResult.get(TimeoutUtil.adjust(10), TimeUnit.SECONDS);
                } catch (Exception e) {
                    domainMasterLifecycleUtil.stop();
                    domainMasterLifecycleUtil.start();
                }
            }

            if (!domainMasterLifecycleUtil.isHostControllerStarted()) {
                domainMasterLifecycleUtil.start();
            }
        }
    }

    public static JavaArchive createDeployment() throws Exception {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, WEB_SUSPEND_JAR);
        jar.addPackage(SuspendResumeHandler.class.getPackage());
        jar.addAsServiceProvider(ServiceActivator.class, TestSuspendServiceActivator.class);
        jar.addAsResource(new StringAsset("Dependencies: org.jboss.dmr, org.jboss.as.controller, io.undertow.core, org.jboss.as.server,org.wildfly.extension.request-controller, org.jboss.as.network\n"), "META-INF/MANIFEST.MF");
        jar.addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(
                new ReflectPermission("suppressAccessChecks"),
                new RuntimePermission("createXnioWorker"),
                new SocketPermission(TestSuiteEnvironment.getServerAddress() + ":8080", "listen,resolve"),
                new SocketPermission("*", "accept,resolve")
        ), "permissions.xml");
        return jar;
    }
}
