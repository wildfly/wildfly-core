/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.standalone.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jboss.as.controller.client.helpers.standalone.DeploymentAction;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlan;
import org.jboss.as.protocol.StreamUtils;
import org.wildfly.common.Assert;

/**
 * Describes a set of actions to take to change the deployment content available
 * to and/or deployed in a standalone server.
 *
 * @author Brian Stansberry
 */
public class DeploymentPlanImpl implements DeploymentPlan {

    private static final long serialVersionUID = -119621318892470668L;
    private final UUID uuid = UUID.randomUUID();
    private final List<DeploymentActionImpl> deploymentActions = new ArrayList<DeploymentActionImpl>();
    private final boolean globalRollback;
    private final boolean shutdown;
    private final long gracefulShutdownPeriod;

    DeploymentPlanImpl(List<DeploymentActionImpl> actions, boolean globalRollback, boolean shutdown, long gracefulTimeout) {
        Assert.checkNotNullParam("actions", actions);
        this.deploymentActions.addAll(actions);
        this.globalRollback = globalRollback;
        this.shutdown = shutdown;
        this.gracefulShutdownPeriod = gracefulTimeout;
    }

    @Override
    public UUID getId() {
        return uuid;
    }

    @Override
    public List<DeploymentAction> getDeploymentActions() {
        return new ArrayList<DeploymentAction>(deploymentActions);
    }

    @Override
    public boolean isGlobalRollback() {
        return globalRollback;
    }

    @Override
    public long getGracefulShutdownTimeout() {
        return gracefulShutdownPeriod;
    }

    @Override
    public boolean isGracefulShutdown() {
        return shutdown && gracefulShutdownPeriod > -1;
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * Same as {@link #getDeploymentActions()} except the type of the list
     * contents reflects the actual implementation class.
     *
     * @return  the actions. Will not be <code>null</code>
     */
    public List<DeploymentActionImpl> getDeploymentActionImpls() {
        return new ArrayList<DeploymentActionImpl>(deploymentActions);
    }

    void cleanup() {
        for (DeploymentActionImpl action : deploymentActions) {
            if (action.isInternalStream() && action.getContentStream() != null) {
                StreamUtils.safeClose(action.getContentStream());
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        cleanup();
    }
}
