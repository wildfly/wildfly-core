/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.jboss.as.controller.AbstractControllerService;
import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessState.State;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ResourceBuilder;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.ManagementSecurityIdentitySupplier;
import org.jboss.as.controller.audit.AuditLogger;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.persistence.ConfigurationExtension;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.as.controller.persistence.NullConfigurationPersister;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;

/**
 * A simple {@code Service<ModelController>} base class for use in unit tests.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public abstract class TestModelControllerService extends AbstractControllerService {

    private final ControlledProcessState processState;
    final AtomicBoolean state = new AtomicBoolean(true);
    private final CountDownLatch latch = new CountDownLatch(2);
    private final CapabilityRegistry capabilityRegistry;

    protected TestModelControllerService() {
        this(ProcessType.EMBEDDED_SERVER, new NullConfigurationPersister(), new ControlledProcessState(true));
    }

    protected TestModelControllerService(ProcessType processType) {
        this(processType, new NullConfigurationPersister(), new ControlledProcessState(true));
    }

    protected TestModelControllerService(ProcessType processType, final ConfigurationPersister configurationPersister, final ControlledProcessState processState) {
        this(processType, configurationPersister, processState,
                ResourceBuilder.Factory.create(PathElement.pathElement("root"), NonResolvingResourceDescriptionResolver.INSTANCE).build());
    }

    protected TestModelControllerService(final ConfigurationPersister configurationPersister, final ControlledProcessState processState) {
        this(ProcessType.EMBEDDED_SERVER, configurationPersister, processState,
                ResourceBuilder.Factory.create(PathElement.pathElement("root"), NonResolvingResourceDescriptionResolver.INSTANCE).build());
    }

    protected TestModelControllerService(final ProcessType processType, final ConfigurationPersister configurationPersister, final ControlledProcessState processState,
                                         final ResourceDefinition rootResourceDefinition) {
        this(processType, configurationPersister, processState, rootResourceDefinition, new CapabilityRegistry(processType.isServer()));
    }

    protected TestModelControllerService(final ProcessType processType, final RunningModeControl runningModeControl, Supplier<ExecutorService> executorService,
                                         final ConfigurationPersister configurationPersister, final ControlledProcessState processState,
                                         final ResourceDefinition rootResourceDefinition) {
        this(processType, runningModeControl,executorService, configurationPersister, processState, rootResourceDefinition, new CapabilityRegistry(processType.isServer()), null);
    }

    protected TestModelControllerService(final ProcessType processType, final ConfigurationPersister configurationPersister, final ControlledProcessState processState,
                                         final ResourceDefinition rootResourceDefinition, final CapabilityRegistry capabilityRegistry) {
        this(processType, new RunningModeControl(RunningMode.NORMAL), null, configurationPersister, processState, rootResourceDefinition, capabilityRegistry, null);
    }

    protected TestModelControllerService(final ProcessType processType, final RunningModeControl runningModeControl, Supplier<ExecutorService> executorService,
                                         final ConfigurationPersister configurationPersister, final ControlledProcessState processState,
                                         final ResourceDefinition rootResourceDefinition, final CapabilityRegistry capabilityRegistry, final ConfigurationExtension configExtension) {
        super(executorService, null, processType, runningModeControl, configurationPersister, processState, rootResourceDefinition, null, ExpressionResolver.TEST_RESOLVER,
                AuditLogger.NO_OP_LOGGER, new DelegatingConfigurableAuthorizer(), new ManagementSecurityIdentitySupplier(), capabilityRegistry, configExtension);
        this.processState = processState;
        this.capabilityRegistry = capabilityRegistry;
    }

    public AtomicBoolean getSharedState() {
        return state;
    }

    public State getCurrentProcessState() {
        return processState.getState();
    }

    public void awaitStartup(long timeout, TimeUnit timeUnit) throws InterruptedException {
        if (!latch.await(timeout, timeUnit)) {
            throw new RuntimeException("Failed to boot in timely fashion");
        }
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        latch.countDown();
    }

    @Override
    protected void bootThreadDone() {
        super.bootThreadDone();
        latch.countDown();
    }

    public CapabilityRegistry getCapabilityRegistry() {
        return capabilityRegistry;
    }

    static OperationDefinition getOD(String name) {
        return getODBuilder(name).build();
    }

    static SimpleOperationDefinitionBuilder getODBuilder(String name) {
        return new SimpleOperationDefinitionBuilder(name, NonResolvingResourceDescriptionResolver.INSTANCE);
    }
}
