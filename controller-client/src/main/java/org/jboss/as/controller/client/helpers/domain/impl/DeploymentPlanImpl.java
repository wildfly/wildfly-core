/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jboss.as.controller.client.helpers.domain.DeploymentAction;
import org.jboss.as.controller.client.helpers.domain.DeploymentPlan;
import org.jboss.as.controller.client.helpers.domain.ServerGroupDeploymentPlan;


/**
 * Describes a set of actions to take to change the deployment content available
 * to deployed in a server group or set of server groups.
 *
 * @author Brian Stansberry
 */
public class DeploymentPlanImpl implements DeploymentPlan, Serializable {

    private static final long serialVersionUID = -7652253540766375101L;

    private final DeploymentSetPlanImpl delegate;
    private final boolean rollbackAcrossGroups;

    DeploymentPlanImpl(DeploymentSetPlanImpl delegate,final boolean rollbackAcrossGroups) {
        this.delegate = delegate;
        this.rollbackAcrossGroups = rollbackAcrossGroups;
    }

    @Override
    public UUID getId() {
        return delegate.getId();
    }

    public DeploymentAction getLastAction() {
        return delegate.getLastAction();
    }

    @Override
    public List<DeploymentAction> getDeploymentActions() {
        return delegate.getDeploymentActions();
    }

    @Override
    public boolean isSingleServerRollback() {
        return delegate.isRollback();
    }

    @Override
    public boolean isRollbackAcrossGroups() {
        return rollbackAcrossGroups;
    }

    @Override
    public long getGracefulShutdownTimeout() {
        return delegate.getGracefulShutdownTimeout();
    }

    @Override
    public boolean isGracefulShutdown() {
        return delegate.isGracefulShutdown();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public List<Set<ServerGroupDeploymentPlan>> getServerGroupDeploymentPlans() {
        return delegate.getServerGroupDeploymentPlans();
    }

    List<DeploymentActionImpl> getDeploymentActionImpls() {
        List<DeploymentAction> actions = delegate.getDeploymentActions();
        List<DeploymentActionImpl> cast = new ArrayList<DeploymentActionImpl>(actions.size());
        for (DeploymentAction action : actions) {
            cast.add(DeploymentActionImpl.class.cast(action));
        }
        return cast;
    }
}
