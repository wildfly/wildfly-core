/*
 * Copyright 2016 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.test.jmx.events;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.AttributeChangeNotification;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;
import org.jboss.as.server.logging.ServerLogger;

import org.wildfly.extension.core.management.client.RunningStateChangeEvent;
import org.wildfly.extension.core.management.client.ProcessStateListener;
import org.wildfly.extension.core.management.client.RuntimeConfigurationStateChangeEvent;
import org.wildfly.extension.core.management.client.ProcessStateListenerInitParameters;

/**
 * MBean that sends notifications when the process state changes.
 * That means when the running state or the runtime configuration state changes.
 *
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class ProcessStateJmx extends NotificationBroadcasterSupport implements ProcessStateJmxMBean, ProcessStateListener {
    public static final String RUNTIME_CONFIGURATION_STATE = "RuntimeConfigurationState";
    public static final String RUNNING_STATE = "RunningState";

    private final ObjectName objectName;
    private AtomicLong sequence = new AtomicLong(0);

    public ProcessStateJmx() {
        try {
            this.objectName = new ObjectName(OBJECT_NAME);
        } catch (MalformedObjectNameException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void init(ProcessStateListenerInitParameters parameters) {
        try {
            ProcessStateListener.super.init(parameters);
            final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            server.registerMBean(this, this.objectName);
        } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException ex) {
            ServerLogger.ROOT_LOGGER.error(ex);
        }
    }

    @Override
    public void cleanup() {
        ProcessStateListener.super.cleanup();
        try {
            final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            server.unregisterMBean(objectName);
        } catch (InstanceNotFoundException | MBeanRegistrationException ex) {
            ServerLogger.ROOT_LOGGER.error(ex);
        }
    }

    @Override
    public void runtimeConfigurationStateChanged(RuntimeConfigurationStateChangeEvent evt) {
        AttributeChangeNotification notification = new AttributeChangeNotification(objectName, sequence.getAndIncrement(),
                System.currentTimeMillis(),
                ServerLogger.ROOT_LOGGER.jmxAttributeChange(RUNTIME_CONFIGURATION_STATE, evt.getOldState().toString(), evt.getNewState().toString()),
                RUNTIME_CONFIGURATION_STATE, String.class.getName(), evt.getOldState().toString(), evt.getNewState().toString());
        sendNotification(notification);
    }

    @Override
    public void runningStateChanged(RunningStateChangeEvent evt) {
        AttributeChangeNotification notification = new AttributeChangeNotification(objectName, sequence.getAndIncrement(),
                System.currentTimeMillis(),
                ServerLogger.ROOT_LOGGER.jmxAttributeChange(RUNNING_STATE, evt.getOldState().toString(), evt.getNewState().toString()),
                RUNNING_STATE, String.class.getName(), evt.getOldState().toString(), evt.getNewState().toString());
        sendNotification(notification);
    }

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[] {new MBeanNotificationInfo(
                    new String[]{AttributeChangeNotification.ATTRIBUTE_CHANGE},
                    AttributeChangeNotification.class.getName(),
                    ServerLogger.ROOT_LOGGER.processStateChangeNotificationDescription())
        };
    }
}
