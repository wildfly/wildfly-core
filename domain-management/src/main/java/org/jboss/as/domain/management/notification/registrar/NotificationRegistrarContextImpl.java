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

package org.jboss.as.domain.management.notification.registrar;

import java.util.Map;

import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.notification.NotificationRegistrarContext;
import org.jboss.as.controller.notification.NotificationRegistry;

/**
 * @author Kabir Khan
 */
class NotificationRegistrarContextImpl implements NotificationRegistrarContext {
    private final String name;
    private final NotificationRegistry registry;
    private final ModelControllerClient modelControllerClient;
    private final ProcessType processType;
    private final RunningMode runningMode;
    private final Map<String, String> properties;

    NotificationRegistrarContextImpl(String name,
                                     NotificationRegistry registry,
                                     ModelControllerClient modelControllerClient,
                                     ProcessType processType,
                                     RunningMode runningMode,
                                     Map<String, String> properties) {
        this.name = name;
        this.registry = registry;
        this.modelControllerClient = modelControllerClient;
        this.processType = processType;
        this.runningMode = runningMode;
        this.properties = properties;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public NotificationRegistry getNotificationRegistry() {
        return registry;
    }

    @Override
    public ProcessType getProcessType() {
        return processType;
    }

    @Override
    public RunningMode getRunningMode() {
        return runningMode;
    }

    @Override
    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public ModelControllerClient getModelControllerClient() {
        return modelControllerClient;
    }
}
