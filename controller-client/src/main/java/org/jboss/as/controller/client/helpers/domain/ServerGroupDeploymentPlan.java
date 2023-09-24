/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain;

import java.io.Serializable;

import org.jboss.as.controller.client.logging.ControllerClientLogger;
import org.wildfly.common.Assert;

/**
 * Indicates how the actions in a {@link DeploymentSetPlan} are to be
 * applied to a particular server group.
 *
 * @author Brian Stansberry
 */
public class ServerGroupDeploymentPlan implements Serializable {

    private static final long serialVersionUID = 4868990805217024722L;

    private final String serverGroupName;
    private final boolean rollback;
    private final boolean rollingToServers;
    private final int maxFailures;
    private final int maxFailurePercentage;

    public ServerGroupDeploymentPlan(final String serverGroupName) {
        this(serverGroupName, false, false, 0, 0);
    }

    private ServerGroupDeploymentPlan(final String serverGroupName, final boolean rollback, final boolean rollingToServers, final int maxFailures, final int maxFailurePercentage) {
        Assert.checkNotNullParam("serverGroupName", serverGroupName);
        this.serverGroupName = serverGroupName;
        this.rollback = rollback;
        this.rollingToServers = rollingToServers;
        this.maxFailures = maxFailures;
        this.maxFailurePercentage = maxFailurePercentage;
    }

    public String getServerGroupName() {
        return serverGroupName;
    }

    public boolean isRollback() {
        return rollback;
    }

    public boolean isRollingToServers() {
        return rollingToServers;
    }

    public int getMaxServerFailures() {
        return maxFailures;
    }

    public int getMaxServerFailurePercentage() {
        return maxFailurePercentage;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof ServerGroupDeploymentPlan
                && ((ServerGroupDeploymentPlan) obj).serverGroupName.equals(serverGroupName));
    }

    @Override
    public int hashCode() {
        return serverGroupName.hashCode();
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName())
            .append("{serverGroupName=")
            .append(serverGroupName)
            .append(",rollback=")
            .append(rollback)
            .append(",rollingToServers=")
            .append(rollingToServers)
            .append("}")
            .toString();
    }

    public ServerGroupDeploymentPlan createRollback() {
        return new ServerGroupDeploymentPlan(serverGroupName, true, rollingToServers, maxFailures, maxFailurePercentage);
    }

    public ServerGroupDeploymentPlan createRollingToServers() {
        return new ServerGroupDeploymentPlan(serverGroupName, rollback, true, maxFailures, maxFailurePercentage);
    }

    public ServerGroupDeploymentPlan createAllowFailures(int serverFailures) {
        if (serverFailures < 1)
            throw ControllerClientLogger.ROOT_LOGGER.invalidValue("serverFailures", serverFailures, 0);
        return new ServerGroupDeploymentPlan(serverGroupName, true, rollingToServers, serverFailures, maxFailurePercentage);
    }

    public ServerGroupDeploymentPlan createAllowFailurePercentage(int serverFailurePercentage) {
        if (serverFailurePercentage < 1 || serverFailurePercentage > 99)
            throw ControllerClientLogger.ROOT_LOGGER.invalidValue("serverFailurePercentage", serverFailurePercentage, 0, 100);
        return new ServerGroupDeploymentPlan(serverGroupName, true, rollingToServers, maxFailures, serverFailurePercentage);
    }

}
