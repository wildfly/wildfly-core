/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.core.management;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNSTABLE_API_ANNOTATIONS;
import static org.jboss.as.server.deployment.Phase.PARSE;
import static org.jboss.as.server.deployment.Phase.PARSE_REPORT_EXPERIMENTAL_ANNOTATIONS;
import static org.jboss.as.server.deployment.Phase.PARSE_SCAN_EXPERIMENTAL_ANNOTATIONS;
import static org.wildfly.extension.core.management.CoreManagementExtension.SUBSYSTEM_NAME;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.extension.core.management.deployment.ReportUnstableApiAnnotationsProcessor;
import org.wildfly.extension.core.management.deployment.ScanUnstableApiAnnotationsProcessor;

/**
 * Resource to configure the unstable api annotation usage reporting.
 *
 */
public class UnstableApiAnnotationResourceDefinition extends PersistentResourceDefinition {

    public static final Stability STABILITY = Stability.PREVIEW;
    public static final SimpleAttributeDefinition LEVEL = SimpleAttributeDefinitionBuilder.create(
            ModelDescriptionConstants.LEVEL, ModelType.STRING, true)
            .setValidator(EnumValidator.create(UnstableApiAnnotationLevel.class))
            .setDefaultValue(new ModelNode(UnstableApiAnnotationLevel.LOG.name()))
            .setRestartAllServices()
            .build();
    public static final PathElement PATH = PathElement.pathElement(SERVICE, UNSTABLE_API_ANNOTATIONS);
    static final ResourceRegistration RESOURCE_REGISTRATION = ResourceRegistration.of(PATH, STABILITY);
    static final UnstableApiAnnotationResourceDefinition INSTANCE = new UnstableApiAnnotationResourceDefinition();

    private static final List<AttributeDefinition> ATTRIBUTES = Collections.singletonList(LEVEL);

    private UnstableApiAnnotationResourceDefinition() {
        super(
                new Parameters(
                            RESOURCE_REGISTRATION,
                            CoreManagementExtension.getResourceDescriptionResolver(UNSTABLE_API_ANNOTATIONS))
                        .setAddHandler(UnstableApiAnnotationResourceAddHandler.INSTANCE)
                        .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE));
    }


    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return ATTRIBUTES;
    }

    private static class UnstableApiAnnotationResourceAddHandler extends AbstractBoottimeAddStepHandler {


        static final UnstableApiAnnotationResourceAddHandler INSTANCE = new UnstableApiAnnotationResourceAddHandler();
        @Override
        public void performBoottime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            ModelNode model = resource.getModel();
            String levelValue = UnstableApiAnnotationResourceDefinition.LEVEL.resolveModelAttribute(context, model).asString();
            UnstableApiAnnotationLevel level = UnstableApiAnnotationLevel.valueOf(levelValue);

            if (context.isNormalServer()) {
                context.addStep(new AbstractDeploymentChainStep() {
                    @Override
                    protected void execute(DeploymentProcessorTarget processorTarget) {
                        processorTarget.addDeploymentProcessor(SUBSYSTEM_NAME, PARSE, PARSE_SCAN_EXPERIMENTAL_ANNOTATIONS,
                                new ScanUnstableApiAnnotationsProcessor(context.getRunningMode(), context.getStability(), level));
                        processorTarget.addDeploymentProcessor(SUBSYSTEM_NAME, PARSE, PARSE_REPORT_EXPERIMENTAL_ANNOTATIONS,
                                new ReportUnstableApiAnnotationsProcessor(level));
                    }
                }, OperationContext.Stage.RUNTIME);
            }
        }
    }


    public enum UnstableApiAnnotationLevel {
        LOG,
        ERROR
    }

}
