/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.core.management;


import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.wildfly.extension.core.management.UnstableApiAnnotationResourceDefinition.UnstableApiAnnotationLevel;
import org.wildfly.extension.core.management.deployment.ReportUnstableApiAnnotationsProcessor;
import org.wildfly.extension.core.management.deployment.ScanUnstableApiAnnotationsProcessor;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.jboss.as.server.deployment.Phase.PARSE;
import static org.jboss.as.server.deployment.Phase.PARSE_REPORT_EXPERIMENTAL_ANNOTATIONS;
import static org.jboss.as.server.deployment.Phase.PARSE_SCAN_EXPERIMENTAL_ANNOTATIONS;
import static org.wildfly.extension.core.management.CoreManagementExtension.SUBSYSTEM_NAME;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for the core-management subsystem root resource.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat inc.
 */
class CoreManagementRootResourceDefinition extends PersistentResourceDefinition {

    CoreManagementRootResourceDefinition() {
        super(CoreManagementExtension.SUBSYSTEM_PATH,
                CoreManagementExtension.getResourceDescriptionResolver(),
                new CoreManagementAddHandler(),
                ReloadRequiredRemoveStepHandler.INSTANCE);
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Collections.emptyList();
    }

    @Override
    protected List<? extends PersistentResourceDefinition> getChildren() {
        return Arrays.asList(ConfigurationChangeResourceDefinition.INSTANCE,
                new ProcessStateListenerResourceDefinition(),
                UnstableApiAnnotationResourceDefinition.INSTANCE
        );
    }

    private static class CoreManagementAddHandler extends AbstractBoottimeAddStepHandler {

        @Override
        protected void performBoottime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {

            if (context.isNormalServer()) {
                context.addStep(new AbstractDeploymentChainStep() {
                    @Override
                    protected void execute(DeploymentProcessorTarget processorTarget) {
                        processorTarget.addDeploymentProcessor(SUBSYSTEM_NAME, PARSE, PARSE_SCAN_EXPERIMENTAL_ANNOTATIONS,
                                new ScanUnstableApiAnnotationsProcessor(context.getRunningMode(), context.getStability(), UnstableApiAnnotationService.LEVEL_SUPPLIER));
                        processorTarget.addDeploymentProcessor(SUBSYSTEM_NAME, PARSE, PARSE_REPORT_EXPERIMENTAL_ANNOTATIONS,
                                new ReportUnstableApiAnnotationsProcessor(UnstableApiAnnotationService.LEVEL_SUPPLIER));
                    }
                }, OperationContext.Stage.RUNTIME);
            }

            Resource unstableApiResource = resource.getChild(UnstableApiAnnotationResourceDefinition.PATH);
            UnstableApiAnnotationLevel level = null;
            if (unstableApiResource != null) {
                ModelNode model = unstableApiResource.getModel();
                String levelValue = UnstableApiAnnotationResourceDefinition.LEVEL.resolveModelAttribute(context, model).asString();
                level = UnstableApiAnnotationLevel.valueOf(levelValue);
            }

            ServiceBuilder<?> sb = context.getCapabilityServiceTarget().addService();
            Consumer<UnstableApiAnnotationService> serviceConsumer = sb.provides(UnstableApiAnnotationService.SERVICE_NAME);
            sb.setInstance(new UnstableApiAnnotationService(serviceConsumer, level));
            sb.install();
        }
    }
}
