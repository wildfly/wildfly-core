/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.requestcontroller;

import static org.jboss.as.server.Services.JBOSS_SUSPEND_CONTROLLER;

import java.io.IOException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.notification.NotificationFilter;
import org.jboss.as.controller.notification.NotificationHandler;
import org.jboss.as.controller.notification.NotificationHandlerRegistry;
import org.jboss.as.server.suspend.SuspendController;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Stuart Douglas
 */
public class RequestControllerSubsystemTestCase extends AbstractSubsystemBaseTest {

    public RequestControllerSubsystemTestCase() {
        super(RequestControllerExtension.SUBSYSTEM_NAME, new RequestControllerExtension());
    }


    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("request-controller-1.0.xml");
    }

    @Test
    public void testRuntime() throws Exception {
        KernelServicesBuilder builder = createKernelServicesBuilder(createAdditionalInitialization())
                .setSubsystemXml(getSubsystemXml());
        KernelServices mainServices = builder.build();
        if (!mainServices.isSuccessfulBoot()) {
            Assert.fail(mainServices.getBootError().toString());
        }
        ServiceController<RequestController> workerServiceController = (ServiceController<RequestController>) mainServices.getContainer().getService(RequestController.SERVICE_NAME);
        workerServiceController.setMode(ServiceController.Mode.ACTIVE);
        workerServiceController.awaitValue();
        RequestController controller = workerServiceController.getService().getValue();
        Assert.assertEquals(100, controller.getMaxRequestCount());
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return new AdditionalInitialization() {

            @Override
            protected void addExtraServices(ServiceTarget target) {
                SuspendController suspendController = new SuspendController();
                final NotificationHandlerRegistry nhr = new NotificationHandlerRegistry() {
                    @Override
                    public void registerNotificationHandler(PathAddress source, NotificationHandler handler, NotificationFilter filter) {

                    }

                    @Override
                    public void unregisterNotificationHandler(PathAddress source, NotificationHandler handler, NotificationFilter filter) {

                    }
                };
                suspendController.getNotificationHandlerRegistry().setValue(() -> nhr);
                target.addService(JBOSS_SUSPEND_CONTROLLER, suspendController)
                        .install();
            }

            @Override
            protected RunningMode getRunningMode() {
                return RunningMode.NORMAL;
            }
        };
    }

}
