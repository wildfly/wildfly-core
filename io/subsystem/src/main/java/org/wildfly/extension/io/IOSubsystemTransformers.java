/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import static org.wildfly.extension.io.IOExtension.CURRENT_MODEL_VERSION;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.transform.ExtensionTransformerRegistration;
import org.jboss.as.controller.transform.SubsystemTransformerRegistration;
import org.jboss.as.controller.transform.description.AttributeConverter;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;

/**
 * @author Tomaz Cerar (c) 2017 Red Hat Inc.
 */
public class IOSubsystemTransformers implements ExtensionTransformerRegistration {
    static final ModelVersion VERSION_2_0 = ModelVersion.create(2, 0);
    static final ModelVersion VERSION_3_0 = ModelVersion.create(3, 0);


    @Override
    public String getSubsystemName() {
        return IOExtension.SUBSYSTEM_NAME;
    }

    @Override
    public void registerTransformers(SubsystemTransformerRegistration registration) {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedSubystemInstance(registration.getCurrentSubsystemVersion());

        buildTransformers_3_0(chainedBuilder.createBuilder(CURRENT_MODEL_VERSION, VERSION_3_0));
        buildTransformers_2_0(chainedBuilder.createBuilder(VERSION_3_0, VERSION_2_0));

        chainedBuilder.buildAndRegister(registration, new ModelVersion[]{ VERSION_3_0, VERSION_2_0 });
    }

    private void buildTransformers_3_0(ResourceTransformationDescriptionBuilder builder) {
        final ResourceTransformationDescriptionBuilder worker = builder.addChildResource(WorkerResourceDefinition.INSTANCE.getPathElement());
        worker.getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.DEFINED, WorkerResourceDefinition.WORKER_TASK_CORE_THREADS)
                .setDiscard(DiscardAttributeChecker.DEFAULT_VALUE, WorkerResourceDefinition.WORKER_TASK_CORE_THREADS);
    }

    private void buildTransformers_2_0(ResourceTransformationDescriptionBuilder builder) {
        final ResourceTransformationDescriptionBuilder worker = builder.addChildResource(WorkerResourceDefinition.INSTANCE.getPathElement());
        worker.rejectChildResource(PathElement.pathElement(OutboundBindAddressResourceDefinition.RESOURCE_NAME));
        worker.getAttributeBuilder()
                .setValueConverter(AttributeConverter.DEFAULT_VALUE, WorkerResourceDefinition.WORKER_TASK_KEEPALIVE)
                .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS,
                        WorkerResourceDefinition.STACK_SIZE,
                        WorkerResourceDefinition.WORKER_IO_THREADS,
                        WorkerResourceDefinition.WORKER_TASK_KEEPALIVE,
                        WorkerResourceDefinition.WORKER_TASK_MAX_THREADS
                );



    }
}
