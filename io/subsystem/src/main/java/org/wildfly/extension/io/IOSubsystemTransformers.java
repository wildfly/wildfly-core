/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.io;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.transform.ExtensionTransformerRegistration;
import org.jboss.as.controller.transform.SubsystemTransformerRegistration;
import org.jboss.as.controller.transform.description.AttributeConverter;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;

/**
 * @author Tomaz Cerar (c) 2017 Red Hat Inc.
 */
public class IOSubsystemTransformers implements ExtensionTransformerRegistration {
    static final ModelVersion VERSION_2_0 = ModelVersion.create(2, 0);


    @Override
    public String getSubsystemName() {
        return IOExtension.SUBSYSTEM_NAME;
    }

    @Override
    public void registerTransformers(SubsystemTransformerRegistration registration) {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedSubystemInstance(registration.getCurrentSubsystemVersion());

        // Current 3.0.0 to 2.0.0, aka EAP 7.0.0
        buildTransformers_2_0(chainedBuilder.createBuilder(registration.getCurrentSubsystemVersion(), VERSION_2_0));

        chainedBuilder.buildAndRegister(registration, new ModelVersion[]{VERSION_2_0});
    }

    private void buildTransformers_2_0(ResourceTransformationDescriptionBuilder builder) {
        builder.addChildResource(WorkerResourceDefinition.INSTANCE.getPathElement()).getAttributeBuilder()
                .setValueConverter(
                        new AttributeConverter.DefaultValueAttributeConverter(WorkerResourceDefinition.WORKER_TASK_KEEPALIVE),
                        WorkerResourceDefinition.WORKER_TASK_KEEPALIVE
                )
                .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS,
                        WorkerResourceDefinition.STACK_SIZE,
                        WorkerResourceDefinition.WORKER_IO_THREADS,
                        WorkerResourceDefinition.WORKER_TASK_KEEPALIVE,
                        WorkerResourceDefinition.WORKER_TASK_MAX_THREADS
                )
        ;


    }
}
