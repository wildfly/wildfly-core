/*
 * Copyright 2018 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.manualmode.suspend;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SHUTDOWN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND_STATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND_TIMEOUT;

import java.net.HttpURLConnection;
import java.net.SocketPermission;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.as.test.shared.PermissionUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.test.suspendresumeendpoint.SuspendResumeHandler;
import org.wildfly.test.suspendresumeendpoint.TestSuspendServiceActivator;
import org.wildfly.test.suspendresumeendpoint.TestUndertowService;
import org.xnio.IoUtils;

/**
 * Tests the graceful shutdown in standalone server.
 *
 * @author Yeray Borges
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class SuspendOnShutdownTestCase {
    public static final String WEB_SUSPEND_JAR = "web-suspend.jar";
    public static final String SUSPENDING = "SUSPENDING";
    public static final String SUSPENDED = "SUSPENDED";

    @Inject
    private static ServerController serverController;

    private static ManagementClient managementClient;

    @BeforeClass
    public static void startContainer() {
        serverController.start();
    }

    @AfterClass
    public static void stopContainer() {
        try {
            serverController.stop();
        } finally {
            IoUtils.safeClose(managementClient);
        }
    }

    @Before
    public void deployApplication() throws Exception {
        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class, WEB_SUSPEND_JAR)
                .addPackage(SuspendResumeHandler.class.getPackage())
                .addAsServiceProvider(ServiceActivator.class, TestSuspendServiceActivator.class)
                .addAsResource(new StringAsset("Dependencies: org.jboss.dmr, org.jboss.as.controller, io.undertow.core, org.jboss.as.server,org.wildfly.extension.request-controller, org.jboss.as.network\n"), "META-INF/MANIFEST.MF")
                .addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(
                        new RuntimePermission("createXnioWorker"),
                        new SocketPermission(TestSuiteEnvironment.getServerAddress() + ":8080", "listen,resolve"),
                        new SocketPermission("*", "accept,resolve")
                ), "permissions.xml");

        serverController.deploy(jar, WEB_SUSPEND_JAR);
        managementClient = serverController.getClient();
    }

    @After
    public void undeployApplication() throws Exception {
        serverController.undeploy(WEB_SUSPEND_JAR);
    }

    @Test
    public void testGracefulShutdownWithTimeout() throws Exception {
        final String address = "http://" + TestSuiteEnvironment.getServerAddress() + ":8080/web-suspend";
        final ExecutorService executorService = Executors.newFixedThreadPool(2);

        Future<ModelNode> shutdownResult = null;
        boolean appLocked = false;
        try {

            // Send a request that will block
            final Future<String> result = executorService.submit(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    return HttpRequest.get(address, TimeoutUtil.adjust(10), TimeUnit.SECONDS);
                }
            });

            appLocked = true;

            //Try to ensure that the HTTP request arrives
            TimeUnit.SECONDS.sleep(TimeoutUtil.adjust(1));

            shutdownResult = executorService.submit(new Callable<ModelNode>() {
                @Override
                public ModelNode call() throws Exception {
                    ModelNode op = new ModelNode();
                    op.get(OP).set(SHUTDOWN);
                    op.get(SUSPEND_TIMEOUT).set(TimeoutUtil.adjust(30));
                    return serverController.getClient().executeForResult(op);
                }
            });

            waitForSuspendState(SUSPENDING, false);

            HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
            try {
                conn.setDoInput(true);
                int responseCode = conn.getResponseCode();
                Assert.assertEquals(503, responseCode);
            } finally {
                conn.disconnect();
            }

            HttpRequest.get(address + "?" + TestUndertowService.SKIP_GRACEFUL + "=true", 10, TimeUnit.SECONDS);
            appLocked = false;
            Assert.assertEquals(SuspendResumeHandler.TEXT, result.get(TimeoutUtil.adjust(10), TimeUnit.SECONDS));

            //check if it is in SUSPENDED or ignore if the server was already stopped
            waitForSuspendState(SUSPENDED, true);

        } finally {
            executorService.shutdown();

            if (appLocked) {
                HttpRequest.get(address + "?" + TestUndertowService.SKIP_GRACEFUL + "=true", TimeoutUtil.adjust(10), TimeUnit.SECONDS);
            }

            if (shutdownResult != null) {
                try {
                    shutdownResult.get(TimeoutUtil.adjust(10), TimeUnit.SECONDS);
                    serverController.stop(true);
                    serverController.start();
                } catch (Exception e) {
                    serverController.stop(true);
                    serverController.start();
                    throw e;
                }
            }
        }
    }

    private void waitForSuspendState(String state, boolean ignoreError) throws InterruptedException, UnsuccessfulOperationException {
        ModelNode op = new ModelNode();
        op.get(OP).set(READ_ATTRIBUTE_OPERATION);
        op.get(NAME).set(SUSPEND_STATE);

        String suspendState;
        long timeout = System.currentTimeMillis() + TimeoutUtil.adjust(10000);
        try {
            do {
                suspendState = serverController.getClient().executeForResult(op).asString();
                if (!state.equals(suspendState)) {
                    TimeUnit.MILLISECONDS.sleep(TimeoutUtil.adjust(50));
                } else {
                    break;
                }
            } while (System.currentTimeMillis() < timeout);
        } catch (UnsuccessfulOperationException | RuntimeException e) {
            if (!ignoreError) throw e;
        }
    }
}
