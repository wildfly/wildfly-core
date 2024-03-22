/*
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2024 Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.wildfly.core.test.unstable.api.annotation.classes.subsystem;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.test.unstable.api.annotation.classes._private.UnstableAnnotationApiTestLogger;

import java.util.Collection;
import java.util.Collections;

import static org.jboss.as.controller.OperationContext.Stage.RUNTIME;
import static org.jboss.as.server.deployment.Phase.DEPENDENCIES;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class UnstableApiAnnotationTestSubsystemDefinition extends PersistentResourceDefinition {

    private static final String UNSTABLE_API_ANNOTATION_SUBSYSTEM_CAPABILITY_NAME = "org.wildfly.core.test.unstable-api-annotation";

    private static final RuntimeCapability<Void> CONTEXT_PROPAGATION_CAPABILITY = RuntimeCapability.Builder
            .of(UNSTABLE_API_ANNOTATION_SUBSYSTEM_CAPABILITY_NAME)
            .build();

    public UnstableApiAnnotationTestSubsystemDefinition() {
        super(
                new Parameters(
                        UnstableApiAnnotationTestExtension.SUBSYSTEM_PATH,
                        UnstableApiAnnotationTestExtension.getResourceDescriptionResolver(UnstableApiAnnotationTestExtension.SUBSYSTEM_NAME))
                .setAddHandler(AddHandler.INSTANCE)
                .setRemoveHandler(new ModelOnlyRemoveStepHandler())
                .setCapabilities(CONTEXT_PROPAGATION_CAPABILITY)
        );
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Collections.emptyList();
    }

    @Override
    public void registerAdditionalRuntimePackages(ManagementResourceRegistration resourceRegistration) {
        super.registerAdditionalRuntimePackages(resourceRegistration);
    }

    static class AddHandler extends AbstractBoottimeAddStepHandler {

        static AddHandler INSTANCE = new AddHandler();

        private AddHandler() {
            super();
        }

        @Override
        protected void performBoottime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            super.performBoottime(context, operation, model);

            context.addStep(new AbstractDeploymentChainStep() {
                public void execute(DeploymentProcessorTarget processorTarget) {
                    final int DEPENDENCIES_TEMPLATE = 6304;
                    processorTarget.addDeploymentProcessor(UnstableApiAnnotationTestExtension.SUBSYSTEM_NAME, DEPENDENCIES, DEPENDENCIES_TEMPLATE, new UnstableApiAnnotationTestDependencyProcessor());
                }
            }, RUNTIME);

            UnstableAnnotationApiTestLogger.LOGGER.activatingSubsystem();
        }
    }
}
