/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.SubsystemResourceRegistration;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ParentResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.SubsystemResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.wildfly.common.function.Functions;
import org.wildfly.io.IOServiceDescriptor;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.SubsystemResourceDefinitionRegistrar;
import org.wildfly.subsystem.resource.capability.CapabilityReference;
import org.wildfly.subsystem.resource.capability.CapabilityReferenceAttributeDefinition;
import org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler;
import org.wildfly.subsystem.service.ResourceServiceConfigurator;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;
import org.xnio.XnioWorker;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
class IOSubsystemResourceDefinitionRegistrar implements SubsystemResourceDefinitionRegistrar, ResourceServiceConfigurator {

    static final SubsystemResourceRegistration REGISTRATION = SubsystemResourceRegistration.of("io");
    static final ParentResourceDescriptionResolver RESOLVER = new SubsystemResourceDescriptionResolver(REGISTRATION.getName(), IOSubsystemResourceDefinitionRegistrar.class);

    static final RuntimeCapability<Void> DEFAULT_WORKER_CAPABILITY = RuntimeCapability.Builder.of(IOServiceDescriptor.DEFAULT_WORKER).build();

    static final CapabilityReferenceAttributeDefinition<XnioWorker> DEFAULT_WORKER = new CapabilityReferenceAttributeDefinition.Builder<>("default-worker", CapabilityReference.builder(DEFAULT_WORKER_CAPABILITY, IOServiceDescriptor.NAMED_WORKER).build())
            .setRequired(false)
            .build();

    static final RuntimeCapability<Void> MAX_THREADS_CAPABILITY = RuntimeCapability.Builder.of(IOServiceDescriptor.MAX_THREADS).build();

    static final ModelNode LEGACY_DEFAULT_WORKER = new ModelNode("default");

    // Tracks max-threads for all workers
    private final AtomicInteger maxThreads = new AtomicInteger();

    @Override
    public ManagementResourceRegistration register(SubsystemRegistration parent, ManagementResourceRegistrationContext context) {
        ManagementResourceRegistration registration = parent.registerSubsystemModel(ResourceDefinition.builder(REGISTRATION, RESOLVER).build());

        ResourceDescriptor descriptor = ResourceDescriptor.builder(RESOLVER)
                .addAttributes(List.of(DEFAULT_WORKER))
                .addCapabilities(List.of(DEFAULT_WORKER_CAPABILITY, MAX_THREADS_CAPABILITY))
                .withRuntimeHandler(ResourceOperationRuntimeHandler.configureParentService(this))
                .build();
        ManagementResourceRegistrar.of(descriptor).register(registration);

        registration.registerSubModel(new WorkerResourceDefinition(this.maxThreads));
        registration.registerSubModel(new BufferPoolResourceDefinition());

        return registration;
    }

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        ModelNode workers = model.get(WorkerResourceDefinition.PATH.getKey());
        WorkerAdd.checkWorkerConfiguration(context, workers);

        CapabilityServiceInstaller.Builder<AtomicInteger, Integer> maxThreadsBuilder =
                CapabilityServiceInstaller.builder(MAX_THREADS_CAPABILITY, AtomicInteger::intValue, Functions.constantSupplier(this.maxThreads));
        if (workers.isDefined()) {
            // Give each known worker a chance to record their threads before starting
            // the service that reports the current total maxThreads.
            for (String workerName : workers.keys()) {
                PathAddress workerPath = context.getCurrentAddress().append(WorkerResourceDefinition.PATH.getKey(), workerName);
                maxThreadsBuilder.requires(ServiceDependency.on(WorkerResourceDefinition.CAPABILITY.getCapabilityServiceName(workerPath)));
            }
        }

        List<ResourceServiceInstaller> installers = new ArrayList<>(2);
        installers.add(maxThreadsBuilder.build());

        ServiceDependency<XnioWorker> defaultWorker = DEFAULT_WORKER.resolve(context, model);
        if (defaultWorker.isPresent()) {
            installers.add(CapabilityServiceInstaller.builder(DEFAULT_WORKER_CAPABILITY, defaultWorker).build());
        }

        return ResourceServiceInstaller.combine(installers);
    }
}
