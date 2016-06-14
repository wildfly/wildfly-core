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

package org.jboss.as.controller.notification;

import java.util.Map;

import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * Encapsulation of data to register notifications via the {@link NotificationRegistrar}
 *
 * @author Kabir Khan
 */
public interface NotificationRegistrarContext {
    /**
     * Get the name of this registrar
     *
     * @return the name
     */
    String getName();
    /**
     * Get the notification registry into which notification handlers will be installed
     *
     * @return the notification registry
     */
    NotificationRegistry getNotificationRegistry();

    /**
     * Get the process type of the process
     *
     * @return the process type
     */
    ProcessType getProcessType();

    /**
     * Get the running mode of the process
     *
     * @return the running mode
     */
    RunningMode getRunningMode();

    /**
     * Get the properties for configuring the notification handler(s)
     *
     * @return the properties
     */
    Map<String, String> getProperties();

    /**
     * Get the model controller client for the process
     *
     * @return the client
     */
    ModelControllerClient getModelControllerClient();

}
