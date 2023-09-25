/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.reflect;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;

/**
 * The processor to remove the reflection index from the deployment unit.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class CleanupReflectionIndexProcessor implements DeploymentUnitProcessor {

    /** {@inheritDoc} */
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        phaseContext.getDeploymentUnit().removeAttachment(Attachments.REFLECTION_INDEX);
        phaseContext.getDeploymentUnit().removeAttachment(Attachments.PROXY_REFLECTION_INDEX);
    }

}
