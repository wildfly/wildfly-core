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
package org.wildfly.core.test.standalone.suspend.web;

import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper;
import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.test.suspendresumeendpoint.SuspendResumeHandler;
import org.wildfly.test.suspendresumeendpoint.TestSuspendServiceActivator;
import org.wildfly.test.suspendresumeendpoint.TestUndertowService;

import javax.inject.Inject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND_STATE;

/**
 * Tests for reload operation using suspend-timeout in standalone mode.
 */
@RunWith(WildflyTestRunner.class)
public class ReloadSuspendTimeoutTestCase {

    public static final String WEB_SUSPEND_JAR = "web-suspend.jar";

    @Inject
    private static ServerController serverController;

    @BeforeClass
    public static void deploy() throws Exception {
        JavaArchive war = ShrinkWrap.create(JavaArchive.class, WEB_SUSPEND_JAR);
        war.addPackage(SuspendResumeHandler.class.getPackage());
        war.addAsServiceProvider(ServiceActivator.class, TestSuspendServiceActivator.class);
        war.addAsResource(new StringAsset("Dependencies: org.jboss.dmr, org.jboss.as.controller, io.undertow.core, org.jboss.as.server,org.wildfly.extension.request-controller, org.jboss.as.network\n"), "META-INF/MANIFEST.MF");
        serverController.deploy(war, WEB_SUSPEND_JAR);
    }

    @AfterClass
    public static void undeploy() throws ServerDeploymentHelper.ServerDeploymentException {
        serverController.undeploy(WEB_SUSPEND_JAR);
    }

    @Test
    public void testSuspendTimeout() throws Exception {
        final String address = "http://" + TestSuiteEnvironment.getServerAddress() + ":8080/web-suspend";
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<Object> result = executorService.submit(() -> HttpRequest.get(address, 60, TimeUnit.SECONDS));

            Thread.sleep(1000); //nasty, but we need to make sure the HTTP request has started

            serverController.reloadSuspendTimeout(false, 60);

            //Server should be rejecting new requests with 503
            final HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
            try {
                conn.setDoInput(true);
                int responseCode = conn.getResponseCode();
                Assert.assertEquals(503, responseCode);
            } finally {
                conn.disconnect();
            }

            //finish the block of deployed application
            HttpRequest.get(address + "?" + TestUndertowService.SKIP_GRACEFUL, 10, TimeUnit.SECONDS);
            Assert.assertEquals(SuspendResumeHandler.TEXT, result.get());

            //wait until reload finish
            serverController.waitForLiveServerToReload(10 * 1000);

            //Server suspend state should be RUNNING
            ModelNode op = new ModelNode();
            op.get(OP).set(READ_ATTRIBUTE_OPERATION);
            op.get(NAME).set(SUSPEND_STATE);

            Assert.assertEquals("RUNNING", serverController.getClient().executeForResult(op).asString());

        } finally {
            executorService.shutdown();
        }
    }
}
