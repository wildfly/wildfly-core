/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.elytron;

import static org.jboss.as.server.deployment.Attachments.CAPABILITY_SERVICE_SUPPORT;
import static org.wildfly.extension.elytron.Capabilities.AUTHENTICATION_CONTEXT_CAPABILITY;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
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

    @Override
    public void undeploy(DeploymentUnit unit) {
        // This phase just sets the dependency so nothing to undeploy.
    }

    public void setDefaultAuthenticationContext(String defaultAuthenticationContext) {
        this.defaultAuthenticationContext = defaultAuthenticationContext;
    }

}
