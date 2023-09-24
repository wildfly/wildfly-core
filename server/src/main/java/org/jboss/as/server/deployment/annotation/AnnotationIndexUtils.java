/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.annotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.jandex.Index;

/**
 * Utility class that allows easy access to all annotation Indexes in a deployment.
 *
 * Note that this will not resolve indexes from Class-Path entries
 *
 * @author Stuart Douglas
 * @author Ales Justin
 */
public class AnnotationIndexUtils {

    public static Map<ResourceRoot, Index> getAnnotationIndexes(final DeploymentUnit deploymentUnit) {
        final List<ResourceRoot> allResourceRoots = new ArrayList<ResourceRoot>();
        allResourceRoots.addAll(deploymentUnit.getAttachmentList(Attachments.RESOURCE_ROOTS));
        allResourceRoots.add(deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT));
        Map<ResourceRoot, Index> indexes = new HashMap<ResourceRoot, Index>();
        for (ResourceRoot resourceRoot : allResourceRoots) {
            if (resourceRoot == null)
                continue;

            Index index = resourceRoot.getAttachment(Attachments.ANNOTATION_INDEX);
            if (index != null) {
                indexes.put(resourceRoot, index);
            }
        }
        return indexes;
    }

}
