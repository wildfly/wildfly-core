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

import static org.jboss.as.server.deployment.Attachments.CAPABILITY_SERVICE_SUPPORT;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.Services;
import org.jboss.msc.service.ServiceName;

/**
 * Utility class to mark a {@link DeploymentUnit} or {@link OperationContext} as requiring a virtual SecurityDomain.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class VirtualDomainMarkerUtility {

    private static final AttachmentKey<Boolean> REQUIRED = AttachmentKey.create(Boolean.class);
    private static final OperationContext.AttachmentKey<Boolean> VIRTUAL_REQUIRED = OperationContext.AttachmentKey.create(Boolean.class);
    private static final ServiceName DOMAIN_SUFFIX = ServiceName.of("security-domain", "virtual");
    private static final String VIRTUAL_SECURITY_DOMAIN_CAPABILITY = "org.wildfly.security.virtual-security-domain";

    public static void virtualDomainRequired(final DeploymentUnit deploymentUnit) {
        DeploymentUnit rootUnit = toRoot(deploymentUnit);
        rootUnit.putAttachment(REQUIRED, Boolean.TRUE);
    }

    public static boolean isVirtualDomainRequired(final DeploymentUnit deploymentUnit) {
        DeploymentUnit rootUnit = toRoot(deploymentUnit);
        Boolean required = rootUnit.getAttachment(REQUIRED);

        return required == null ? false : required.booleanValue();
    }

    public static void virtualDomainRequired(final OperationContext context) {
        context.attach(VIRTUAL_REQUIRED, Boolean.TRUE);
    }

    public static boolean isVirtualDomainRequired(final OperationContext context) {
        Boolean required = context.getAttachment(VIRTUAL_REQUIRED);
        return required == null ? false : required.booleanValue();
    }

    public static ServiceName virtualDomainName(final DeploymentUnit deploymentUnit) {
        DeploymentUnit rootUnit = toRoot(deploymentUnit);

        return rootUnit.getServiceName().append(DOMAIN_SUFFIX);
    }

    public static ServiceName virtualDomainName(final String domainName) {
        return Services.deploymentUnitName(domainName).append(DOMAIN_SUFFIX);
    }

    public static ServiceName virtualDomainName(final OperationContext operationContext) {
        return ServiceName.of(operationContext.getCurrentAddressValue()).append(DOMAIN_SUFFIX);
    }


    public static ServiceName virtualDomainMetaDataName(final DeploymentPhaseContext context, final DeploymentUnit deploymentUnit) {
        CapabilityServiceSupport capabilityServiceSupport = context.getDeploymentUnit().getAttachment(CAPABILITY_SERVICE_SUPPORT);
        return capabilityServiceSupport.getCapabilityServiceName(VIRTUAL_SECURITY_DOMAIN_CAPABILITY, toRoot(deploymentUnit).getName());
    }

    public static ServiceName virtualDomainMetaDataName(final DeploymentUnit deploymentUnit) {
        CapabilityServiceSupport capabilityServiceSupport = deploymentUnit.getAttachment(CAPABILITY_SERVICE_SUPPORT);
        return capabilityServiceSupport.getCapabilityServiceName(VIRTUAL_SECURITY_DOMAIN_CAPABILITY, toRoot(deploymentUnit).getName());
    }

    public static ServiceName virtualDomainMetaDataName(final DeploymentPhaseContext context, final String virtualDomainName) {
        CapabilityServiceSupport capabilityServiceSupport = context.getDeploymentUnit().getAttachment(CAPABILITY_SERVICE_SUPPORT);
        return capabilityServiceSupport.getCapabilityServiceName(VIRTUAL_SECURITY_DOMAIN_CAPABILITY, virtualDomainName);
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
