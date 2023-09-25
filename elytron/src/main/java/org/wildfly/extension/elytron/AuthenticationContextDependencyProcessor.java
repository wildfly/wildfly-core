/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.jboss.as.server.deployment.Attachments.CAPABILITY_SERVICE_SUPPORT;
import static org.wildfly.extension.elytron.Capabilities.AUTHENTICATION_CONTEXT_CAPABILITY;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.msc.service.ServiceName;
import org.wildfly.security.auth.client.AuthenticationContext;

/**
 * A {@link DeploymentUnitProcessor} responsible for setting a dependency on the {@link AuthenticationContext} to be used for
 * the deployment.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class AuthenticationContextDependencyProcessor implements DeploymentUnitProcessor {

    private volatile String defaultAuthenticationContext;

    @Override
    public void deploy(DeploymentPhaseContext context) throws DeploymentUnitProcessingException {
        String defaultAuthenticationContext = this.defaultAuthenticationContext;
        if (defaultAuthenticationContext != null) {
            CapabilityServiceSupport capabilityServiceSupport = context.getDeploymentUnit().getAttachment(CAPABILITY_SERVICE_SUPPORT);
            ServiceName serviceName = capabilityServiceSupport.getCapabilityServiceName(AUTHENTICATION_CONTEXT_CAPABILITY, defaultAuthenticationContext);
            context.addDependency(serviceName, ElytronExtension.AUTHENTICATION_CONTEXT_KEY);
        }
    }

    public void setDefaultAuthenticationContext(String defaultAuthenticationContext) {
        this.defaultAuthenticationContext = defaultAuthenticationContext;
    }

}
