    /*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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
package org.wildfly.core.test.standalone.suspend.web;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper;
import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.as.test.shared.TestSuiteEnvironment;
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
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.test.suspendresumeendpoint.SuspendResumeHandler;
import org.wildfly.test.suspendresumeendpoint.TestSuspendServiceActivator;
import org.wildfly.test.suspendresumeendpoint.TestUndertowService;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;

    /**
 * Tests for suspend/resume functionality
 */
@RunWith(WildflyTestRunner.class)
public class SuspendResumeTestCase {

    public static final String WEB_SUSPEND_JAR = "web-suspend.jar";
    @Inject
    private static ManagementClient managementClient;

    @BeforeClass
    public static void deploy() throws Exception {
        ServerDeploymentHelper helper = new ServerDeploymentHelper(managementClient.getControllerClient());
        JavaArchive war = ShrinkWrap.create(JavaArchive.class, WEB_SUSPEND_JAR);
        war.addPackage(SuspendResumeHandler.class.getPackage());
        war.addAsServiceProvider(ServiceActivator.class, TestSuspendServiceActivator.class);
        war.addAsResource(new StringAsset("Dependencies: org.jboss.dmr, org.jboss.as.controller, io.undertow.core, org.jboss.as.server,org.wildfly.extension.request-controller, org.jboss.as.network\n"), "META-INF/MANIFEST.MF");
        helper.deploy(WEB_SUSPEND_JAR, war.as(ZipExporter.class).exportAsInputStream());

    }

    @AfterClass
    public static void undeploy() throws ServerDeploymentHelper.ServerDeploymentException {
        ServerDeploymentHelper helper = new ServerDeploymentHelper(managementClient.getControllerClient());
        helper.undeploy(WEB_SUSPEND_JAR);
    }

    @Test
    public void testSuspendResume() throws Exception {

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
            op.get(OP).set("suspend");
            managementClient.getControllerClient().execute(op);

            op = new ModelNode();
            op.get(OP).set(READ_ATTRIBUTE_OPERATION);
            op.get(NAME).set(SUSPEND_STATE);
            Assert.assertEquals("SUSPENDING", managementClient.executeForResult(op).asString());

            HttpRequest.get(address + "?" + TestUndertowService.SKIP_GRACEFUL + "=true", 10, TimeUnit.SECONDS);
            Assert.assertEquals(SuspendResumeHandler.TEXT, result.get());
            Assert.assertEquals("SUSPENDED", managementClient.executeForResult(op).asString());

            final HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
            try {
                conn.setDoInput(true);
                int responseCode = conn.getResponseCode();
                Assert.assertEquals(503, responseCode);
            } finally {
                conn.disconnect();
            }

            op = new ModelNode();
            op.get(OP).set("resume");
            managementClient.getControllerClient().execute(op);

            Assert.assertEquals(SuspendResumeHandler.TEXT, HttpRequest.get(address, 60, TimeUnit.SECONDS));
        } finally {
            HttpRequest.get(address + "?" + TestUndertowService.SKIP_GRACEFUL, 10, TimeUnit.SECONDS);
            executorService.shutdown();

            ModelNode op = new ModelNode();
            op.get(OP).set("resume");
            managementClient.getControllerClient().execute(op);
        }


    }
}
