/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.jmx.test.util;

import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.jboss.as.controller.AbstractControllerService;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessState.State;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelControllerServiceInitialization;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.ManagementSecurityIdentitySupplier;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.version.Stability;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;

/**
 * A simple {@code Service<ModelController>} base class for use in unit tests.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public abstract class TestModelControllerService extends AbstractControllerService {

    protected static final Supplier<PathManager> DEFAULT_PATH_MANAGER = () -> new PathManagerService() {
    };
    private final ControlledProcessState processState;
    final AtomicBoolean state = new AtomicBoolean(true);
    private final ProcessType processType;
    private final CountDownLatch latch = new CountDownLatch(2);

    protected TestModelControllerService(final ProcessType processType, final ConfigurationPersister configurationPersister, final ControlledProcessState processState,
                                         final ResourceDefinition rootResourceDefinition, final ManagedAuditLogger auditLogger, final DelegatingConfigurableAuthorizer authorizer) {
        super(null,
                null,
                processType, Stability.DEFAULT, new RunningModeControl(RunningMode.NORMAL),
                configurationPersister,
                processState, rootResourceDefinition, null, ExpressionResolver.TEST_RESOLVER,
                auditLogger, authorizer == null ? new DelegatingConfigurableAuthorizer() : authorizer,
                new ManagementSecurityIdentitySupplier(),
                new CapabilityRegistry(true), null,
                DEFAULT_PATH_MANAGER);
        this.processState = processState;
        this.processType = processType;
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

    @Override
    protected ModelControllerServiceInitializationParams getModelControllerServiceInitializationParams() {
        final ServiceLoader<ModelControllerServiceInitialization> sl = ServiceLoader.load(ModelControllerServiceInitialization.class);
        return new ModelControllerServiceInitializationParams(sl) {
            @Override
            public String getHostName() {
                return null;
            }
        };
    }

    @Override
    protected void initModel(ManagementModel managementModel, Resource modelControllerResource) {
    }
}
