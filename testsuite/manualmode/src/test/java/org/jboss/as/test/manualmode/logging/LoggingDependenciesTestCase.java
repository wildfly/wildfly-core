/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.logging;

import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper.ServerDeploymentException;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Create a deployment with dependency to log4j module.
 * Verify, that it can be deployed (server logging modules should be used by default).
 * Disable the server logging modules (add-logging-api-dependencies=false).
 * Verify, that exception is thrown during deployment.
 *
 * @author <a href="mailto:pkremens@redhat.com">Petr Kremensky</a>
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class LoggingDependenciesTestCase extends AbstractLoggingTestCase {
    private static final Logger log = Logger.getLogger(LoggingDependenciesTestCase.class.getName());
    private static final String API_DEPENDENCIES = "add-logging-api-dependencies";

    @Before
    public void startContainer() throws Exception {
        // Start the container
        container.start();
    }

    @After
    public void stopContainer() throws Exception {
        executeOperation(Operations.createUndefineAttributeOperation(createAddress(), API_DEPENDENCIES));
        // No need to undeploy the deployment should be in error, but check the deployments and undeploy if necessary,
        // for example if the test failed
        final ModelNode op = Operations.createReadResourceOperation(PathAddress.pathAddress("deployment", "*").toModelNode());
        final List<ModelNode> result = Operations.readResult(executeOperation(op)).asList();
        if (!result.isEmpty()) {
            try {
                undeploy();
            } catch (ServerDeploymentException e) {
                log.warn("Error undeploying", e);
            }
        }
        container.stop();
    }

    @Test(expected = ServerDeploymentException.class)
    public void disableLoggingDependencies() throws Exception {
        final JavaArchive archive = createDeployment(Log4j2ServiceActivator.class, Log4j2ServiceActivator.DEPENDENCIES);
        // Ensure the log4j deployment can be deployed
        deploy(archive);
        undeploy();

        // Set add-logging-api-dependencies to false
        executeOperation(Operations.createWriteAttributeOperation(createAddress(), API_DEPENDENCIES, false));
        // Restart the container, expect the exception during deployment
        container.stop();
        container.start();
        deploy(archive);
    }
}