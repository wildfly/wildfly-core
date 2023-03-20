/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.http.server;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.jboss.as.controller.AbstractControllerService;
import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ResourceBuilder;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.FeatureStream;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.ManagementSecurityIdentitySupplier;
import org.jboss.as.controller.audit.AuditLogger;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;

/**
 * A simple {@code Service<ModelController>} base class for use in unit tests.
 *
 * @author <a href="mailto:yborgess@redhat.com">Yeray Borges</a>
 */
public abstract class TestModelControllerService extends AbstractControllerService {
    private final CountDownLatch latch = new CountDownLatch(2);
    private final CapabilityRegistry capabilityRegistry;

    protected TestModelControllerService(ProcessType processType, FeatureStream stream, final ConfigurationPersister configurationPersister, final ControlledProcessState processState) {
        this(processType, stream, configurationPersister, processState, ResourceBuilder.Factory.create(PathElement.pathElement("root"), NonResolvingResourceDescriptionResolver.INSTANCE).build());
    }

    protected TestModelControllerService(final ProcessType processType, FeatureStream stream, final ConfigurationPersister configurationPersister, final ControlledProcessState processState,
                                         final ResourceDefinition rootResourceDefinition) {
        this(processType, stream, configurationPersister, processState, rootResourceDefinition, new CapabilityRegistry(processType.isServer()));
    }

    protected TestModelControllerService(final ProcessType processType, FeatureStream stream, final ConfigurationPersister configurationPersister, final ControlledProcessState processState,
                                         final ResourceDefinition rootResourceDefinition, final CapabilityRegistry capabilityRegistry) {
        this(processType, stream, new RunningModeControl(RunningMode.NORMAL), null, configurationPersister, processState, rootResourceDefinition, capabilityRegistry);
    }

    protected TestModelControllerService(final ProcessType processType, FeatureStream stream, final RunningModeControl runningModeControl, Supplier<ExecutorService> executorService,
                                         final ConfigurationPersister configurationPersister, final ControlledProcessState processState,
                                         final ResourceDefinition rootResourceDefinition, final CapabilityRegistry capabilityRegistry) {
        super(executorService, null, processType, stream, runningModeControl, configurationPersister, processState, rootResourceDefinition, null, ExpressionResolver.TEST_RESOLVER,
                AuditLogger.NO_OP_LOGGER, new DelegatingConfigurableAuthorizer(), new ManagementSecurityIdentitySupplier(), capabilityRegistry, null);
        this.capabilityRegistry = capabilityRegistry;
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
}
