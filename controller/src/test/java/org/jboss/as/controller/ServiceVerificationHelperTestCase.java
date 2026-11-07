/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StabilityMonitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test of the failure description recorded by {@link ServiceVerificationHelper}.
 *
 * @see <a href="https://redhat.atlassian.net/browse/WFCORE-7754">WFCORE-7754 Deployment rollback caused by service dependency problems can have no failure description</a>
 * @author Radoslav Husar
 */
@SuppressWarnings("deprecation")
public class ServiceVerificationHelperTestCase {

    private static final ServiceName SERVICE = ServiceName.of("test", "service");
    private static final ServiceName DEPENDENCY = ServiceName.of("test", "dependency");
    private static final ServiceName NOT_INSTALLED = ServiceName.of("test", "not-installed");

    private ServiceContainer container;
    private OperationContext context;
    private ModelNode failureDescription;
    private ServiceVerificationHelper helper;

    @Before
    public void setUp() {
        this.container = ServiceContainer.Factory.create("test");
        this.failureDescription = new ModelNode();
        this.context = mock(OperationContext.class);
        when(this.context.getFailureDescription()).thenReturn(this.failureDescription);
        when(this.context.getServiceRegistry(false)).thenReturn(this.container);
        when(this.context.isRollbackOnRuntimeFailure()).thenReturn(true);
        this.helper = new ServiceVerificationHelper();
    }

    @After
    public void tearDown() throws InterruptedException {
        this.container.shutdown();
        this.container.awaitTermination();
    }

    /**
     * A dependency that is installed and active, but unavailable and not tracked by the operation, must not leave the failure description undefined.
     */
    @Test
    public void testInstalledButUnavailableDependency() throws InterruptedException {
        // Given
        this.installUnavailableDependency();

        ServiceBuilder<?> builder = this.container.addService(SERVICE);
        builder.requires(DEPENDENCY);
        this.helper.getMonitor().addController(builder.install());

        // When
        this.helper.execute(this.context, new ModelNode());

        // Then
        assertEquals(List.of(new ModelNode("test.service (DOWN) is missing [test.dependency (DOWN, ACTIVE)]")), this.unresolvedProblemsModelNode());
        verify(this.context).addStep(any(OperationStepHandler.class), eq(OperationContext.Stage.VERIFY));
    }

    @Test
    public void testAnonymousService() throws InterruptedException {
        // Given
        this.installUnavailableDependency();

        ServiceBuilder<?> builder = this.container.addService();
        builder.requires(DEPENDENCY);
        this.helper.getMonitor().addController(builder.install());

        // When
        this.helper.execute(this.context, new ModelNode());

        // Then
        assertEquals(List.of(new ModelNode("<anonymous> (DOWN) is missing [test.dependency (DOWN, ACTIVE)]")), this.unresolvedProblemsModelNode());
    }

    /**
     * Problems that are already explained by a dependency that is not installed are reported as before;
     * i.e. without ControllerLogger#servicesWithUnresolvedProblems()
     */
    @Test
    public void testNotInstalledDependency() {
        // Given
        ServiceBuilder<?> builder = this.container.addService(SERVICE);
        builder.requires(NOT_INSTALLED);
        this.helper.getMonitor().addController(builder.install());

        // When
        this.helper.execute(this.context, new ModelNode());

        // Then
        assertEquals(List.of(new ModelNode(NOT_INSTALLED.getCanonicalName())), this.failureDescription.get(ControllerLogger.ROOT_LOGGER.missingRequiredServices()).asList());
        assertEquals(1, this.failureDescription.get(ControllerLogger.ROOT_LOGGER.servicesMissingDependencies()).asList().size());
        assertFalse(this.failureDescription.has(ControllerLogger.ROOT_LOGGER.servicesWithUnresolvedProblems()));
    }

    /**
     * Installs an active dependency, not tracked by the helper, that is itself missing a dependency.
     */
    private void installUnavailableDependency() throws InterruptedException {
        ServiceBuilder<?> builder = this.container.addService(DEPENDENCY);
        builder.requires(NOT_INSTALLED);
        ServiceController<?> controller = builder.install();
        StabilityMonitor monitor = new StabilityMonitor();
        monitor.addController(controller);
        monitor.awaitStability();
        monitor.removeController(controller);
    }

    private List<ModelNode> unresolvedProblemsModelNode() {
        String key = ControllerLogger.ROOT_LOGGER.servicesWithUnresolvedProblems();
        assertTrue(this.failureDescription.toString(), this.failureDescription.hasDefined(key));
        return this.failureDescription.get(key).asList();
    }
}
