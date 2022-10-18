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

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FULL_REPLACE_DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IN_SERIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MAX_FAILED_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEPLOY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.URL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.util.stream.Collectors;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.deployment.broken.ServiceActivatorDeployment;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests handling of a failed deployment rollout and the removal thereof.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
public class DeploymentRollbackFailureTestCase {

    private static final String BROKEN_DEPLOYMENT = "broken.jar";
    private static final String MSG = "main-server-group";
    private static final PathElement DEPLOYMENT_PATH = PathElement.pathElement(DEPLOYMENT, BROKEN_DEPLOYMENT);
    private static final PathElement MAIN_SERVER_GROUP = PathElement.pathElement(SERVER_GROUP, MSG);
    private static final PathAddress SYS_PROP_ADDR = PathAddress.pathAddress(PathElement.pathElement(HOST, "secondary"),
            PathElement.pathElement(SERVER_CONFIG, "main-three"), PathElement.pathElement(SYSTEM_PROPERTY, ServiceActivatorDeployment.FAIL_SYS_PROP));
    private static DomainTestSupport testSupport;
    private static DomainClient primaryClient;
    private static File tmpDir;
    private static File deployment;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(DeploymentRollbackFailureTestCase.class.getSimpleName());
        primaryClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();

        File tmpRoot = new File(System.getProperty("java.io.tmpdir"));
        tmpDir = new File(tmpRoot, DeploymentRollbackFailureTestCase.class.getSimpleName() + System.currentTimeMillis());
        Files.createDirectory(tmpDir.toPath());
        deployment = new File(tmpDir, BROKEN_DEPLOYMENT);
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, BROKEN_DEPLOYMENT);
        archive.addClass(ServiceActivatorDeployment.class);
        archive.addAsServiceProvider(ServiceActivator.class, ServiceActivatorDeployment.class);
        archive.addAsManifestResource(new StringAsset("Dependencies: org.jboss.msc\n"), "MANIFEST.MF");
        archive.as(ZipExporter.class).exportTo(deployment);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        primaryClient = null;
        DomainTestSuite.stopSupport();

        if (deployment != null && !deployment.delete() && deployment.exists()) {
            deployment.deleteOnExit();
        }
        if (tmpDir != null && !tmpDir.delete() && tmpDir.exists()) {
            tmpDir.deleteOnExit();
        }

    }

    @After
    public void cleanup() throws IOException {
        try {
            cleanDeploymentFromServerGroup();
        } catch (MgmtOperationException e) {
            // ignored
        } finally {
            try {
                cleanDeployment();
            }  catch (MgmtOperationException e) {
                // ignored
            } finally {
                try {
                    cleanSystemProperty();
                }  catch (MgmtOperationException e1) {
                    // ignored
                }

            }
        }
    }

    @Test
    public void test() throws IOException, MgmtOperationException {
        installBrokenDeployment();
        configureDeploymentFailure();
        failedReplaceBrokenDeployment();
    }

    private void configureDeploymentFailure() throws IOException, MgmtOperationException {
        ModelNode op = Util.createAddOperation(SYS_PROP_ADDR);
        op.get(VALUE).set("true");
        DomainTestUtils.executeForResult(op, primaryClient);
    }

    private void installBrokenDeployment() throws IOException, MgmtOperationException {
        ModelNode op = getDeploymentCompositeOp();
        final ModelNode ret = primaryClient.execute(op);
        if (! SUCCESS.equals(ret.get(OUTCOME).asString())) {
            throw new MgmtOperationException("Management operation failed.", op, ret);
        }
        // Validate the server results are included
        ModelNode m3Resp = ret.get(SERVER_GROUPS, MSG, HOST, "secondary", "main-three", RESPONSE);
        Assert.assertTrue(ret.toString(), m3Resp.isDefined());
        Assert.assertEquals(m3Resp.toString(), SUCCESS, m3Resp.get(OUTCOME).asString());
    }

    private void failedReplaceBrokenDeployment() throws IOException, MgmtOperationException {
        ModelNode op = Util.createOperation(FULL_REPLACE_DEPLOYMENT, PathAddress.EMPTY_ADDRESS);
        op.get(NAME).set(BROKEN_DEPLOYMENT);
        ModelNode content = new ModelNode();
        content.get(URL).set(deployment.toURI().toURL().toString());
        op.get(CONTENT).add(content);
        final ModelNode ret = primaryClient.execute(op);
        if (! FAILED.equals(ret.get(OUTCOME).asString())) {
            throw new MgmtOperationException("Management operation succeeded.", op, ret);
        }
        // Validate the server results are included
        ModelNode m3Resp = ret.get(SERVER_GROUPS, MSG, HOST, "secondary", "main-three", RESPONSE);
        Assert.assertTrue(ret.toString(), m3Resp.isDefined());
        Assert.assertEquals(m3Resp.toString(), FAILED, m3Resp.get(OUTCOME).asString());
        Assert.assertTrue(m3Resp.toString(), m3Resp.get(FAILURE_DESCRIPTION).asString().contains(ServiceActivatorDeployment.FAILURE_MESSAGE));

        ModelNode failDesc = ret.get(FAILURE_DESCRIPTION);
        Assert.assertTrue(failDesc.toString(), failDesc.isDefined());
        ModelNode servers = failDesc.asProperty().getValue();
        Assert.assertTrue(failDesc.toString(), servers.isDefined());
        ModelNode serverFail = servers.get(SERVER_GROUP, MSG, HOST, "secondary", "main-three");
        Assert.assertTrue(failDesc.toString(), serverFail.isDefined());
        Assert.assertTrue(failDesc.toString(), serverFail.toString().contains(ServiceActivatorDeployment.FAILURE_MESSAGE));
        String logs = readLogs();
        Assert.assertFalse(logs + " shouldn't contain WFLYCTL0190", logs.contains("WFLYCTL0190"));
        Assert.assertFalse(logs + " shouldn't contain java.util.NoSuchElementException", logs.contains("java.util.NoSuchElementException"));
    }

    private ModelNode getDeploymentCompositeOp() throws MalformedURLException {
        ModelNode op = new ModelNode();
        op.get(OP).set(COMPOSITE);
        ModelNode steps = op.get(STEPS);

        ModelNode depAdd = Util.createAddOperation(PathAddress.pathAddress(DEPLOYMENT_PATH));
        ModelNode content = new ModelNode();
        content.get(URL).set(deployment.toURI().toURL().toString());
        depAdd.get(CONTENT).add(content);

        steps.add(depAdd);

        ModelNode sgAdd = Util.createAddOperation(PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_PATH));
        sgAdd.get(ENABLED).set(true);
        steps.add(sgAdd);
        return op;
    }

    private void succeedInstallFailedDeployment() throws IOException, MgmtOperationException {
        ModelNode op = getDeploymentCompositeOp();
        op.get(OPERATION_HEADERS, ROLLOUT_PLAN).set(getRolloutPlanO());
        DomainTestUtils.executeForResult(op, primaryClient);
    }

    private ModelNode getRolloutPlanO() {
        ModelNode plan = new ModelNode();
        ModelNode sg = plan.get(IN_SERIES).add().get(SERVER_GROUP, "main-server-group");
        sg.get(MAX_FAILED_SERVERS).set(1);
        return plan;
    }

    private ModelNode getUndeployCompositeOp() throws MalformedURLException {
        ModelNode op = new ModelNode();
        op.get(OP).set(COMPOSITE);
        ModelNode steps = op.get(STEPS);

        PathAddress sgDep = PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_PATH);
        steps.add(Util.createEmptyOperation(UNDEPLOY, sgDep));
        steps.add(Util.createRemoveOperation(sgDep));
        steps.add(Util.createRemoveOperation(PathAddress.pathAddress(DEPLOYMENT_PATH)));

        return op;
    }

    private void cleanDeploymentFromServerGroup() throws IOException, MgmtOperationException {
        ModelNode op = Util.createRemoveOperation(PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_PATH));
        op.get(ENABLED).set(true);
        op.get(OPERATION_HEADERS, ROLLOUT_PLAN).set(getRolloutPlanO());
        DomainTestUtils.executeForResult(op, primaryClient);
    }

    private void cleanDeployment() throws IOException, MgmtOperationException {
        ModelNode op = Util.createRemoveOperation(PathAddress.pathAddress(DEPLOYMENT_PATH));
        DomainTestUtils.executeForResult(op, primaryClient);
    }

    private void cleanSystemProperty() throws IOException, MgmtOperationException {
        ModelNode op = Util.createRemoveOperation(SYS_PROP_ADDR);
        DomainTestUtils.executeForResult(op, primaryClient);
    }

    private String readLogs() throws IOException {
        ModelNode readLog = Util.createOperation("read-log-file", PathAddress.parseCLIStyleAddress("/host=secondary/server=main-three/subsystem=logging"));
        readLog.get(NAME).set("server.log");
        readLog.get("tail").set(true);
        readLog.get("lines").set(40);
        final ModelNode ret = primaryClient.execute(readLog);
        Assert.assertEquals(SUCCESS, ret.get(OUTCOME).asString());
        return ret.get(RESULT).asList().stream().map(ModelNode::asString).collect(Collectors.joining(System.lineSeparator()));
    }
}
