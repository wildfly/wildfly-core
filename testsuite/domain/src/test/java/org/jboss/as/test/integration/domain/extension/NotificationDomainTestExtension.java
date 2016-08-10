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

package org.jboss.as.test.integration.domain.extension;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_CONFIGURATION_STATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.util.List;

import javax.management.AttributeChangeNotification;
import javax.management.MBeanServer;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.ControlledProcessStateJmxMBean;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.notification.NotificationFilter;
import org.jboss.as.controller.notification.NotificationHandler;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.DomainModelControllerService;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.server.Services;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * @author Kabir Khan
 */
public class NotificationDomainTestExtension implements Extension {
    public static final String MODULE_NAME = "org.wildfly.processstatenotification";
    private static final String NAMESPACE = "urn:jboss:test:notification-tracker:1.0";
    public static final String SUBSYSTEM_NAME = "notification-tracker";
    public static final String MASTER_FACADE_ROOT = "jboss.as:host=master";

    //The notification handlers/listeners installed directly by the OSH
    public static final String MANAGEMENT_DIRECT_FILE = "mgmt-direct-notifications.dmr";
    public static final String JMX_DIRECT_FILE = "jmx-direct-notifications.dmr";

    //The notification handlers/listeners installed by the service installed by the OSH
    public static final String MANAGEMENT_SERVICE_FILE = "mgmt-service-notifications.dmr";
    public static final String JMX_SERVICE_FILE = "jmx-service-notifications.dmr";

    //The jmx facade listener
    public static final String JMX_FACADE_FILE = "jmx-facade-notifications.dmr";

    @Override
    public void initialize(ExtensionContext context) {
        System.out.println("Initializing NotificationTestExtension");
        SubsystemRegistration registration = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(1));
        registration.setHostCapable();
        registration.registerSubsystemModel(SubsystemResourceDefinition.create(context.getProcessType()));


        registration.registerXMLElementWriter((writer, ctx) -> {
            ctx.startSubsystemElement(NAMESPACE, true);
        });
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE, new XMLElementReader<List<ModelNode>>() {

            @Override
            public void readElement(XMLExtendedStreamReader reader, List<ModelNode> ops) throws XMLStreamException {
                ParseUtils.requireNoAttributes(reader);
                ParseUtils.requireNoContent(reader);
                ops.add(Util.createAddOperation(PathAddress.pathAddress(SUBSYSTEM, SUBSYSTEM_NAME)));
            }
        });
    }

    private static File getFile(String fileName) {
        return new File(System.getProperty(HostControllerEnvironment.DOMAIN_DATA_DIR), fileName);
    }

    public static class SubsystemResourceDefinition extends SimpleResourceDefinition {

        private SubsystemResourceDefinition(AddHandler addHandler) {
            super(PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME), new NonResolvingResourceDescriptionResolver(),
                    addHandler, new ModelOnlyRemoveStepHandler());
        }

        static SubsystemResourceDefinition create(ProcessType processType) {
            if (processType != ProcessType.HOST_CONTROLLER) {
                throw new IllegalArgumentException("Unhandled process state");
            }
            return new SubsystemResourceDefinition(AddHandler.INSTANCE);
        }
    }

    private static class AddHandler extends AbstractAddStepHandler {
        static final AddHandler INSTANCE = new AddHandler();
        volatile JMXNotificationListener directListener;

        private AddHandler() {
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return true;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            if (context.isBooting()) {
                ///////////////////////////////////
                // 1) Install a service to install the handlers
                installService(context);

                ///////////////////////////////////
                //2) None of these 'direct' ones would get removed by a remove step handler

                //Since the notification registry is recreated on server reload, we need to re-register the handler
                //on every start. This means we don't get the stopped->starting notification.
                ManagementNotificationHandler handler = new ManagementNotificationHandler(getFile(MANAGEMENT_DIRECT_FILE));
                context.registerNotificationHandler(PathAddress.EMPTY_ADDRESS, handler, handler);

                if (directListener == null) {
                    //Only add the JMXNotificationListener on the first load. Since the platform mbean server is kept
                    //during server reloads we can look for the stopped->starting notification
                    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
                    directListener = new JMXNotificationListener(getFile(JMX_DIRECT_FILE));
                    try {
                        server.addNotificationListener(new ObjectName(ControlledProcessStateJmxMBean.OBJECT_NAME), directListener, null, null);
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }

            } else {
                context.reloadRequired();
            }
        }

        static void installService(OperationContext context) {
            ServiceName controllerName = context.getProcessType().isServer() ?
                    Services.JBOSS_SERVER_CONTROLLER : DomainModelControllerService.SERVICE_NAME;

            ListenerService listenerService = new ListenerService(context.getProcessType());
            context.getServiceTarget().addService(ListenerService.NAME, listenerService)
                    .addDependency(controllerName, ModelController.class, listenerService.controllerValue)
                    .setInitialMode(ServiceController.Mode.ACTIVE)
                    .install();

            JmxFacadeListenerService jmxFacadeListenerService = new JmxFacadeListenerService(context.getProcessType());
            context.getServiceTarget().addService(JmxFacadeListenerService.NAME, jmxFacadeListenerService)
                    .addDependency(ServiceName.parse("org.wildfly.management.jmx"), MBeanServer.class, jmxFacadeListenerService.mbeanServerValue)
                    .setInitialMode(ServiceController.Mode.ACTIVE)
                    .install();
        }

    }

    private static class ListenerService implements Service<Void> {
        static final ServiceName NAME = ServiceName.JBOSS.append("test", "notification", "listeners");

        private final ManagementNotificationHandler handler;
        private final JMXNotificationListener listener;
        private final InjectedValue<ModelController> controllerValue = new InjectedValue<>();

        public ListenerService(ProcessType processType) {
            handler = new ManagementNotificationHandler(getFile(MANAGEMENT_SERVICE_FILE));
            listener = new JMXNotificationListener(getFile(JMX_SERVICE_FILE));
        }

        @Override
        public void start(StartContext context) throws StartException {
            controllerValue.getValue().getNotificationRegistry().registerNotificationHandler(PathAddress.EMPTY_ADDRESS, handler, handler);

            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            try {
                server.addNotificationListener(new ObjectName(ControlledProcessStateJmxMBean.OBJECT_NAME), listener, null, null);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }

        }

        @Override
        public void stop(StopContext context) {
            controllerValue.getValue().getNotificationRegistry().unregisterNotificationHandler(PathAddress.EMPTY_ADDRESS, handler, handler);
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            try {
                server.removeNotificationListener(new ObjectName(ControlledProcessStateJmxMBean.OBJECT_NAME), listener, null, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public Void getValue() throws IllegalStateException, IllegalArgumentException {
            return null;
        }
    }

    private static class JmxFacadeListenerService implements Service<Void> {
        static final ServiceName NAME = ServiceName.JBOSS.append("test", "notification", "facade", "listener");

        private final JMXNotificationListener listener;

        //Inject the mbean service so that the facade has been initialised
        InjectedValue<MBeanServer> mbeanServerValue = new InjectedValue<>();

        public JmxFacadeListenerService(ProcessType processType) {
            listener = new JMXNotificationListener(getFile(JMX_FACADE_FILE));
        }

        @Override
        public void start(StartContext context) throws StartException {
            try {
                mbeanServerValue.getValue().addNotificationListener(new ObjectName(MASTER_FACADE_ROOT), listener, null, null);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        @Override
        public void stop(StopContext context) {
            try {
                mbeanServerValue.getValue().removeNotificationListener(new ObjectName(MASTER_FACADE_ROOT), listener, null, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public Void getValue() throws IllegalStateException, IllegalArgumentException {
            return null;
        }
    }



    private static class ModelNodeToFileCommon {
        final File file;

        public ModelNodeToFileCommon(File file) {
            this.file = file;
        }

        void writeData(ModelNode data) {
            try {
                ModelNode output = readNotificationOutput(file);
                output.add(data);
                writeNotificationOutput(file, output);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private static ModelNode readNotificationOutput(File file) throws IOException {
            if (!file.exists()) {
                return new ModelNode();
            }
            try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                return ModelNode.fromJSONStream(in);
            }
        }

        private static void writeNotificationOutput(File file, ModelNode output) throws IOException {
            file.delete();
            file.createNewFile();

            try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
                output.writeJSONString(out, false);
            }
        }

    }

    private static class ManagementNotificationHandler extends ModelNodeToFileCommon implements NotificationHandler, NotificationFilter {

        public ManagementNotificationHandler(File file) {
            super(file);
        }

        @Override
        public void handleNotification(Notification notification) {
            ModelNode data = notification.getData().clone();
            data.get("type").set(notification.getType());
            data.get("source").set(notification.getSource().toModelNode());
            writeData(data);
        }

        @Override
        public boolean isNotificationEnabled(Notification notification) {
            PathAddress source = notification.getSource();
            if (source.size() != 1) {
                return false;
            }
            return source.getElement(0).getKey().equals(HOST) &&
                    notification.getData().get(NAME).asString().equals(RUNTIME_CONFIGURATION_STATE);
        }
    }

    private static class JMXNotificationListener extends ModelNodeToFileCommon implements NotificationListener {

        public JMXNotificationListener(File file) {
            super(file);
        }

        @Override
        public void handleNotification(javax.management.Notification notification, Object handback) {
            if (notification.getType().equals(AttributeChangeNotification.ATTRIBUTE_CHANGE)) {
                AttributeChangeNotification attrChange = (AttributeChangeNotification)notification;

                ModelNode data = new ModelNode();
                try {
                    data.get(NAME).set(attrChange.getAttributeName());
                    data.get(TYPE).set(attrChange.getAttributeType());
                    data.get("source").set(attrChange.getSource().toString());
                    data.get("old-value").set(attrChange.getOldValue().toString());
                    data.get("new-value").set(attrChange.getNewValue().toString());
                } catch (Exception ignore) {
                    //Ignore this to be able to test that rogue notifications don't make it through as addressed in WFCORE-1518
                    ignore.printStackTrace();
                }
                writeData(data);
            }
        }
    }
}
