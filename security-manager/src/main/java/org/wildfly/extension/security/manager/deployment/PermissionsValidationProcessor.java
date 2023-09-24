/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.security.manager.deployment;

import java.security.Permission;
import java.util.List;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.modules.security.FactoryPermissionCollection;
import org.jboss.modules.security.ImmediatePermissionFactory;
import org.jboss.modules.security.PermissionFactory;
import org.wildfly.extension.security.manager.logging.SecurityManagerLogger;

/**
 * This class implements a {@link DeploymentUnitProcessor} that validates the security permissions that have been granted
 * to the deployments. The permissions granted via subsystem ({@code minimum-set}) combined with those granted via deployment
 * descriptors ({@code permissions.xml} and {@code jboss-permissions.xml}) must be implied by the {@code maximum-set}.
 * <p/>
 * Permissions that are internally granted by the container are ignored as those are always granted irrespective of the
 * {@code maximum-set} configuration.
 * <p/>
 * This processor must be installed into {@link org.jboss.as.server.deployment.Phase#POST_MODULE} because it needs the
 * deployment module's {@link ClassLoader} to load the permissions from the descriptors and that is only available after
 * the module has been created.
 *
 * @author <a href="mailto:sguilhen@redhat.com">Stefan Guilhen</a>
 */
public class PermissionsValidationProcessor implements DeploymentUnitProcessor {

    // maximum set of permissions deployments should have.
    private FactoryPermissionCollection maxPermissions;

    /**
     * Creates an instance of this {@link DeploymentUnitProcessor}.
     *
     * @param maxPermissions a {@link List} containing the maximum set of configurable permissions a deployment can have.
     *                       In other words, all permissions in the minimum set plus the permissions parsed in
     *                       META-INF/permissions.xml (or jboss-permissions.xml) must be implied by the maximum set.
     */
    public PermissionsValidationProcessor(final List<PermissionFactory> maxPermissions) {
        this.maxPermissions = new FactoryPermissionCollection(maxPermissions.toArray(new PermissionFactory[maxPermissions.size()]));
    }

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final List<PermissionFactory> permissionFactories = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION).getPermissionFactories();
        final StringBuilder failedPermissions = new StringBuilder();
        for (PermissionFactory factory : permissionFactories) {
            // all permissions granted internally by the container are of type ImmediatePermissionFactory - they should
            // not be considered when validating the permissions granted to deployments via subsystem or deployment
            // descriptors.
            if (!(factory instanceof ImmediatePermissionFactory)) {
                Permission permission = factory.construct();
                if (permission != null && !this.maxPermissions.implies(permission)) {
                    failedPermissions.append("\n\t\t").append(permission);

                }
            }
        }
        if (failedPermissions.length() > 0) {
            throw SecurityManagerLogger.ROOT_LOGGER.invalidDeploymentConfiguration(failedPermissions);
        }
    }

}

