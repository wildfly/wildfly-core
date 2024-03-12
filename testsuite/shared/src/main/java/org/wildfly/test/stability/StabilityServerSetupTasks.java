/*
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2024 Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.wildfly.test.stability;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.ManagementOperations;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Assume;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;

import java.util.EnumSet;
import java.util.Set;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_OPERATION_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_OPERATION_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD_ENHANCED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STABILITY;
import static org.jboss.as.server.controller.descriptions.ServerDescriptionConstants.SERVER_ENVIRONMENT;

/**
 * For tests that need to run under a specific server stability level,
 * the server setup tasks from the inner classes can be used to change the stability level of the server to the desired level.
 * Once the test is done, the original stability level is restored.
 */
public abstract class StabilityServerSetupTasks implements ServerSetupTask {

    private final Stability desiredStability;
    private volatile Stability originalStability;


    public StabilityServerSetupTasks(Stability desiredStability) {
        this.desiredStability = desiredStability;
    }

    @Override
    public void setup(ManagementClient managementClient) throws Exception {
        // Make sure the desired stability level is one of the ones supported by the server
        Set<Stability> supportedStabilityLevels = getSupportedStabilityLevels();
        Assume.assumeTrue(
                String.format("%s is not a supported stability level", desiredStability, supportedStabilityLevels),
                supportedStabilityLevels.contains(desiredStability));

        // Check the reload-enhanced operation exists in the current stability level
        Assume.assumeTrue(
                "The reload-enhanced operation is not registered at this stability level",
                checkReloadEnhancedOperationIsAvailable(managementClient));

        // Check the reload-enhanced operation exists in the stability level we want to load to so that
        // we can reload back to the current one
        Stability reloadOpStability = getReloadEnhancedOperationStabilityLevel(managementClient);
        Assume.assumeTrue(desiredStability.enables(reloadOpStability));

        // All good, let's do it!
        reloadToDesiredStability(managementClient.getControllerClient(), desiredStability);
    }

    @Override
    public void tearDown(ManagementClient managementClient) throws Exception {
        reloadToDesiredStability(managementClient.getControllerClient(), originalStability);
    }

    private boolean checkReloadEnhancedOperationIsAvailable(ManagementClient managementClient) throws Exception {
        ModelNode op = Util.createOperation(READ_OPERATION_NAMES_OPERATION, PathAddress.EMPTY_ADDRESS);
        ModelNode result = ManagementOperations.executeOperation(managementClient.getControllerClient(), op);
        for (ModelNode name : result.asList()) {
            if (name.asString().equals(RELOAD_ENHANCED)) {
                return true;
            }
        }
        return false;
    }

    private Stability getReloadEnhancedOperationStabilityLevel(ManagementClient managementClient) throws Exception {
        ModelNode op = Util.createOperation(READ_OPERATION_DESCRIPTION_OPERATION, PathAddress.EMPTY_ADDRESS);
        op.get(NAME).set(RELOAD_ENHANCED);

        ModelNode result = ManagementOperations.executeOperation(managementClient.getControllerClient(), op);
        String stability = result.get(STABILITY).asString();
        return Stability.fromString(stability);

    }
    private Set<Stability> getSupportedStabilityLevels() {
        // TODO WFCORE-6731 - see https://github.com/wildfly/wildfly-core/pull/5895#discussion_r1520489808
        // This information will be available in a management operation, For now just return a hardcoded set
        return EnumSet.allOf(Stability.class);
    }

    private Stability reloadToDesiredStability(ModelControllerClient client, Stability stability) throws Exception {
        // Check the stability
        Stability currentStability = readCurrentStability(client);
        if (originalStability == null) {
            originalStability = currentStability;
        }


        if (currentStability == stability) {
            return originalStability;
        }

        //Reload the server to the desired stability level
        ServerReload.Parameters parameters = new ServerReload.Parameters()
                .setStability(stability);
        // Execute the reload
        ServerReload.executeReloadAndWaitForCompletion(client, parameters);

        Stability reloadedStability = readCurrentStability(client);
        Assert.assertEquals(stability, reloadedStability);
        return originalStability;
    }

    private Stability readCurrentStability(ModelControllerClient client) throws Exception {
        ModelNode op = Util.getReadAttributeOperation(PathAddress.pathAddress(CORE_SERVICE, SERVER_ENVIRONMENT), STABILITY);
        ModelNode result = ManagementOperations.executeOperation(client, op);
        return Stability.fromString(result.asString());
    }

    /**
     * A server setup task that sets the server stability to the default level.
     */
    public static class Default extends StabilityServerSetupTasks {
        public Default() {
            super(Stability.DEFAULT);
        }
    }

    /**
     * A server setup task that sets the server stability to the community level.
     */
    public static class Community extends StabilityServerSetupTasks {
        public Community() {
            super(Stability.COMMUNITY);
        }
    }

    /**
     * A server setup task that sets the server stability to the preview level.
     */
    public static class Preview extends StabilityServerSetupTasks {
        public Preview() {
            super(Stability.PREVIEW);
        }
    }

    /**
     * A server setup task that sets the server stability to the experimental level.
     */
    public static class Experimental extends StabilityServerSetupTasks {
        public Experimental() {
            super(Stability.EXPERIMENTAL);
        }
    }

}
