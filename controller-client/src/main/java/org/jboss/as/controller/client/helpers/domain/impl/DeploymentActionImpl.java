/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.client.helpers.domain.impl;

import java.io.Serializable;
import java.util.UUID;

import org.jboss.as.controller.client.helpers.domain.DeploymentAction;
import org.wildfly.common.Assert;

/**
 * Implementation of {@link DeploymentAction}.
 *
 * @author Brian Stansberry
 */
public class DeploymentActionImpl implements DeploymentAction, Serializable {

    private static final long serialVersionUID = 613098200977026475L;

    public static DeploymentActionImpl getAddAction(String deploymentName, String fileName, byte[] hash) {
        assert fileName != null : "fileName is null";
        assert hash != null : "hash is null";
        return new DeploymentActionImpl(Type.ADD, deploymentName, fileName, hash, null);
    }

    public static DeploymentActionImpl getDeployAction(String deploymentName) {
        return new DeploymentActionImpl(Type.DEPLOY, deploymentName, null, null, null);
    }

    public static DeploymentActionImpl getRedeployAction(String deploymentName) {
        return new DeploymentActionImpl(Type.REDEPLOY, deploymentName, null, null, null);
    }

    public static DeploymentActionImpl getUndeployAction(String deploymentName) {
        return new DeploymentActionImpl(Type.UNDEPLOY, deploymentName, null, null, null);
    }

    public static DeploymentActionImpl getReplaceAction(String deploymentName, String replacedName) {
        Assert.checkNotNullParam("replacedName", replacedName);
        return new DeploymentActionImpl(Type.REPLACE, deploymentName, null, null, replacedName);
    }

    public static DeploymentActionImpl getFullReplaceAction(String deploymentName, String fileName, byte[] hash) {
        assert fileName != null : "fileName is null";
        assert hash != null : "hash is null";
        return new DeploymentActionImpl(Type.FULL_REPLACE, deploymentName, fileName, hash, null);
    }

    public static DeploymentActionImpl getRemoveAction(String deploymentName) {
        return new DeploymentActionImpl(Type.REMOVE, deploymentName, null, null, null);
    }

    private final UUID uuid = UUID.randomUUID();
    private final Type type;
    private final String deploymentUnitName;
    private final String oldDeploymentUnitName;
    private final String newContentFileName;
    private final byte[] newContentHash;

    private DeploymentActionImpl(Type type, String deploymentUnitName, String newContentFileName, byte[] newContentHash, String replacedDeploymentUnitName) {
        Assert.checkNotNullParam("type", type);
        Assert.checkNotNullParam("deploymentUnitName", deploymentUnitName);
        this.type = type;
        this.deploymentUnitName = deploymentUnitName;
        this.newContentFileName = newContentFileName;
        this.newContentHash = newContentHash;
        this.oldDeploymentUnitName = replacedDeploymentUnitName;
    }

    @Override
    public UUID getId() {
        return uuid;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public String getDeploymentUnitUniqueName() {
        return deploymentUnitName;
    }

    @Override
    public String getReplacedDeploymentUnitUniqueName() {
        return oldDeploymentUnitName;
    }

    public String getNewContentFileName() {
        return newContentFileName;
    }

    public byte[] getNewContentHash() {
        return newContentHash;
    }

}
