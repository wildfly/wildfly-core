/*
 *
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2013, Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags. See the copyright.txt file in the
 *  distribution for a full listing of individual contributors.
 *
 *  This is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this software; if not, write to the Free
 *  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  02110-1301 USA, or see the FSF site: http://www.fsf.org.
 * /
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
import org.jboss.msc.value.ImmediateValue;
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
                suspendController.getNotificationHandlerRegistry().setValue(new ImmediateValue<>(new NotificationHandlerRegistry() {
                    @Override
                    public void registerNotificationHandler(PathAddress source, NotificationHandler handler, NotificationFilter filter) {

                    }

                    @Override
                    public void unregisterNotificationHandler(PathAddress source, NotificationHandler handler, NotificationFilter filter) {

                    }
                }));
                target.addService(JBOSS_SUSPEND_CONTROLLER, suspendController)
                        .addAliases(SuspendController.SERVICE_NAME)
                        .install();
            }

            @Override
            protected RunningMode getRunningMode() {
                return RunningMode.NORMAL;
            }
        };
    }

}
