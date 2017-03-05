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

import static org.jboss.as.server.deployment.Attachments.MODULE;
import static org.wildfly.extension.elytron.ElytronExtension.AUTHENTICATION_CONTEXT_KEY;

import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.wildfly.security.auth.client.AuthenticationContext;

/**
 * A {@link DeploymentUnitProcessor} to associate a previously obtained {@link AuthenticationContext} with the
 * {@link ClassLoader} of the deployment and clear it at undeployment.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class AuthenticationContextAssociationProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext context) throws DeploymentUnitProcessingException {
        AuthenticationContext authenticationContext = context.getAttachment(AUTHENTICATION_CONTEXT_KEY);
        if (authenticationContext != null) {
            AuthenticationContext.getContextManager().setClassLoaderDefault(
                    context.getDeploymentUnit().getAttachment(MODULE).getClassLoader(), authenticationContext);
        }
    }

    @Override
    public void undeploy(DeploymentUnit unit) {
        AuthenticationContext.getContextManager().setClassLoaderDefault(unit.getAttachment(MODULE).getClassLoader(), null);

    }

}
