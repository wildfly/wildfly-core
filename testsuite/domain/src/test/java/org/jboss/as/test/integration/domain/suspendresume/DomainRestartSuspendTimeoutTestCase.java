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
package org.jboss.as.test.integration.domain.suspendresume;

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
import org.jboss.as.test.shared.TestSuiteEnvironment;
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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART_SERVERS;

/**
 * Test case for domain restart operations using suspend-timeout attribute
 */
public class DomainRestartSuspendTimeoutTestCase {
    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;
    public static final String WEB_SUSPEND_JAR = "web-suspend.jar";
    public static final String MAIN_SERVER_GROUP = "main-server-group";

    // (host=master),(server-config=main-one)
    private static final PathAddress mainOneAddress = PathAddress.pathAddress("host", "master").append("server-config", "main-one");
    // (server-group=main-server-group)
    private static final PathAddress mainServerGroupAddress = PathAddress.pathAddress("server-group", "main-server-group");

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(DomainRestartSuspendTimeoutTestCase.class.getSimpleName());
        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
    }


    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainMasterLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Before
    public void deployTestApplication() throws Exception {
        DomainClient client = domainMasterLifecycleUtil.getDomainClient();

        DomainDeploymentManager deploymentManager = client.getDeploymentManager();
        DeploymentPlan plan = deploymentManager.newDeploymentPlan().add(WEB_SUSPEND_JAR, createDeployment().as(ZipExporter.class).exportAsInputStream())
                .andDeploy().toServerGroup(MAIN_SERVER_GROUP)
                .build();
        deploymentManager.execute(plan).get();
    }

    @After
    public void undeployTestApplication() throws Exception {
        DomainClient client = domainMasterLifecycleUtil.getDomainClient();

        DomainDeploymentManager deploymentManager = client.getDeploymentManager();
        DeploymentPlan plan = deploymentManager.newDeploymentPlan().undeploy(WEB_SUSPEND_JAR)
                .andRemoveUndeployed()
                .toServerGroup(MAIN_SERVER_GROUP)
                .build();
        deploymentManager.execute(plan).get();
    }


    /**
     * Test the execution of :restart-servers(blocking=true, suspend-timeout=60)
     *
     * @throws Exception
     */
    @Test
    public void restartServersWithSuspendTimeoutBlocking() throws Exception {
        executeRestart(RESTART_SERVERS, null, true, 60);
    }

    /**
     * Test the execution of /host=master/server-config=main-one:restart(blocking=true, suspend-timeout=60)
     *
     * @throws Exception
     */
    @Test
    public void restartServerConfigWithSuspendTimeoutBlocking() throws Exception {
        executeRestart(RESTART, mainOneAddress.toModelNode(), true, 60);
    }

    /**
     * Test the execution of server-group=main-server-group:restart-servers(blocking=true, suspend-timeout=60)
     *
     * @throws Exception
     */
    @Test
    public void restartServerGroupWithSuspendTimeoutBlocking() throws Exception {
        executeRestart(RESTART_SERVERS, mainServerGroupAddress.toModelNode(), true, 60);
    }

    /**
     * Test the execution of :restart-servers(blocking=true, suspend-timeout=60)
     *
     * @throws Exception
     */
    @Test
    public void restartServersWithSuspendTimeout() throws Exception {
        executeRestart(RESTART_SERVERS, null, false, 60);
    }

    /**
     * Test the execution of /host=master/server-config=main-one:restart(blocking=true, suspend-timeout=60)
     *
     * @throws Exception
     */
    @Test
    public void restartServerConfigWithSuspendTimeout() throws Exception {
        executeRestart(RESTART, mainOneAddress.toModelNode(), false, 60);
    }

    /**
     * Test the execution of server-group=main-server-group:restart-servers(blocking=true, suspend-timeout=60)
     *
     * @throws Exception
     */
    @Test
    public void restartServerGroupWithSuspendTimeout() throws Exception {
        executeRestart(RESTART_SERVERS, mainServerGroupAddress.toModelNode(), false, 60);
    }

    private void executeRestart(String operation, ModelNode address, boolean blocking, int suspendTimeout) throws InterruptedException, IOException, TimeoutException, ExecutionException {
        final String appUrl = "http://" + TestSuiteEnvironment.getServerAddress() + ":8080/web-suspend";

        final ExecutorService executorService = Executors.newFixedThreadPool(blocking ? 2 : 1);
        try {

            Future<Object> resultHttpRequest = executorService.submit(() -> HttpRequest.get(appUrl, 60, TimeUnit.SECONDS));

            Thread.sleep(1000); //nasty, but we need to make sure the HTTP request has started

            //Trigger operation
            if (blocking) {
                executorService.submit(() -> executeSuspendOperation(operation, address, blocking, suspendTimeout));
            } else {
                executeSuspendOperation(operation, address, blocking, suspendTimeout);
            }

            DomainTestUtils.waitUntilState(domainMasterLifecycleUtil.getDomainClient(), "master", "main-one", "STOPPING");

            //Server should return 503 rejecting requests
            final HttpURLConnection conn = (HttpURLConnection) new URL(appUrl).openConnection();
            try {
                conn.setDoInput(true);
                int responseCode = conn.getResponseCode();
                Assert.assertEquals(503, responseCode);
            } finally {
                conn.disconnect();
            }

            //make sure the server is still up, and trigger the actual shutdown
            HttpRequest.get(appUrl + "?" + TestUndertowService.SKIP_GRACEFUL + "=true", 10, TimeUnit.SECONDS);
            Assert.assertEquals(SuspendResumeHandler.TEXT, resultHttpRequest.get());

            DomainTestUtils.waitUntilState(domainMasterLifecycleUtil.getDomainClient(), "master", "main-one", "STARTED");
        } finally {
            executorService.shutdown();
        }
    }

    private String executeSuspendOperation(String operation, ModelNode address, boolean blocking, int suspendTimeout){
        final ModelNode op = new ModelNode();
        op.get(OP).set(operation);
        if ( address != null ){
            op.get(OP_ADDR).set(address);
        }
        op.get(ModelDescriptionConstants.SUSPEND_TIMEOUT).set(suspendTimeout);
        op.get(ModelDescriptionConstants.BLOCKING).set(blocking);
        return domainMasterLifecycleUtil.executeForResult(op).toString();
    }


    public static JavaArchive createDeployment() throws Exception {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, WEB_SUSPEND_JAR);
        jar.addPackage(SuspendResumeHandler.class.getPackage());
        jar.addAsServiceProvider(ServiceActivator.class, TestSuspendServiceActivator.class);
        jar.addAsResource(new StringAsset("Dependencies: org.jboss.dmr, org.jboss.as.controller, io.undertow.core, org.jboss.as.server,org.wildfly.extension.request-controller, org.jboss.as.network\n"), "META-INF/MANIFEST.MF");
        return jar;
    }
}