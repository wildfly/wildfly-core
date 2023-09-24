/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.requestcontroller;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.DeploymentUnit;

/**
 * Marker that is attached to the deployment if the request controller is enabled
 *
 * TODO: remove this once we have proper capabilities and requirements
 * @author Stuart Douglas
 */
public class RequestControllerActivationMarker {

    /**
     * The service name of the global request controller.
     *
     * If this is null then the controller is not installed.
     *
     *
     */
    private static final AttachmentKey<Boolean> MARKER = AttachmentKey.create(Boolean.class);

    public static boolean isRequestControllerEnabled(DeploymentUnit du) {
        return du.getAttachment(MARKER) != null;
    }

    static void mark(DeploymentUnit du) {
        du.putAttachment(MARKER, true);
    }

}
