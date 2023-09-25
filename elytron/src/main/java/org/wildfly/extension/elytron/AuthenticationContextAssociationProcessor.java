/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.jboss.as.server.deployment.Attachments.MODULE;
import static org.wildfly.extension.elytron.ElytronExtension.AUTHENTICATION_CONTEXT_KEY;
import static org.wildfly.extension.elytron.SecurityActions.doPrivileged;

import java.security.PrivilegedAction;
import java.util.function.Supplier;

import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.modules.ModuleClassLoader;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.ElytronXmlParser;
import org.wildfly.security.auth.client.InvalidAuthenticationConfigurationException;

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
        ModuleClassLoader classLoader = context.getDeploymentUnit().getAttachment(MODULE).getClassLoader();
        if (authenticationContext != null) {
            AuthenticationContext.getContextManager().setClassLoaderDefault(
                    classLoader, authenticationContext);
        } else {
            AuthenticationContext.getContextManager().setClassLoaderDefaultSupplier(
                    classLoader, new Supplier<AuthenticationContext>() {

                        private volatile AuthenticationContext context;

                        @Override
                        public AuthenticationContext get() {
                            if (context != null) {
                                return context;
                            }
                            synchronized (this) {
                                if (context != null) {
                                    return context;
                                }
                                return context = doPrivileged((PrivilegedAction<AuthenticationContext>) () -> {
                                    ClassLoader old = Thread.currentThread().getContextClassLoader();
                                    try {
                                        Thread.currentThread().setContextClassLoader(classLoader);
                                        return ElytronXmlParser.parseAuthenticationClientConfiguration().create();
                                    } catch (Throwable t) {
                                        throw new InvalidAuthenticationConfigurationException(t);
                                    } finally {
                                        Thread.currentThread().setContextClassLoader(old);
                                    }
                                });
                            }
                        }
                    });

        }
    }

    @Override
    public void undeploy(DeploymentUnit unit) {
        AuthenticationContext.getContextManager().setClassLoaderDefault(unit.getAttachment(MODULE).getClassLoader(), null);

    }

}
