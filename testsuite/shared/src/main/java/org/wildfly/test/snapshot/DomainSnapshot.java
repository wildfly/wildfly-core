/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.snapshot;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_ENVIRONMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STABILITY;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.domain.management.util.WildFlyManagedConfiguration;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;

public class DomainSnapshot {
    /**
     * Takes a snapshot of the current state of the domain.
     * <p>
     * Returns a AutoCloseable that can be used to restore the state
     *
     * @param testSupport The test support
     * @return A closeable that can be used to restore the server
     */
    public static AutoCloseable takeSnapshot(DomainTestSupport testSupport) {
        try {
            DomainLifecycleUtil primaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
            DomainClient primaryClient = primaryLifecycleUtil.getDomainClient();

            WildFlyManagedConfiguration domainPrimaryConfiguration = testSupport.getDomainPrimaryConfiguration();
            WildFlyManagedConfiguration domainSecondaryConfiguration = testSupport.getDomainSecondaryConfiguration();

            PathAddress hostPrimaryAddress = PathAddress.pathAddress(HOST, domainPrimaryConfiguration.getHostName());
            PathAddress hostSecondaryAddress = domainSecondaryConfiguration != null ? PathAddress.pathAddress(HOST, domainSecondaryConfiguration.getHostName()) : null;

            // take domain level snapshot
            ModelNode result = DomainTestUtils.executeForResult(Util.createEmptyOperation("take-snapshot", null), primaryClient);
            String domainConfig = result.asString();
            // Domain Config must be specified for reload operation being path relative to the JBoss Configuration Directory
            Path relDomainConfigPath = findSnapShotRelativePath(domainConfig, domainPrimaryConfiguration);

            // take primary snapshot
            final List<Snapshot> snapShots = new ArrayList<>();
            Snapshot snapshot = takeHostSnapShot(hostPrimaryAddress, primaryLifecycleUtil, domainPrimaryConfiguration, relDomainConfigPath);
            snapShots.add(snapshot);

            // take secondary snapshot if it exists
            if (hostSecondaryAddress != null) {
                snapshot = takeHostSnapShot(hostSecondaryAddress, testSupport.getDomainSecondaryLifecycleUtil(), domainSecondaryConfiguration, null);
                snapShots.add(snapshot);
            }

            return new AutoCloseable() {
                @Override
                public void close() throws Exception {
                    for (Snapshot snapshot : snapShots) {
                        DomainLifecycleUtil.ReloadEnhancedParameters reloadParams = new DomainLifecycleUtil.ReloadEnhancedParameters();
                        reloadParams.setStability(snapshot.stability)
                                .setHostConfig(snapshot.hostConfig)
                                .setDomainConfig(snapshot.domainConfig);

                        snapshot.lifecycleUtil.reload(snapshot.hostName, reloadParams);
                        PathAddress hostAddress = PathAddress.pathAddress(HOST, snapshot.hostName);
                        Stability reloadedStability = getStability(hostAddress, snapshot.lifecycleUtil.getDomainClient());
                        Assert.assertSame(reloadedStability, snapshot.stability);
                    }
                }
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to take snapshot", e);
        }
    }

    private static Snapshot takeHostSnapShot(PathAddress hostAddress, DomainLifecycleUtil lifecycleUtil, WildFlyManagedConfiguration configuration, Path relDomainConfigPath) throws IOException, MgmtOperationException {
        DomainClient client = lifecycleUtil.getDomainClient();

        ModelNode result = DomainTestUtils.executeForResult(Util.createEmptyOperation("take-snapshot", hostAddress), client);
        String hostConfig = result.asString();
        Path relHostConfigPath = findSnapShotRelativePath(hostConfig, configuration);

        Stability stability = getStability(hostAddress, client);

        return new Snapshot(relDomainConfigPath, relHostConfigPath.toString(), stability, lifecycleUtil);
    }

    private static Stability getStability(PathAddress hostAddress, DomainClient client) throws IOException, MgmtOperationException {
        ModelNode result;
        ModelNode op = Util.getReadAttributeOperation(hostAddress.append(PathAddress.pathAddress(CORE_SERVICE, HOST_ENVIRONMENT)), STABILITY);
        result = DomainTestUtils.executeForResult(op, client);
        return  Stability.fromString(result.asString());
    }

    private static Path findSnapShotRelativePath(String absPath, WildFlyManagedConfiguration configuration) {
        Path primaryConfigDir = Paths.get(configuration.getDomainDirectory()).resolve("configuration");
        Path absDomainConfigPath = Paths.get(absPath);
        return primaryConfigDir.relativize(absDomainConfigPath);
    }

    private static class Snapshot {
        final String domainConfig;
        final String hostConfig;
        final Stability stability;
        final String hostName;
        final DomainLifecycleUtil lifecycleUtil;

        public Snapshot(Path domainConfig, String hostConfig, Stability stability, DomainLifecycleUtil lifecycleUtil) {
            this.lifecycleUtil = lifecycleUtil;
            this.hostName = lifecycleUtil.getConfiguration().getHostName();
            this.domainConfig = domainConfig != null ? domainConfig.toString() : null;
            this.hostConfig = hostConfig;
            this.stability = stability;
        }
    }
}
