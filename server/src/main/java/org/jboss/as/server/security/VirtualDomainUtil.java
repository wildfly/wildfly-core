/*
 * Copyright 2023 Red Hat, Inc.
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

import static org.jboss.as.server.security.SecurityMetaData.ATTACHMENT_KEY;
import static org.jboss.as.server.security.VirtualDomainMarkerUtility.virtualDomainMetaDataName;

import java.util.function.UnaryOperator;

import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * Utility class with methods for configuring virtual security domains.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
public class VirtualDomainUtil {

    public static final ServiceName VIRTUAL_SECURITY_DOMAIN_CREATION_SERVICE = ServiceName.of("org.wildfly.security.virtual-security-domain-creation");
    public static final ServiceName OIDC_VIRTUAL_SECURITY_DOMAIN_CREATION_SERVICE = ServiceName.of("org.wildfly.security.oidc-virtual-security-domain-creation");

    public static VirtualDomainMetaData configureVirtualDomain(DeploymentPhaseContext phaseContext, DeploymentUnit deploymentUnit,
                                                               SecurityDomain.Builder virtualDomainBuilder) throws DeploymentUnitProcessingException {
        VirtualDomainMetaData virtualDomainMetaData = getVirtualDomainMetaData(phaseContext, deploymentUnit);
        UnaryOperator<SecurityIdentity> securityIdentityTransformer;
        if (virtualDomainMetaData != null) {
            securityIdentityTransformer = virtualDomainMetaData.getSecurityIdentityTransformer();
            if (securityIdentityTransformer != null) {
                virtualDomainBuilder.setSecurityIdentityTransformer(securityIdentityTransformer);
            }
        }
        return virtualDomainMetaData;
    }

    public static void configureVirtualDomain(VirtualDomainMetaData virtualDomainMetaData,
                                              SecurityDomain.Builder virtualDomainBuilder) {
        if (virtualDomainMetaData != null) {
            UnaryOperator<SecurityIdentity> securityIdentityTransformer = virtualDomainMetaData.getSecurityIdentityTransformer();
            if (securityIdentityTransformer != null) {
                virtualDomainBuilder.setSecurityIdentityTransformer(securityIdentityTransformer);
            }
        }
    }

    public static void setTopLevelDeploymentSecurityMetaData(DeploymentUnit deploymentUnit, ServiceName virtualDomainName) {
        DeploymentUnit topLevelDeployment = toRoot(deploymentUnit);
        SecurityMetaData topLevelSecurityMetaData = topLevelDeployment.getAttachment(ATTACHMENT_KEY);
        topLevelSecurityMetaData.setSecurityDomain(virtualDomainName);
    }

    private static <T> ServiceController<T> getService(ServiceRegistry serviceRegistry, ServiceName serviceName, Class<T> serviceType) {
        ServiceController<?> controller = serviceRegistry.getService(serviceName);
        return (ServiceController<T>) controller;
    }

    public static VirtualDomainMetaData getVirtualDomainMetaData(DeploymentUnit deploymentUnit) throws DeploymentUnitProcessingException {
        ServiceName virtualDomainMetaDataName = VirtualDomainMarkerUtility.virtualDomainMetaDataName(deploymentUnit);
        ServiceController<VirtualDomainMetaData> serviceContainer = getService(deploymentUnit.getServiceRegistry(), virtualDomainMetaDataName, VirtualDomainMetaData.class);
        if (serviceContainer != null) {
            ServiceController.State serviceState = serviceContainer.getState();
            if (serviceState != ServiceController.State.UP) {
                throw ServerLogger.ROOT_LOGGER.requiredServiceNotUp(virtualDomainMetaDataName, serviceState);
            }
            return serviceContainer.getService().getValue();
        }
        return null;
    }

    public static boolean isVirtualDomainCreated(DeploymentUnit deploymentUnit) throws DeploymentUnitProcessingException {
        ServiceName virtualDomainName = VirtualDomainMarkerUtility.virtualDomainName(deploymentUnit);
        ServiceController<SecurityDomain> serviceController = getService(deploymentUnit.getServiceRegistry(), virtualDomainName, SecurityDomain.class);
        if (serviceController != null) {
            ServiceController.State serviceState = serviceController.getState();
            return serviceState == ServiceController.State.UP;
        }
        return false;
    }

    public static void createVirtualDomain(final ServiceRegistry serviceRegistry, final VirtualDomainMetaData virtualDomainMetaData,
                                           final ServiceName virtualDomainServiceName, final ServiceTarget serviceTarget) {
        final ServiceName serviceName = getCreationServiceName(virtualDomainMetaData);
        if (serviceName != null) {
            final ServiceController<?> serviceController = serviceRegistry.getService(serviceName);
            if (serviceController != null) {
                ServiceController.State serviceState = serviceController.getState();
                if (serviceState == ServiceController.State.UP) {
                    VirtualSecurityDomainCreationService virtualSecurityDomainCreationService = (VirtualSecurityDomainCreationService) serviceController.getService();
                    virtualSecurityDomainCreationService.createVirtualSecurityDomain(virtualDomainMetaData, virtualDomainServiceName, serviceTarget);
                }
            }
        }
    }

    public static void clearVirtualDomainMetaDataSecurityDomain(final DeploymentUnit deploymentUnit) {
        ServiceName virtualDomainMetaDataServiceName = virtualDomainMetaDataName(deploymentUnit);
        ServiceRegistry registry = deploymentUnit.getServiceRegistry();
        if (registry != null){
            ServiceController<?> serviceController = registry.getService(virtualDomainMetaDataServiceName);
            if (serviceController != null) {
                ServiceController.State serviceState = serviceController.getState();
                if (serviceState == ServiceController.State.UP) {
                    VirtualDomainMetaData virtualDomainMetaData = (VirtualDomainMetaData) serviceController.getService().getValue();
                    if (virtualDomainMetaData != null) {
                        // clear virtual domain
                        virtualDomainMetaData.setSecurityDomain(null);
                    }
                }
            }
        }
    }

    private static ServiceName getCreationServiceName(final VirtualDomainMetaData virtualDomainMetaData) {
        if (virtualDomainMetaData == null) {
            return null;
        }
        return (virtualDomainMetaData.getAuthMethod() == VirtualDomainMetaData.AuthMethod.OIDC) ?
                OIDC_VIRTUAL_SECURITY_DOMAIN_CREATION_SERVICE : VIRTUAL_SECURITY_DOMAIN_CREATION_SERVICE;
    }

    private static VirtualDomainMetaData getVirtualDomainMetaData(DeploymentPhaseContext phaseContext, DeploymentUnit deploymentUnit) throws DeploymentUnitProcessingException {
        ServiceName virtualDomainMetaDataName = VirtualDomainMarkerUtility.virtualDomainMetaDataName(phaseContext, deploymentUnit);
        ServiceController<VirtualDomainMetaData> serviceContainer = getService(phaseContext.getServiceRegistry(), virtualDomainMetaDataName, VirtualDomainMetaData.class);
        if (serviceContainer != null) {
            ServiceController.State serviceState = serviceContainer.getState();
            if (serviceState != ServiceController.State.UP) {
                throw ServerLogger.ROOT_LOGGER.requiredServiceNotUp(virtualDomainMetaDataName, serviceState);
            }
            return serviceContainer.getService().getValue();
        }
        return null;
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
