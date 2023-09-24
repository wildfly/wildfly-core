/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

/**
 * Marker that can be used to determine if a deployment is an exploded deployment or not
 *
 * @author Stuart Douglas
 */
public class ExplodedDeploymentMarker {

    private static final AttachmentKey<Boolean> EXPLODED_DEPLOYMENT = AttachmentKey.create(Boolean.class);

    public static void markAsExplodedDeployment(final DeploymentUnit deploymentUnit) {
        deploymentUnit.putAttachment(EXPLODED_DEPLOYMENT, true);
    }

    public static boolean isExplodedDeployment(final DeploymentUnit deploymentUnit) {
        Boolean exploded = deploymentUnit.getAttachment(EXPLODED_DEPLOYMENT);
        return exploded != null ? exploded : false;
    }
}
