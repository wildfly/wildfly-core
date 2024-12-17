/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LIST_MODULES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBDEPLOYMENT;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.VERBOSE;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.ENABLED;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.RUNTIME_NAME;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.moduleservice.ModuleLoadService;
import org.jboss.as.server.moduleservice.ServiceModuleLoader;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceRegistry;

/**
 * Handles listing module dependencies of a deployment and sub-deployment.
 *
 * @author Yeray Borges
 */
public class DeploymentListModulesHandler implements OperationStepHandler {
    public static final String OPERATION_NAME = LIST_MODULES;

    public DeploymentListModulesHandler() {
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final PathAddress currentAddress = context.getCurrentAddress();
        final boolean subDeploymentFlag = currentAddress.getLastElement().getKey().equals(SUBDEPLOYMENT);
        final PathAddress address = subDeploymentFlag ? currentAddress.getParent() : currentAddress;

        final ModelNode model = context.readResourceFromRoot(address, false).getModel();
        final boolean verbose = VERBOSE.resolveModelAttribute(context, operation).asBoolean();
        final boolean enabled = ENABLED.resolveModelAttribute(context, model).asBoolean();
        final String runtimeName = RUNTIME_NAME.resolveModelAttribute(context, model).asString();
        final String item = context.getCurrentAddressValue();

        if (enabled && context.isNormalServer()) {
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) {
                    final ServiceRegistry sr = context.getServiceRegistry(false);
                    final ServiceController<?> deploymentUnitSc = sr.getService(Services.deploymentUnitName(runtimeName));
                    final DeploymentUnit deploymentUnit = (DeploymentUnit) deploymentUnitSc.getValue();

                    ModuleIdentifier moduleIdentifier = null;
                    if (subDeploymentFlag) {
                        for (DeploymentUnit subDeployment : deploymentUnit.getAttachmentList(Attachments.SUB_DEPLOYMENTS)) {
                            if (subDeployment.getName().equals(item)) {
                                moduleIdentifier = subDeployment.getAttachment(Attachments.MODULE_IDENTIFIER);
                                break;
                            }
                        }
                        if (moduleIdentifier == null) {
                            throw ControllerLogger.ROOT_LOGGER.managementResourceNotFound(currentAddress);
                        }
                    } else {
                        moduleIdentifier = deploymentUnit.getAttachment(Attachments.MODULE_IDENTIFIER);
                    }

                    final ServiceController<?> moduleLoadServiceController = sr.getService(ServiceModuleLoader.moduleServiceName(moduleIdentifier.toString()));
                    final ModuleLoadService moduleLoadService = (ModuleLoadService) moduleLoadServiceController.getService();

                    final ModelNode result = new ModelNode();
                    List<ModuleDependency> dependencies = moduleLoadService.getSystemDependencies();
                    Collections.sort(dependencies, Comparator.comparing(p -> p.getIdentifier().toString()));
                    result.get("system-dependencies").set(buildDependenciesInfo(dependencies, verbose));

                    dependencies = moduleLoadService.getLocalDependencies();
                    Collections.sort(dependencies, Comparator.comparing(p -> p.getIdentifier().toString()));
                    result.get("local-dependencies").set(buildDependenciesInfo(dependencies, verbose));

                    dependencies = moduleLoadService.getUserDependencies();
                    Collections.sort(dependencies, Comparator.comparing(p -> p.getIdentifier().toString()));
                    result.get("user-dependencies").set(buildDependenciesInfo(dependencies, verbose));

                    context.getResult().set(result);
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

    private ModelNode buildDependenciesInfo(List<ModuleDependency> dependencies, boolean verbose) {
        ModelNode deps = new ModelNode().setEmptyList();
        for (ModuleDependency dependency : dependencies) {
            ModelNode depData = new ModelNode();
            ModuleIdentifier identifier = dependency.getIdentifier();
            depData.get("name").set(identifier.toString());
            if (verbose) {
                depData.get("optional").set(dependency.isOptional());
                depData.get("export").set(dependency.isExport());
                depData.get("import-services").set(dependency.isImportServices());
            }
            deps.add(depData);
        }
        return deps;
    }
}
