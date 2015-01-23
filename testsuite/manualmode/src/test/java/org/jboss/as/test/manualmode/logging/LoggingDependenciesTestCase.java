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
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Create a deployment with dependency to log4j module.
 * Verify, that it can be deployed (server logging modules should be used by default).
 * Disable the server logging modules (add-logging-api-dependencies=false).
 * Verify, that exception is thrown during deployment.
 *
 * @author <a href="mailto:pkremens@redhat.com">Petr Kremensky</a>
 */
@RunWith(WildflyTestRunner.class)
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

        executeOperation(Operations.createWriteAttributeOperation(createAddress(), API_DEPENDENCIES, true));
        container.stop();
    }

    @Test(expected = ServerDeploymentException.class)
    public void disableLoggingDependencies() throws Exception {
        final JavaArchive archive = createDeployment(Log4jServiceActivator.class, Log4jServiceActivator.DEPENDENCIES);
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