/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.client.helpers.ClientConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;

import java.lang.reflect.ReflectPermission;
import java.net.SocketPermission;
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
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
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
 * Tests suspend-servers and resume-servers operations at host level.
 * <p>
 * The test starts domain controller and a secondary. It triggers the suspend-servers operation in the DC and verifies that
 * the suspend-status changes until getting the suspend value. Finally, it triggers a resume-servers in the DC and
 * verifies that the server in the DC gets the running state again. The servers managed by the secondary host controller
 * should be all the time in running state.
 *
 * @author Yeray Borges
 */
public class HostSuspendResumeTestCase {
    public static final String SUSPEND_STATE = "suspend-state";
    public static final String RUNNING = "RUNNING";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String SUSPENDING = "SUSPENDING";
    public static final String SUSPEND_SERVERS = "suspend-servers";
    public static final String RESUME_SERVERS = "resume-servers";
    public static final String WEB_SUSPEND_JAR = "web-suspend.jar";
    public static final String MAIN_SERVER_GROUP = "main-server-group";

    public static final PathAddress SECONDARY_ADDR = PathAddress.pathAddress(HOST, "secondary");
    public static final PathAddress PRIMARY_ADDR = PathAddress.pathAddress(HOST, "primary");

    public static final PathAddress SERVER_MAIN_ONE = PathAddress.pathAddress(SERVER, "main-one");
    public static final PathAddress SERVER_MAIN_THREE = PathAddress.pathAddress(SERVER, "main-three");

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    @BeforeClass
    public static void setupDomain() {
        testSupport = DomainTestSuite.createSupport(HostSuspendResumeTestCase.class.getSimpleName());

        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() {
        testSupport = null;
        domainPrimaryLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Before
    public void deployTestApplication() throws Exception {
        DomainClient client = domainPrimaryLifecycleUtil.getDomainClient();

        DomainDeploymentManager deploymentManager = client.getDeploymentManager();
        DeploymentPlan plan = deploymentManager.newDeploymentPlan()
                .add(WEB_SUSPEND_JAR, createDeployment().as(ZipExporter.class).exportAsInputStream())
                .andDeploy().toServerGroup(MAIN_SERVER_GROUP)
                .build();

        deploymentManager.execute(plan).get();
    }

    @After
    public void undeployTestApplication() throws Exception {
        DomainClient client = domainPrimaryLifecycleUtil.getDomainClient();

        DomainDeploymentManager deploymentManager = client.getDeploymentManager();
        DeploymentPlan plan = deploymentManager.newDeploymentPlan().undeploy(WEB_SUSPEND_JAR)
                .andRemoveUndeployed()
                .toServerGroup(MAIN_SERVER_GROUP)
                .build();

        deploymentManager.execute(plan).get();
    }

    @Test
    public void hostSuspendAndResume() throws Exception {
        final String appUrl = "http://" + TestSuiteEnvironment.getServerAddress() + ":8080/web-suspend";

        final ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {

            Future<Object> result = executorService.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    return HttpRequest.get(appUrl, TimeoutUtil.adjust(30), TimeUnit.SECONDS);
                }
            });

            TimeUnit.SECONDS.sleep(TimeoutUtil.adjust(1)); //nasty, but we need to make sure the HTTP request has started

            final DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
            final DomainClient secondaryClient = domainSecondaryLifecycleUtil.getDomainClient();

            executorService.submit(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    ModelNode op = Util.createOperation(SUSPEND_SERVERS, PRIMARY_ADDR);
                    op.get(ModelDescriptionConstants.SUSPEND_TIMEOUT).set(TimeoutUtil.adjust(30));
                    return DomainTestUtils.executeForResult(op, domainPrimaryLifecycleUtil.createDomainClient()).asString();
                }
            });

            DomainTestUtils.waitUntilSuspendState(primaryClient, PRIMARY_ADDR.append(SERVER_MAIN_ONE), SUSPENDING);

            ModelNode op = Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER_MAIN_THREE), SUSPEND_STATE);
            Assert.assertEquals(RUNNING, DomainTestUtils.executeForResult(op, secondaryClient).asString());

            HttpRequest.get(appUrl + "?" + TestUndertowService.SKIP_GRACEFUL + "=true", TimeoutUtil.adjust(30), TimeUnit.SECONDS);
            Assert.assertEquals(SuspendResumeHandler.TEXT, result.get());

            op = Util.getReadAttributeOperation(PRIMARY_ADDR.append(SERVER_MAIN_ONE), SUSPEND_STATE);
            Assert.assertEquals(SUSPENDED, DomainTestUtils.executeForResult(op, primaryClient).asString());

            op = Util.createOperation(RESUME_SERVERS, PRIMARY_ADDR);
            DomainTestUtils.executeForResult(op, primaryClient);

            op = Util.getReadAttributeOperation(PRIMARY_ADDR.append(SERVER_MAIN_ONE), SUSPEND_STATE);
            Assert.assertEquals(RUNNING, DomainTestUtils.executeForResult(op, primaryClient).asString());

            op = Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER_MAIN_THREE), SUSPEND_STATE);
            Assert.assertEquals(RUNNING, DomainTestUtils.executeForResult(op, secondaryClient).asString());

        } finally {
            HttpRequest.get(appUrl + "?" + TestUndertowService.SKIP_GRACEFUL, TimeoutUtil.adjust(30), TimeUnit.SECONDS);
            executorService.shutdown();
        }
    }

    private static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class, WEB_SUSPEND_JAR)
                .addPackage(SuspendResumeHandler.class.getPackage())
                .addAsServiceProvider(ServiceActivator.class, TestSuspendServiceActivator.class)
                .addAsResource(new StringAsset("Dependencies: org.jboss.dmr, org.jboss.as.controller, io.undertow.core, org.jboss.as.server, org.wildfly.extension.request-controller, org.jboss.as.network, org.wildfly.service\n"),
                        "META-INF/MANIFEST.MF")
                .addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(
                        new ReflectPermission("suppressAccessChecks"),
                        new RuntimePermission("createXnioWorker"),
                        new SocketPermission(TestSuiteEnvironment.getServerAddress() + ":8080", "listen,resolve"),
                        new SocketPermission("*", "accept,resolve")
        ), "permissions.xml");
    }
}
