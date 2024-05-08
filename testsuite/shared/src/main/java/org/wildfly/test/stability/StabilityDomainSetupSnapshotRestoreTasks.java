/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.stability;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_ENVIRONMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_OPERATION_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_OPERATION_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD_ENHANCED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STABILITY;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.domain.management.util.WildFlyManagedConfiguration;
import org.jboss.as.test.integration.management.ManagementOperations;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Assume;
import org.wildfly.test.snapshot.DomainSnapshot;

/**
 * A task to set up the domain to be reloaded to a desired stability level.
 * The setup method will take a snapshot of the current domain configuration and reload the domain to the desired stability level.
 * The tear down method will restore the domain to the original stability level.
 */
public class StabilityDomainSetupSnapshotRestoreTasks {

    private final Stability desiredStability;
    private final DomainTestSupport testSupport;
    private final PathAddress hostPrimaryAddress;
    private final PathAddress hostSecondaryAddress;
    private final Map<PathAddress, DomainLifecycleUtil> hostAddresses = new HashMap<>();
    private AutoCloseable snapshot;

    /**
     * Constructor
     *
     * @param desiredStability The stability level to which the domain should be reloaded
     * @param testSupport The domain test support
     */
    public StabilityDomainSetupSnapshotRestoreTasks(Stability desiredStability, DomainTestSupport testSupport) {
        if (testSupport == null) {
            throw new IllegalArgumentException("testSupport is null");
        }
        this.desiredStability = desiredStability;
        this.testSupport = testSupport;

        WildFlyManagedConfiguration domainPrimaryConfiguration = testSupport.getDomainPrimaryConfiguration();
        WildFlyManagedConfiguration domainSecondaryConfiguration = testSupport.getDomainSecondaryConfiguration();

        hostPrimaryAddress = PathAddress.pathAddress(HOST, domainPrimaryConfiguration.getHostName());
        hostSecondaryAddress = domainSecondaryConfiguration != null ? PathAddress.pathAddress(HOST, domainSecondaryConfiguration.getHostName()) : null;
    }

    public void setup() throws Exception {
        assumeStability(hostPrimaryAddress, testSupport.getDomainPrimaryLifecycleUtil());
        assumeStability(hostSecondaryAddress, testSupport.getDomainSecondaryLifecycleUtil());

        // Take a snapshot, indicating that when reloading we want to go back to the original stability
        snapshot = DomainSnapshot.takeSnapshot(testSupport);

        // All good, let's do it!
        reloadToDesiredStability(hostPrimaryAddress, testSupport.getDomainPrimaryLifecycleUtil(), desiredStability);
        reloadToDesiredStability(hostSecondaryAddress, testSupport.getDomainSecondaryLifecycleUtil(), desiredStability);
    }

    public void tearDown() throws Exception {
        if (snapshot != null) {
            snapshot.close();
        }
    }

    private void assumeStability(PathAddress hostAddress, DomainLifecycleUtil domainLifecycleUtil) throws Exception {
        if (domainLifecycleUtil == null) {
            return;
        }

        // Make sure the desired stability level is one of the ones supported by the server
        Set<Stability> supportedStabilityLevels = getSupportedStabilityLevels(hostAddress, domainLifecycleUtil.getDomainClient());

        Assume.assumeTrue(
                String.format("%s is not a supported stability level. Supported levels are %s", desiredStability, supportedStabilityLevels),
                supportedStabilityLevels.contains(desiredStability));


        // Check the reload-enhanced operation exists in the current stability level
        Assume.assumeTrue(
                "The reload-enhanced operation is not registered at this stability level",
                checkReloadEnhancedOperationIsAvailable(hostAddress, domainLifecycleUtil.getDomainClient()));

        // Check the reload-enhanced operation exists in the stability level we want to load to so that
        // we can reload back to the current one
        Stability reloadOpStability = getReloadEnhancedOperationStabilityLevel(hostAddress, domainLifecycleUtil.getDomainClient());
        Assume.assumeTrue(desiredStability.enables(reloadOpStability));
    }

    private Stability getReloadEnhancedOperationStabilityLevel(PathAddress hostAddress, ModelControllerClient managementClient) throws Exception {
        ModelNode op = Util.createOperation(READ_OPERATION_DESCRIPTION_OPERATION, hostAddress);
        op.get(NAME).set(RELOAD_ENHANCED);

        ModelNode result = ManagementOperations.executeOperation(managementClient, op);
        String stability = result.get(STABILITY).asString();
        return Stability.fromString(stability);
    }

    private Set<Stability> getSupportedStabilityLevels(PathAddress hostAddress, ModelControllerClient managementClient) throws IOException, MgmtOperationException {
        ModelNode op = Util.getReadAttributeOperation(hostAddress.append(
                        PathAddress.pathAddress(CORE_SERVICE, HOST_ENVIRONMENT)),
                "permissible-stability-levels");

        ModelNode result = ManagementOperations.executeOperation(managementClient, op);
        Set<Stability> set = new HashSet<>();
        for (ModelNode mn : result.asList()) {
            set.add(Stability.fromString(mn.asString()));
        }
        return set;
    }

    private boolean checkReloadEnhancedOperationIsAvailable(PathAddress hostAddress, ModelControllerClient managementClient) throws Exception {
        ModelNode op = Util.createOperation(READ_OPERATION_NAMES_OPERATION, hostAddress.append(PathAddress.EMPTY_ADDRESS));
        ModelNode result = DomainTestUtils.executeForResult(op, managementClient);
        for (ModelNode name : result.asList()) {
            if (name.asString().equals(RELOAD_ENHANCED)) {
                return true;
            }
        }
        return false;
    }

    private void reloadToDesiredStability(PathAddress hostAddress, DomainLifecycleUtil domainLifecycleUtil, Stability stability) throws Exception {
        if (domainLifecycleUtil == null) {
            // Ignore, this is the case when there is no secondary Host Controller configured on the domain
            return;
        }

        // Check the stability
        DomainClient client = domainLifecycleUtil.getDomainClient();
        Stability currentStability = readCurrentStability(hostAddress, client);
        if (currentStability == stability) {
            return;
        }

        //Reload the server to the desired stability level
        domainLifecycleUtil.reload(hostAddress.getLastElement().getValue(), stability, null, null);
        client = domainLifecycleUtil.getDomainClient();
        Stability reloadedStability = readCurrentStability(hostAddress, client);
        Assert.assertEquals(stability, reloadedStability);
    }

    private Stability readCurrentStability(PathAddress hostAddress, ModelControllerClient client) throws Exception {
        ModelNode op = Util.getReadAttributeOperation(hostAddress.append(PathAddress.pathAddress(CORE_SERVICE, HOST_ENVIRONMENT)), STABILITY);
        ModelNode result = ManagementOperations.executeOperation(client, op);
        return Stability.fromString(result.asString());
    }
}
