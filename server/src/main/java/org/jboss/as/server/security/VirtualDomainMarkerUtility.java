/*
 * Copyright 2019 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.server.security;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.msc.service.ServiceName;

/**
 * Utility class to mark a {@link DeploymentUnit} as requiring a virtual SecurityDomain.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class VirtualDomainMarkerUtility {

    private static final AttachmentKey<Boolean> REQUIRED = AttachmentKey.create(Boolean.class);
    private static final ServiceName DOMAIN_SUFFIX = ServiceName.of("security-domain", "virtual");

    public static void virtualDomainRequired(final DeploymentUnit deploymentUnit) {
        DeploymentUnit rootUnit = toRoot(deploymentUnit);
        rootUnit.putAttachment(REQUIRED, Boolean.TRUE);
    }

    public static boolean isVirtualDomainRequired(final DeploymentUnit deploymentUnit) {
        DeploymentUnit rootUnit = toRoot(deploymentUnit);
        Boolean required = rootUnit.getAttachment(REQUIRED);

        return required == null ? false : required.booleanValue();
    }

    public static ServiceName virtualDomainName(final DeploymentUnit deploymentUnit) {
        DeploymentUnit rootUnit = toRoot(deploymentUnit);

        return rootUnit.getServiceName().append(DOMAIN_SUFFIX);
    }

    private static DeploymentUnit toRoot(final DeploymentUnit deploymentUnit) {
        DeploymentUnit result = deploymentUnit;
        DeploymentUnit parent = result.getParent();
        while (parent != null) {
            result = parent;
            parent = result.getParent();
        }

        return result;
    }

}
