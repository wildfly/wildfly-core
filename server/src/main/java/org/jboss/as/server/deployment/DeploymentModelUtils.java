/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;

/**
 * @author Emanuel Muckenhuber
 * @deprecated Use {@link org.jboss.as.server.deployment.DeploymentResourceSupport} from an {@link Attachments#DEPLOYMENT_RESOURCE_SUPPORT attachment} on the {@link org.jboss.as.server.deployment.DeploymentUnit}
 */
@Deprecated(forRemoval = true)
public class DeploymentModelUtils {

    public static final AttachmentKey<Resource> DEPLOYMENT_RESOURCE = DeploymentResourceSupport.DEPLOYMENT_RESOURCE;
    public static final AttachmentKey<ManagementResourceRegistration> MUTABLE_REGISTRATION_ATTACHMENT = DeploymentResourceSupport.MUTABLE_REGISTRATION_ATTACHMENT;
}
