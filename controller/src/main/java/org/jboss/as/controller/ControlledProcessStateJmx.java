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

package org.jboss.as.controller;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.AttributeChangeNotification;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;

import org.jboss.as.controller.logging.ControllerLogger;


/**
 * @author Kabir Khan
 * @see ControlledProcessStateJmxMBean
 */
public class ControlledProcessStateJmx extends NotificationBroadcasterSupport implements ControlledProcessStateJmxMBean {

    private volatile String state = ControlledProcessState.State.STOPPED.toString();
    private final ObjectName objectName;
    private AtomicLong sequence = new AtomicLong(0);

    private ControlledProcessStateJmx(ObjectName objectName) {
        this.objectName = objectName;
    }

    @Override
    public String getProcessState() {
        return state;
    }

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[]{
                new MBeanNotificationInfo(
                        new String[]{AttributeChangeNotification.ATTRIBUTE_CHANGE},
                        AttributeChangeNotification.class.getName(),
                        ControllerLogger.ROOT_LOGGER.processStateChangeNotificationDescription())};
    }

    void setProcessState(ControlledProcessState.State state) {
        final String oldState = this.state;
        final String stateString;
        if (state == ControlledProcessState.State.RUNNING) {
            stateString = "ok";
        } else {
            stateString = state.toString();
        }
        this.state = stateString;


        final String name = "ProcessState";
        AttributeChangeNotification notification = new AttributeChangeNotification(objectName, sequence.getAndIncrement(),
                System.currentTimeMillis(),
                ControllerLogger.ROOT_LOGGER.jmxAttributeChange(name, oldState, stateString),
                "ProcessState", String.class.getName(), oldState, stateString);
        sendNotification(notification);
    }

    public static void registerMBean(ControlledProcessState processState, ProcessType processType) {
        try {
            final ObjectName name = new ObjectName(OBJECT_NAME);
            final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            final ControlledProcessStateJmx mbean = new ControlledProcessStateJmx(name);
            server.registerMBean(mbean, name);

            processState.getService().addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if ("currentState".equals(evt.getPropertyName())) {
                        ControlledProcessState.State newState = (ControlledProcessState.State) evt.getNewValue();
                        mbean.setProcessState(newState);
                        if (processType.isEmbedded() && newState == ControlledProcessState.State.STOPPED) {
                            try {
                                server.unregisterMBean(name);
                            } catch (InstanceNotFoundException | MBeanRegistrationException ignore) {
                            }
                        }
                    }
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
