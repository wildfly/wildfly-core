/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
import org.wildfly.test.snapshot.ServerSnapshot;

import java.util.HashSet;
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
 *
 * In order to not pollute the configuration with XML from a different stability level following the run of the test,
 * it takes a snapshot of the server configuration in the setup() method, and
 */
public abstract class StabilityServerSetupSnapshotRestoreTasks implements ServerSetupTask {

    private final Stability desiredStability;
    private volatile Stability originalStability;

    private AutoCloseable snapshot;


    public StabilityServerSetupSnapshotRestoreTasks(Stability desiredStability) {
        this.desiredStability = desiredStability;
    }

    @Override
    public final void setup(ManagementClient managementClient) throws Exception {
        // Make sure the desired stability level is one of the ones supported by the server
        Set<Stability> supportedStabilityLevels = getSupportedStabilityLevels(managementClient);
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



        originalStability = readCurrentStability(managementClient.getControllerClient());

        // Take a snapshot, indicating that when reloading we want to go back to the original stability
        snapshot = ServerSnapshot.takeSnapshot(managementClient, originalStability);

        // All good, let's do it!
        reloadToDesiredStability(managementClient.getControllerClient(), desiredStability);

        // Do any additional setup from the sub-classes
        doSetup(managementClient);
    }

    protected void doSetup(ManagementClient managementClient) throws Exception {

    }

    @Override
    public void tearDown(ManagementClient managementClient) throws Exception {
        snapshot.close();
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
    private Set<Stability> getSupportedStabilityLevels(ManagementClient managementClient) throws Exception {
        ModelNode op = Util.getReadAttributeOperation(PathAddress.pathAddress(CORE_SERVICE, SERVER_ENVIRONMENT), "permissible-stability-levels");
        ModelNode result = ManagementOperations.executeOperation(managementClient.getControllerClient(), op);
        Set<Stability> set = new HashSet<>();
        for (ModelNode mn : result.asList()) {
            set.add(Stability.fromString(mn.asString()));
        }
        return set;
    }

    private Stability reloadToDesiredStability(ModelControllerClient client, Stability stability) throws Exception {
        // Check the stability
        Stability currentStability = readCurrentStability(client);
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
    public static class Default extends StabilityServerSetupSnapshotRestoreTasks {
        public Default() {
            super(Stability.DEFAULT);
        }
    }

    /**
     * A server setup task that sets the server stability to the community level.
     */
    public static class Community extends StabilityServerSetupSnapshotRestoreTasks {
        public Community() {
            super(Stability.COMMUNITY);
        }
    }

    /**
     * A server setup task that sets the server stability to the preview level.
     */
    public static class Preview extends StabilityServerSetupSnapshotRestoreTasks {
        public Preview() {
            super(Stability.PREVIEW);
        }
    }

    /**
     * A server setup task that sets the server stability to the experimental level.
     */
    public static class Experimental extends StabilityServerSetupSnapshotRestoreTasks {
        public Experimental() {
            super(Stability.EXPERIMENTAL);
        }
    }

}
