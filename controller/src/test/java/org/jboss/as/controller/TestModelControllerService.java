/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.jboss.as.controller.ControlledProcessState.State;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.ManagementSecurityIdentitySupplier;
import org.jboss.as.controller.audit.AuditLogger;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
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
                ResourceBuilder.Factory.create(PathElement.pathElement("root"), new NonResolvingResourceDescriptionResolver()).build());
    }

    protected TestModelControllerService(final ConfigurationPersister configurationPersister, final ControlledProcessState processState) {
        this(ProcessType.EMBEDDED_SERVER, configurationPersister, processState,
                ResourceBuilder.Factory.create(PathElement.pathElement("root"), new NonResolvingResourceDescriptionResolver()).build());
    }

    protected TestModelControllerService(final ProcessType processType, final ConfigurationPersister configurationPersister, final ControlledProcessState processState,
                                         final ResourceDefinition rootResourceDefinition) {
        this(processType, configurationPersister, processState, rootResourceDefinition, new CapabilityRegistry(processType.isServer()));
    }

    protected TestModelControllerService(final ProcessType processType, final RunningModeControl runningModeControl, Supplier<ExecutorService> executorService,
                                         final ConfigurationPersister configurationPersister, final ControlledProcessState processState,
                                         final ResourceDefinition rootResourceDefinition) {
        this(processType, runningModeControl,executorService, configurationPersister, processState, rootResourceDefinition, new CapabilityRegistry(processType.isServer()));
    }

    protected TestModelControllerService(final ProcessType processType, final ConfigurationPersister configurationPersister, final ControlledProcessState processState,
                                         final ResourceDefinition rootResourceDefinition, final CapabilityRegistry capabilityRegistry) {
        this(processType, new RunningModeControl(RunningMode.NORMAL), null, configurationPersister, processState, rootResourceDefinition, capabilityRegistry);
    }

    protected TestModelControllerService(final ProcessType processType, final RunningModeControl runningModeControl, Supplier<ExecutorService> executorService,
                                         final ConfigurationPersister configurationPersister, final ControlledProcessState processState,
                                         final ResourceDefinition rootResourceDefinition, final CapabilityRegistry capabilityRegistry) {
        super(executorService, null, processType, runningModeControl, configurationPersister, processState, rootResourceDefinition, null, ExpressionResolver.TEST_RESOLVER,
                AuditLogger.NO_OP_LOGGER, new DelegatingConfigurableAuthorizer(), new ManagementSecurityIdentitySupplier(), capabilityRegistry);
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
        return new SimpleOperationDefinitionBuilder(name, new NonResolvingResourceDescriptionResolver());
    }
}
