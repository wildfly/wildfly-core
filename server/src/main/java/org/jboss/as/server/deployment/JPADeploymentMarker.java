/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;


/**
 * JPA Deployment marker
 *
 * @author Scott Marlow (copied from WeldDeploymentMarker)
 */
public class JPADeploymentMarker {

    private static final AttachmentKey<Boolean> MARKER = AttachmentKey.create(Boolean.class);

    /**
     * Mark the top level deployment as being a JPA deployment. If the deployment is not a top level deployment the parent is
     * marked instead
     */
    public static void mark(DeploymentUnit unit) {
        unit = DeploymentUtils.getTopDeploymentUnit(unit);
        unit.putAttachment(MARKER, Boolean.TRUE);
    }

    /**
     * return true if the {@link DeploymentUnit} is part of a JPA deployment
     */
    public static boolean isJPADeployment(DeploymentUnit unit) {
        unit = DeploymentUtils.getTopDeploymentUnit(unit);
        return unit.getAttachment(MARKER) != null;
    }

    private JPADeploymentMarker() {

    }
}
