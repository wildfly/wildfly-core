/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suspendresume;

import java.io.IOException;
import java.lang.reflect.ReflectPermission;
import java.net.HttpURLConnection;
import java.net.SocketPermission;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DeploymentPlan;
import org.jboss.as.controller.client.helpers.domain.DeploymentPlanResult;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.DomainDeploymentManager;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.domain.suites.DomainTestSuite;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.shared.PermissionUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.Assert;
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
public class DomainSuspendResumeTestCase {
    public static final String WEB_SUSPEND_JAR = "web-suspend.jar";
    public static final String MAIN_SERVER_GROUP = "main-server-group";

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    private static final int ADJUSTED_SECOND = TimeoutUtil.adjust(1000);

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(DomainSuspendResumeTestCase.class.getSimpleName());
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainPrimaryLifecycleUtil = null;
        domainSecondaryLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Test
    public void testSuspendResumeDomainLevel() throws Exception {

        DomainClient client = domainPrimaryLifecycleUtil.getDomainClient();

        DomainDeploymentManager deploymentManager = client.getDeploymentManager();
        DeploymentPlan plan = deploymentManager.newDeploymentPlan().add(WEB_SUSPEND_JAR, createDeployment().as(ZipExporter.class).exportAsInputStream())
                .andDeploy().toServerGroup(MAIN_SERVER_GROUP)
                .build();
        DeploymentPlanResult res = deploymentManager.execute(plan).get();


        final String address = "http://" + TestSuiteEnvironment.getServerAddress() + ":8080/web-suspend";
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<Object> result = executorService.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    return HttpRequest.get(address, 60, TimeUnit.SECONDS);
                }
            });

            Thread.sleep(ADJUSTED_SECOND); //nasty, but we need to make sure the HTTP request has started

            ModelNode op = new ModelNode();
            op.get(ModelDescriptionConstants.OP).set("suspend-servers");
            client.execute(op);

            HttpRequest.get(address + "?" + TestUndertowService.SKIP_GRACEFUL + "=true", 10, TimeUnit.SECONDS);
            Assert.assertEquals(SuspendResumeHandler.TEXT, result.get());

            final HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
            try {
                conn.setDoInput(true);
                int responseCode = conn.getResponseCode();
                Assert.assertEquals(503, responseCode);
            } finally {
                conn.disconnect();
            }

            op = new ModelNode();
            op.get(ModelDescriptionConstants.OP).set("resume-servers");
            client.execute(op);

            Assert.assertEquals(SuspendResumeHandler.TEXT, HttpRequest.get(address, 60, TimeUnit.SECONDS));
        } finally {
            try {
                try {
                    HttpRequest.get(address + "?" + TestUndertowService.SKIP_GRACEFUL, 10, TimeUnit.SECONDS);
                    executorService.shutdown();

                    ModelNode op = new ModelNode();
                    op.get(ModelDescriptionConstants.OP).set("resume");
                    client.execute(op);
                } finally {
                    plan = deploymentManager.newDeploymentPlan().undeploy(WEB_SUSPEND_JAR)
                            .andRemoveUndeployed()
                            .toServerGroup(MAIN_SERVER_GROUP)
                            .build();
                    res = deploymentManager.execute(plan).get();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }



    }

    private static ModelNode createOpNode(String address, String operation) {
        ModelNode op = new ModelNode();

        // set address
        ModelNode list = op.get("address").setEmptyList();
        if (address != null) {
            String[] pathSegments = address.split("/");
            for (String segment : pathSegments) {
                String[] elements = segment.split("=");
                list.add(elements[0], elements[1]);
            }
        }
        op.get("operation").set(operation);
        return op;
    }

    private ModelNode executeOperation(final ModelNode op, final ModelControllerClient modelControllerClient) throws IOException, MgmtOperationException {
        return DomainTestUtils.executeForResult(op, modelControllerClient);
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
