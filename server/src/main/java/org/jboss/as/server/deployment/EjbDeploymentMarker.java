/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;


/**
 * Marks a {@link DeploymentUnit} as an EJB deployment.
 *
 * @author Jaikiran Pai
 */
public class EjbDeploymentMarker {

    private static final AttachmentKey<Boolean> ATTACHMENT_KEY = AttachmentKey.create(Boolean.class);

    public static void mark(final DeploymentUnit deployment) {
        deployment.putAttachment(ATTACHMENT_KEY, true);
    }

    public static boolean isEjbDeployment(final DeploymentUnit deploymentUnit) {
        final Boolean val = deploymentUnit.getAttachment(ATTACHMENT_KEY);
        return val != null && val;
    }

}
