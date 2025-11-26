/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.security.manager;

import static org.wildfly.extension.security.manager.Constants.PERMISSION_ACTIONS;
import static org.wildfly.extension.security.manager.Constants.PERMISSION_MODULE;
import static org.wildfly.extension.security.manager.Constants.PERMISSION_NAME;
import static org.wildfly.extension.security.manager.DeploymentPermissionsResourceDefinition.ACTIONS;
import static org.wildfly.extension.security.manager.DeploymentPermissionsResourceDefinition.CLASS;
import static org.wildfly.extension.security.manager.DeploymentPermissionsResourceDefinition.DEFAULT_MAXIMUM_SET;
import static org.wildfly.extension.security.manager.DeploymentPermissionsResourceDefinition.DEPLOYMENT_PERMISSIONS_PATH;
import static org.wildfly.extension.security.manager.DeploymentPermissionsResourceDefinition.MAXIMUM_PERMISSIONS;
import static org.wildfly.extension.security.manager.DeploymentPermissionsResourceDefinition.MINIMUM_PERMISSIONS;
import static org.wildfly.extension.security.manager.DeploymentPermissionsResourceDefinition.MODULE;
import static org.wildfly.extension.security.manager.DeploymentPermissionsResourceDefinition.NAME;

import java.security.Permission;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.Module;
import org.jboss.modules.security.FactoryPermissionCollection;
import org.jboss.modules.security.PermissionFactory;
import org.wildfly.extension.security.manager.deployment.PermissionsParserProcessor;
import org.wildfly.extension.security.manager.deployment.PermissionsValidationProcessor;
import org.wildfly.extension.security.manager.logging.SecurityManagerLogger;

/**
 * Handler that adds the security manager subsystem. It instantiates the permissions specified in the subsystem configuration
 * and installs the DUPs that parse and validate the deployment permissions.
 *
 * @author <a href="sguilhen@jboss.com">Stefan Guilhen</a>
 */
class SecurityManagerSubsystemAdd extends AbstractBoottimeAddStepHandler {

    static final SecurityManagerSubsystemAdd INSTANCE = new SecurityManagerSubsystemAdd();

    private SecurityManagerSubsystemAdd() {
    }

    @Override
    protected void populateModel(final ModelNode operation, final ModelNode model) throws OperationFailedException {
    }

    @Override
    protected void performBoottime(final OperationContext context, final ModelNode operation, final ModelNode model)
            throws OperationFailedException {

        final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
        final ModelNode node = Resource.Tools.readModel(resource);
        // get the minimum set of deployment permissions.
        final ModelNode deploymentPermissionsModel = node.get(DEPLOYMENT_PERMISSIONS_PATH.getKeyValuePair());
        ModelNode minimumPermissionsNode;
        ModelNode maximumPermissionsNode;
        try {
            // WFCORE-7335: upon expression resolution failure during startup with security manager
            // enabled, abort boot sequence
            minimumPermissionsNode = MINIMUM_PERMISSIONS.resolveModelAttribute(context, deploymentPermissionsModel);
            maximumPermissionsNode = MAXIMUM_PERMISSIONS.resolveModelAttribute(context, deploymentPermissionsModel);
        } catch (ExpressionResolver.ExpressionResolutionUserException ex) {
            if (System.getSecurityManager() != null) {
                context.setRollbackOnly();
            }
            throw ex;
        }
        if (!maximumPermissionsNode.isDefined())
            maximumPermissionsNode = DEFAULT_MAXIMUM_SET;
        final List<PermissionFactory> minimumSet = this
                .retrievePermissionSet(DeferredPermissionFactory.Type.MINIMUM_SET, context, minimumPermissionsNode);
        final List<PermissionFactory> maximumSet = this
                .retrievePermissionSet(DeferredPermissionFactory.Type.MAXIMUM_SET, context, maximumPermissionsNode);

        // validate the configured permissions - the minimum set must be implied by the maximum set.
        final FactoryPermissionCollection maxPermissionCollection = new FactoryPermissionCollection(maximumSet.toArray(new PermissionFactory[maximumSet.size()]));
        final StringBuilder failedPermissions = new StringBuilder();
        for (PermissionFactory factory : minimumSet) {
            Permission permission = factory.construct();
            if (permission != null && !maxPermissionCollection.implies(permission)) {
                failedPermissions.append("\n\t\t").append(permission);
            }
        }
        if (failedPermissions.length() > 0) {
            throw SecurityManagerLogger.ROOT_LOGGER.invalidSubsystemConfiguration(failedPermissions);
        }

        // install the DUPs responsible for parsing and validating security permissions found in META-INF/permissions.xml.
        context.addStep(new AbstractDeploymentChainStep() {
            protected void execute(DeploymentProcessorTarget processorTarget) {
                 processorTarget.addDeploymentProcessor(Constants.SUBSYSTEM_NAME, Phase.PARSE, Phase.PARSE_PERMISSIONS,
                         new PermissionsParserProcessor(minimumSet));
                 processorTarget.addDeploymentProcessor(Constants.SUBSYSTEM_NAME, Phase.POST_MODULE, Phase.POST_MODULE_PERMISSIONS_VALIDATION,
                         new PermissionsValidationProcessor(maximumSet));
            }
        }, OperationContext.Stage.RUNTIME);
    }

    /**
     * This method retrieves all security permissions contained within the specified node.
     *
     * @param type the type of the permission set (maximum-set or minimum-set)
     * @param context the {@link OperationContext} used to resolve the permission attributes.
     * @param node the {@link ModelNode} that might contain security permissions metadata.
     * @return a {@link List} containing the retrieved permissions. They are wrapped as {@link PermissionFactory} instances.
     * @throws OperationFailedException if an error occurs while retrieving the security permissions.
     */
    private List<PermissionFactory> retrievePermissionSet(DeferredPermissionFactory.Type type, final OperationContext context, final ModelNode node) throws OperationFailedException {

        final List<PermissionFactory> permissions = new ArrayList<>();

        if (node != null && node.isDefined()) {
            for (ModelNode permissionNode : node.asList()) {
                String permissionClass = CLASS.resolveModelAttribute(context, permissionNode).asString();
                String permissionName = null;
                if (permissionNode.hasDefined(PERMISSION_NAME))
                    permissionName = NAME.resolveModelAttribute(context, permissionNode).asString();
                String permissionActions = null;
                if (permissionNode.hasDefined(PERMISSION_ACTIONS))
                    permissionActions = ACTIONS.resolveModelAttribute(context, permissionNode).asString();
                String moduleName = null;
                if(permissionNode.hasDefined(PERMISSION_MODULE)) {
                    moduleName =  MODULE.resolveModelAttribute(context, permissionNode).asString();
                }

                permissions.add(new DeferredPermissionFactory(type, Module.getBootModuleLoader(), moduleName,
                        permissionClass, permissionName, permissionActions));
            }
        }
        return permissions;
    }
}
