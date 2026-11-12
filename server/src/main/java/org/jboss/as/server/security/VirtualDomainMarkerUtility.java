/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
    private static final AttachmentKey<Boolean> REQUIRED_BY_UNIT = AttachmentKey.create(Boolean.class);
    private static final OperationContext.AttachmentKey<Boolean> VIRTUAL_REQUIRED = OperationContext.AttachmentKey.create(Boolean.class);
    private static final ServiceName DOMAIN_SUFFIX = ServiceName.of("security-domain", "virtual");
    private static final String VIRTUAL_SECURITY_DOMAIN_CAPABILITY = "org.wildfly.security.virtual-security-domain";

    public static void virtualDomainRequired(final DeploymentUnit deploymentUnit) {
        DeploymentUnit rootUnit = toRoot(deploymentUnit);
        rootUnit.putAttachment(REQUIRED, Boolean.TRUE);
        deploymentUnit.putAttachment(REQUIRED_BY_UNIT, Boolean.TRUE);
    }

    public static boolean isVirtualDomainRequired(final DeploymentUnit deploymentUnit) {
        DeploymentUnit rootUnit = toRoot(deploymentUnit);
        Boolean required = rootUnit.getAttachment(REQUIRED);

        return required == null ? false : required.booleanValue();
    }

    /**
     * Check if this specific deployment unit was itself marked as requiring a virtual security domain,
     * as opposed to any deployment unit of the same top-level deployment being marked.
     * The flag is written and read by the unit's own deployment phase thread, so unlike the root-level flag checked by
     * {@link #isVirtualDomainRequired(DeploymentUnit)}, it can be consulted in the same phase in which the unit is marked;
     * the root-level flag is only reliable in phases after the one in which sub-deployments are marked,
     * since sub-deployments process a given phase concurrently.
     *
     * @param deploymentUnit the deployment unit to check
     * @return {@code true} if {@link #virtualDomainRequired(DeploymentUnit)} was called for this deployment unit
     */
    public static boolean isVirtualDomainRequiredByUnit(final DeploymentUnit deploymentUnit) {
        Boolean required = deploymentUnit.getAttachment(REQUIRED_BY_UNIT);
        return required != null && required;
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
