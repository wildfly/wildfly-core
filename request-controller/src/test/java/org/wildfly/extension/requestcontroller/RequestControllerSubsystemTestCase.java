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

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Executor;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.notification.NotificationFilter;
import org.jboss.as.controller.notification.NotificationHandler;
import org.jboss.as.controller.registry.NotificationHandlerRegistration;
import org.jboss.as.server.suspend.SuspendController;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.dmr.ModelNode;
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
                suspendController.getModelControllerInjectedValue().setValue(new ImmediateValue<>(new ModelController() {
                    @Override
                    public ModelNode execute(ModelNode operation, OperationMessageHandler handler, OperationTransactionControl control, OperationAttachments attachments) {
                        return null;
                    }

                    @Override
                    public OperationResponse execute(Operation operation, OperationMessageHandler handler, OperationTransactionControl control) {
                        return null;
                    }

                    @Override
                    public ModelControllerClient createClient(Executor executor) {
                        return null;
                    }

                    @Override
                    public NotificationHandlerRegistration getNotificationRegistry() {
                        return new NotificationHandlerRegistration() {
                            @Override
                            public void registerNotificationHandler(PathAddress source, NotificationHandler handler, NotificationFilter filter) {

                            }

                            @Override
                            public void unregisterNotificationHandler(PathAddress source, NotificationHandler handler, NotificationFilter filter) {

                            }

                            @Override
                            public Collection<NotificationHandler> findMatchingNotificationHandlers(Notification notification) {
                                return null;
                            }
                        };
                    }
                }));
                target.addService(SuspendController.SERVICE_NAME, suspendController).install();
            }

            @Override
            protected RunningMode getRunningMode() {
                return RunningMode.NORMAL;
            }
        };
    }

}
