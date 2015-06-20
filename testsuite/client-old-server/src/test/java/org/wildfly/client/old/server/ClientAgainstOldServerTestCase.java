/*
 * JBoss, Home of Professional Open Source.
 * Copyright ${year}, Red Hat, Inc., and individual contributors
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
package org.wildfly.client.old.server;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PRODUCT_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELEASE_VERSION;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.client.old.server.util.LargeDeploymentFile;
import org.wildfly.client.old.server.util.OldVersionTestParameter;
import org.wildfly.client.old.server.util.OldVersionTestRunner;
import org.wildfly.client.old.server.util.Version;
import org.wildfly.core.testrunner.ManagementClient;


/**
 * The OldVersionTestRunner generates the file, and its size is controlled by -Djboss.test.client.old.server.size={@code <bytes>}.
 * The default is 1GB.
 *
 * @author Kabir Khan
 */
@RunWith(OldVersionTestRunner.class)
public class ClientAgainstOldServerTestCase {

    // Max time to wait for some action to complete, in s
    private static final int TIMEOUT = TimeoutUtil.adjust(240);

    private final Version.AsVersion version;

    @Inject
    protected ManagementClient managementClient;

    @Inject
    protected LargeDeploymentFile testDeploymentFile;

    public ClientAgainstOldServerTestCase(OldVersionTestParameter param) {
        this.version = param.getAsVersion();
    }

    @OldVersionTestRunner.OldVersionParameter
    public static List<OldVersionTestParameter> parameters(){
        return OldVersionTestParameter.setupVersions();
    }

    @Test
    public void testVersion() throws Exception {
        checkVersion();
    }

    @Test
    public void testDeployment() throws Exception {
        final ModelControllerClient client = managementClient.getControllerClient();
        final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client);

        //Deploy
        Future<?> future = manager.execute(
                manager.newDeploymentPlan().add(testDeploymentFile.getName(), testDeploymentFile.getFile()).deploy(testDeploymentFile.getName()).build());
        awaitDeploymentExecution(future);

        //Check there
        ModelNode findDeployment = Util.createEmptyOperation(READ_RESOURCE_OPERATION,
                PathAddress.pathAddress(DEPLOYMENT, testDeploymentFile.getName()));
        Assert.assertTrue(
                managementClient.executeForResult(findDeployment).isDefined());

        //Undeploy
        future = manager.execute(manager.newDeploymentPlan().undeploy(testDeploymentFile.getName()).remove(testDeploymentFile.getName()).build());
        awaitDeploymentExecution(future);

        //Check deployment is gone
        ModelNode result = client.execute(findDeployment);
        Assert.assertEquals(FAILED, result.get(OUTCOME).asString());
    }

    private void checkVersion() throws Exception {
        String attribute = version.isEap() ? PRODUCT_VERSION : RELEASE_VERSION;
        String actual = managementClient.executeForResult(Util.getReadAttributeOperation(PathAddress.EMPTY_ADDRESS, attribute)).asString();
        String expected = this.version.getVersion();
        Assert.assertTrue("Expected version to contain '" + expected +"' but the version was '" + actual + "'", actual.contains(expected));
    }

    private void awaitDeploymentExecution(Future<?> future) {
        try {
            future.get(TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }

    }
}
