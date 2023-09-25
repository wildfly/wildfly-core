/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deploymentoverlay;

import java.util.Set;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;

/**
 * @author Stuart Douglas
 */
public class DeploymentOverlayModel {
    public static final PathElement CONTENT_PATH = PathElement.pathElement(ModelDescriptionConstants.CONTENT);
    public static final PathElement DEPLOYMENT_OVERRIDE_PATH = PathElement.pathElement(ModelDescriptionConstants.DEPLOYMENT_OVERLAY);
    public static final PathElement DEPLOYMENT_OVERRIDE_DEPLOYMENT_PATH = PathElement.pathElement(ModelDescriptionConstants.DEPLOYMENT);

    public static final OperationContext.AttachmentKey<Set<PathAddress>> REMOVED_LINKS = OperationContext.AttachmentKey.create(Set.class);
    public static final OperationContext.AttachmentKey<Set<PathAddress>> REMOVED_CONTENTS = OperationContext.AttachmentKey.create(Set.class);
}
