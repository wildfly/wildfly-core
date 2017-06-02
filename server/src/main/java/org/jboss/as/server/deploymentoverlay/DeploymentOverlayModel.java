/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
