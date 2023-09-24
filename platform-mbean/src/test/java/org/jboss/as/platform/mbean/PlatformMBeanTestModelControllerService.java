/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.util.concurrent.CountDownLatch;

import org.jboss.as.controller.AbstractControllerService;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelControllerClientFactory;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ResourceBuilder;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.ManagementSecurityIdentitySupplier;
import org.jboss.as.controller.audit.AuditLogger;
import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.ValidateAddressOperationHandler;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.persistence.NullConfigurationPersister;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;

/**
 * {@code Service<ModelController>} implementation for use in platform mbean resource tests.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class PlatformMBeanTestModelControllerService extends AbstractControllerService {

    final CountDownLatch latch = new CountDownLatch(2);

    /**
     * Construct a new instance.
     *
     */
    protected PlatformMBeanTestModelControllerService() {
        super(ProcessType.EMBEDDED_SERVER, new RunningModeControl(RunningMode.NORMAL), new NullConfigurationPersister(), new ControlledProcessState(true),
        ResourceBuilder.Factory.create(PathElement.pathElement("root"),NonResolvingResourceDescriptionResolver.INSTANCE).build(), null, ExpressionResolver.TEST_RESOLVER,
        AuditLogger.NO_OP_LOGGER, new DelegatingConfigurableAuthorizer(), new ManagementSecurityIdentitySupplier(), new CapabilityRegistry(true));
    }

    @Override
    protected void initModel(ManagementModel managementModel, Resource modelControllerResource) {
        ManagementResourceRegistration rootRegistration = managementModel.getRootResourceRegistration();
        GlobalOperationHandlers.registerGlobalOperations(rootRegistration, processType);
        rootRegistration.registerOperationHandler(ValidateAddressOperationHandler.DEFINITION, ValidateAddressOperationHandler.INSTANCE);

        GlobalNotifications.registerGlobalNotifications(rootRegistration, processType);

        // Platform mbeans
        PlatformMBeanResourceRegistrar.registerPlatformMBeanResources(rootRegistration);
        managementModel.getRootResource().registerChild(PlatformMBeanConstants.ROOT_PATH, new RootPlatformMBeanResource());
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
    protected ModelControllerClientFactory getModelControllerClientFactory() {
        return super.getModelControllerClientFactory();
    }
}
