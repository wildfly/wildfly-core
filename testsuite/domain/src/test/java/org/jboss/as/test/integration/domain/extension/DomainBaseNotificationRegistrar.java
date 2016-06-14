/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.integration.domain.extension;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Map;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.notification.NotificationFilter;
import org.jboss.as.controller.notification.NotificationHandler;
import org.jboss.as.controller.notification.NotificationRegistrar;
import org.jboss.as.controller.notification.NotificationRegistrarContext;
import org.jboss.dmr.ModelNode;

/**
 * @author Kabir Khan
 */
public class DomainBaseNotificationRegistrar implements NotificationRegistrar {

    private final String qualifier;

    protected DomainBaseNotificationRegistrar(String qualifier) {
        this.qualifier = qualifier;
    }

    @Override
    public void registerNotificationListeners(NotificationRegistrarContext context) {
        if (context.getRunningMode() != RunningMode.NORMAL) {
            throw new RuntimeException("RunningMode " + context.getRunningMode());
        }
        if (context.getProcessType() != ProcessType.HOST_CONTROLLER && context.getProcessType() != ProcessType.DOMAIN_SERVER) {
            throw new RuntimeException("ProcessType " + context.getProcessType());
        }
        if (context.getModelControllerClient() == null) {
            throw new RuntimeException("No client");
        }

        File file = determineAndCreateFile(context.getProcessType());

        TestNotificationHandler handler = new TestNotificationHandler(file, qualifier, context.getName(), context.getProperties());
        context.getNotificationRegistry().registerNotificationHandler(
                PathAddress.pathAddress(PathElement.pathElement(SYSTEM_PROPERTY)), handler, handler);
    }

    private File determineAndCreateFile(ProcessType processType) {
        File file = processType.isServer() ?
                new File(System.getProperty("jboss.server.data.dir")) :
                new File(System.getProperty("jboss.domain.data.dir"));

        while (!file.getName().equals("target")) {
            file = file.getParentFile();
        }

        if (!file.getName().equals("target")) {
            throw new RuntimeException("Could not find target directory");
        }

        file = new File(file, "test-notifications");
        if (!file.exists()) {
            file.mkdir();
        }

        if (processType == ProcessType.HOST_CONTROLLER) {
            file = new File(file, "hc");
        } else {
            file = new File(file, "server");
        }
        file.mkdir();
        file = new File(file, "notifications.dmr");
        return file;
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

    private static class TestNotificationHandler extends ModelNodeToFileCommon implements NotificationHandler, NotificationFilter {
        private final String qualifier;
        private final String name;
        private final Map<String, String> properties;

        public TestNotificationHandler(File file, String qualifier, String name, Map<String, String> properties) {
            super(file);
            this.qualifier = qualifier;
            this.name = name;
            this.properties = properties;
        }

        @Override
        public void handleNotification(Notification notification) {
            //WFCORE-1538 only the attribute-value-written notifications contain data.
            ModelNode data = notification.getType().equals(ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION) ?
                    notification.getData().clone() : new ModelNode();
            data.get("qualifier").set(qualifier);
            data.get("type").set(notification.getType());
            data.get("source").set(notification.getSource().toModelNode());
            data.get("registrar-name").set(name);
            if (properties != null && properties.size() > 0) {
                ModelNode props = new ModelNode();
                for (Map.Entry<String, String> entry : properties.entrySet()) {
                    props.get(entry.getKey()).set(entry.getValue());
                }
                data.get("properties").set(props);
            }
            writeData(data);
        }

        @Override
        public boolean isNotificationEnabled(Notification notification) {
            PathAddress source = notification.getSource();
            if (source.size() != 1) {
                return false;
            }
            PathElement element = source.getElement(0);
            return element.getKey().equals(SYSTEM_PROPERTY);
        }
    }
}
