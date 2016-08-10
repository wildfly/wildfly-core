/*
Copyright 2016 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.jboss.as.controller.notification;

import static org.jboss.as.controller.SimpleAttributeDefinitionBuilder.create;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_CONFIGURATION_STATE;
import static org.jboss.dmr.ModelType.BOOLEAN;
import static org.jboss.dmr.ModelType.STRING;

import java.util.List;

import org.jboss.as.controller.BootContext;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.test.AbstractControllerTestBase;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Kabir Khan
 */
public class ControlledProcessStateNotificationsTestCase extends AbstractControllerTestBase {

    public static final SimpleAttributeDefinition STATE_ATTRIBUTE = create(RUNTIME_CONFIGURATION_STATE, STRING)
            .setStorageRuntime()
            .build();

    public static final SimpleAttributeDefinition RR = create("rr", BOOLEAN)
            .build();

    private TestControllerService controllerService;
    private ListBackedNotificationHandler handler;
    private NotificationFilter filter;

    @Override
    protected ModelControllerService createModelControllerService(ProcessType processType) {
        controllerService = new TestControllerService(processType);
        return controllerService;
    }

    @Override
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration rootRegistration = managementModel.getRootResourceRegistration();
        // register the global operations to be able to call :read-attribute and :write-attribute
        GlobalOperationHandlers.registerGlobalOperations(rootRegistration, processType);
        // register the global notifications so there is no warning that emitted notifications are not described by the resource.
        GlobalNotifications.registerGlobalNotifications(rootRegistration, processType);

        rootRegistration.registerReadOnlyAttribute(STATE_ATTRIBUTE, new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                context.getResult().set(controllerService.getCurrentProcessState().toString());
            }
        });

        rootRegistration.registerReadWriteAttribute(RR, null, new ModelOnlyWriteAttributeHandler(RR) {
            @Override
            protected void finishModelStage(OperationContext context, ModelNode operation, String attributeName, ModelNode newValue, ModelNode oldValue, Resource model) throws OperationFailedException {
                super.finishModelStage(context, operation, attributeName, newValue, oldValue, model);
                if (newValue.asBoolean()) {
                    context.reloadRequired();
                } else {
                    context.restartRequired();
                }
            }
        });

        handler = new ListBackedNotificationHandler();
    }


    @Test
    public void testStartStop() throws Exception {
        List<Notification> notifications = handler.getNotifications();
        Assert.assertEquals(1, notifications.size());
        Notification ok = notifications.get(0);
        Assert.assertEquals(ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION, ok.getType());
        ModelNode data = ok.getData();
        Assert.assertEquals(RUNTIME_CONFIGURATION_STATE, data.get(NAME).asString());
        Assert.assertEquals("starting", data.get("old-value").asString());
        Assert.assertEquals("ok", data.get("new-value").asString());
        handler.getNotifications().clear();

        super.shutdownServiceContainer();
        notifications = handler.getNotifications();
        Assert.assertEquals(1, notifications.size());
        Notification stopped = notifications.get(0);
        Assert.assertEquals(ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION, stopped.getType());
        data = stopped.getData();
        Assert.assertEquals(RUNTIME_CONFIGURATION_STATE, data.get(NAME).asString());
        Assert.assertEquals("ok", data.get("old-value").asString());
        Assert.assertEquals("stopped", data.get("new-value").asString());
        handler.getNotifications().clear();
    }

    @Test
    public void testReloadRequiredAndStop() throws Exception {
        List<Notification> notifications = handler.getNotifications();
        Assert.assertEquals(1, notifications.size());
        Notification ok = notifications.get(0);
        Assert.assertEquals(ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION, ok.getType());
        ModelNode data = ok.getData();
        Assert.assertEquals(RUNTIME_CONFIGURATION_STATE, data.get(NAME).asString());
        Assert.assertEquals("starting", data.get("old-value").asString());
        Assert.assertEquals("ok", data.get("new-value").asString());
        handler.getNotifications().clear();

        executeCheckNoFailure(Util.getWriteAttributeOperation(PathAddress.EMPTY_ADDRESS, "rr", new ModelNode(true)));
        notifications = handler.getNotifications();
        Assert.assertEquals(1, notifications.size());
        Notification reload = notifications.get(0);
        Assert.assertEquals(ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION, reload.getType());
        data = reload.getData();
        Assert.assertEquals(RUNTIME_CONFIGURATION_STATE, data.get(NAME).asString());
        Assert.assertEquals("ok", data.get("old-value").asString());
        Assert.assertEquals("reload-required", data.get("new-value").asString());
        handler.getNotifications().clear();

        super.shutdownServiceContainer();
        notifications = handler.getNotifications();
        Assert.assertEquals(1, notifications.size());
        Notification stopped = notifications.get(0);
        Assert.assertEquals(ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION, stopped.getType());
        data = stopped.getData();
        Assert.assertEquals(RUNTIME_CONFIGURATION_STATE, data.get(NAME).asString());
        Assert.assertEquals("reload-required", data.get("old-value").asString());
        Assert.assertEquals("stopped", data.get("new-value").asString());
        handler.getNotifications().clear();
    }

    @Test
    public void testRestartRequiredAndStop() throws Exception {
        List<Notification> notifications = handler.getNotifications();
        Assert.assertEquals(1, notifications.size());
        Notification ok = notifications.get(0);
        Assert.assertEquals(ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION, ok.getType());
        ModelNode data = ok.getData();
        Assert.assertEquals(RUNTIME_CONFIGURATION_STATE, data.get(NAME).asString());
        Assert.assertEquals("starting", data.get("old-value").asString());
        Assert.assertEquals("ok", data.get("new-value").asString());
        handler.getNotifications().clear();

        executeCheckNoFailure(Util.getWriteAttributeOperation(PathAddress.EMPTY_ADDRESS, "rr", new ModelNode(false)));
        notifications = handler.getNotifications();
        Assert.assertEquals(1, notifications.size());
        Notification reload = notifications.get(0);
        Assert.assertEquals(ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION, reload.getType());
        data = reload.getData();
        Assert.assertEquals(RUNTIME_CONFIGURATION_STATE, data.get(NAME).asString());
        Assert.assertEquals("ok", data.get("old-value").asString());
        Assert.assertEquals("restart-required", data.get("new-value").asString());
        handler.getNotifications().clear();

        super.shutdownServiceContainer();
        notifications = handler.getNotifications();
        Assert.assertEquals(1, notifications.size());
        Notification stopped = notifications.get(0);
        Assert.assertEquals(ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION, stopped.getType());
        data = stopped.getData();
        Assert.assertEquals(RUNTIME_CONFIGURATION_STATE, data.get(NAME).asString());
        Assert.assertEquals("restart-required", data.get("old-value").asString());
        Assert.assertEquals("stopped", data.get("new-value").asString());
        handler.getNotifications().clear();
    }


    private static class AttributeNotificationFilter implements NotificationFilter {

        private final PathAddress expectedAddress;
        private final String attributeName;

        /**
         * Filters out notifications so that the handler will handle only those that are from the {@code expectedType}
         * and emitted from the {@code expectedAddress}.
         */
        AttributeNotificationFilter(PathAddress expectedAddress, String attributeName) {
            this.expectedAddress = expectedAddress;
            this.attributeName = attributeName;
        }

        @Override
        public boolean isNotificationEnabled(Notification notification) {
            return notification.getSource().equals(expectedAddress) &&
                    notification.getData().get(NAME).asString().equals(attributeName);
        }
    }

    private class TestControllerService extends AbstractControllerTestBase.ModelControllerService {
        public TestControllerService(ProcessType processType) {
            super(processType);
        }

        @Override
        protected void boot(final BootContext context) throws ConfigurationPersistenceException {
            getValue().getNotificationRegistry().registerNotificationHandler(PathAddress.EMPTY_ADDRESS, handler,
                    new AttributeNotificationFilter(PathAddress.EMPTY_ADDRESS, RUNTIME_CONFIGURATION_STATE));
            super.boot(context);
        }

    }
}
