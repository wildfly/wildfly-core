/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment.service;

import java.util.ArrayList;
import java.util.List;
import org.jboss.as.server.deployment.AttachmentList;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.ServicesAttachment;
import org.jboss.modules.Module;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceActivatorContextImpl;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceRegistryException;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Deployment processor responsible for executing any ServiceActivator instances for a deployment.
 *
 * @author John Bailey
 */
public class ServiceActivatorProcessor implements DeploymentUnitProcessor {

    /**
     * If the deployment has a module attached it will ask the module to load the ServiceActivator services.
     *
     * @param phaseContext the deployment unit context
     */
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ServicesAttachment servicesAttachment = deploymentUnit.getAttachment(Attachments.SERVICES);
        if (servicesAttachment == null || servicesAttachment.getServiceImplementations(ServiceActivator.class.getName()).isEmpty()) {
            return; // Skip it if it has not been marked
        }
        final Module module = deploymentUnit.getAttachment(Attachments.MODULE);
        if (module == null) {
            return; // Skip deployments with no module
        }

        AttachmentList<DeploymentUnit> duList = deploymentUnit.getAttachment(Attachments.SUB_DEPLOYMENTS);
        List<String> serviceAcitvatorList = new ArrayList<String>();
        if (duList!=null && !duList.isEmpty()) {
            for (DeploymentUnit du : duList) {
                ServicesAttachment duServicesAttachment = du.getAttachment(Attachments.SERVICES);
                for (String serv : duServicesAttachment.getServiceImplementations(ServiceActivator.class.getName())) {
                    serviceAcitvatorList.add(serv);
                }
            }
        }

        ServiceRegistry serviceRegistry = phaseContext.getServiceRegistry();
        if (WildFlySecurityManager.isChecking()) {
            //service registry allows you to modify internal server state across all deployments
            //if a security manager is present we use a version that has permission checks
            serviceRegistry = new SecuredServiceRegistry(serviceRegistry);
        }
        final ServiceActivatorContext serviceActivatorContext = new ServiceActivatorContextImpl(phaseContext.getServiceTarget(), serviceRegistry);

        final ClassLoader current = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();

        try {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(module.getClassLoader());
            for (ServiceActivator serviceActivator : module.loadService(ServiceActivator.class)) {
                try {
                    for (String serv : servicesAttachment.getServiceImplementations(ServiceActivator.class.getName())) {
                        if (serv.compareTo(serviceActivator.getClass().getName()) == 0 && !serviceAcitvatorList.contains(serv)) {
                            serviceActivator.activate(serviceActivatorContext);
                            break;
                        }
                    }
                } catch (ServiceRegistryException e) {
                    throw new DeploymentUnitProcessingException(e);
                }
            }
        } finally {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(current);
        }
    }

}
