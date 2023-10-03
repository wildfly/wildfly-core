/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.test.standalone.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.repository.ContentFilter;
import org.jboss.as.repository.ContentRepositoryElement;
import org.jboss.as.repository.PathUtil;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeployment;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.as.test.integration.management.ManagementOperations;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Tests that there is no jar duplication on deployment to save disk space.
 *
 * @author <a href="mailto:aoingl@gmail.com">Lin Gao</a>
 */
@RunWith(WildFlyRunner.class)
public class DeploymentDuplicationTestCase {

    private static final String DEPLOYMENT_NAME = DeploymentDuplicationTestCase.class.getSimpleName() + ".jar";

    private static final Map<String, String> DEFAULT_MAP = Collections.singletonMap(ServiceActivatorDeployment.DEFAULT_SYS_PROP_NAME,
            ServiceActivatorDeployment.DEFAULT_SYS_PROP_VALUE);

    @Inject
    private ManagementClient managementClient;

    @Test
    public void test() throws Exception {
        ModelControllerClient mcc = managementClient.getControllerClient();
        JavaArchive deployment = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive(DEPLOYMENT_NAME, DEFAULT_MAP);
        Operation deploymentOp = createDeploymentOp(deployment);
        try {
            ManagementOperations.executeOperation(mcc, deploymentOp);
            ServiceActivatorDeploymentUtil.validateProperties(mcc, DEFAULT_MAP);

            Path tempPath = Paths.get(ManagementOperations.executeOperation(mcc, readTempPathOp()).asString());
            List<ContentRepositoryElement> elements = PathUtil.listFiles(tempPath, null, new ContentFilter() {
                @Override
                public boolean acceptFile(Path rootPath, Path file) throws IOException {
                    return file.endsWith("content");
                }
                @Override
                public boolean acceptFile(Path rootPath, Path file, InputStream in) throws IOException {
                    return file.endsWith("content");
                }
                @Override
                public boolean acceptDirectory(Path rootPath, Path path) throws IOException {
                    return false;
                }
            });
            // Check temp directory to make sure no duplication
            Assert.assertTrue("There should be no content file in the tmp directory", elements.isEmpty());
        } finally {
            ManagementOperations.executeOperation(mcc, Util.createRemoveOperation(PathAddress.pathAddress("deployment", deployment.getName())));
            ServerReload.executeReloadAndWaitForCompletion(mcc);
        }
    }

    private Operation readTempPathOp() {
        final ModelNode readAttributeOperation = Util.getReadAttributeOperation(PathAddress.pathAddress("path", ServerEnvironment.SERVER_TEMP_DIR), "path");
        return Operation.Factory.create(readAttributeOperation, Collections.emptyList(), true);
    }

    private Operation createDeploymentOp(JavaArchive deployment) {
        final List<InputStream> streams = new ArrayList<>();
        streams.add(deployment.as(ZipExporter.class).exportAsInputStream());
        final ModelNode addOperation = Util.createAddOperation(PathAddress.pathAddress("deployment", deployment.getName()));
        addOperation.get("enabled").set(true);
        addOperation.get("content").add().get("input-stream-index").set(0);
        return Operation.Factory.create(addOperation, streams, true);
    }

}
