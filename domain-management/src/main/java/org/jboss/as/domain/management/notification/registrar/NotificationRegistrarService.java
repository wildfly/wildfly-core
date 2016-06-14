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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.notification.NotificationFilter;
import org.jboss.as.controller.notification.NotificationHandler;
import org.jboss.as.controller.notification.NotificationRegistrar;
import org.jboss.as.controller.notification.NotificationRegistrarContext;
import org.jboss.as.controller.notification.NotificationRegistry;
import org.jboss.as.controller.registry.NotificationHandlerRegistration;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * @author Kabir Khan
 */
class NotificationRegistrarService implements Service<Void> {
    private final InjectedValue<ModelController> controllerValue = new InjectedValue<>();
    private final InjectedValue<ExecutorService> executorServiceValue = new InjectedValue<>();

    private final String name;
    private final String className;
    private final ModuleIdentifier moduleIdentifier;
    private final ProcessType processType;
    private final RunningMode runningMode;
    private final Map<String, String> properties;

    private volatile NotificationRegistrar notificationRegistrar;
    private volatile TrackingNotificationRegistry notificationRegistry;

    private NotificationRegistrarService(final ServiceTarget serviceTarget, final String name, final String className,
                                         final ModuleIdentifier moduleIdentifier, final ProcessType processType,
                                         final RunningMode runningMode, final Map<String, String> properties) {
        this.name = name;
        this.className = className;
        this.moduleIdentifier = moduleIdentifier;
        this.processType = processType;
        this.runningMode = runningMode;
        this.properties = Collections.unmodifiableMap(properties);
    }

    static void install(final ServiceTarget serviceTarget, final ServiceName serviceName, final String className,
                        final ModuleIdentifier moduleIdentifier, final ProcessType processType,
                        final RunningMode runningMode, final Map<String, String> properties) {

        final NotificationRegistrarService service =
                new NotificationRegistrarService(serviceTarget, serviceName.getSimpleName(), className,
                        moduleIdentifier, processType, runningMode, properties);

        final ServiceName serverControllerName = processType.isServer() ?
                ServiceName.JBOSS.append("as", "server-controller") :
                ServiceName.JBOSS.append("host", "controller", "model", "controller");

        final ServiceName executorName = processType.isServer() ?
                ServiceName.JBOSS.append("as", "server-executor") :
                ServiceName.JBOSS.append("host", "controller", "executor");


        serviceTarget.addService(serviceName, service)
                .setInitialMode(ServiceController.Mode.ACTIVE)
                .addDependency(serverControllerName, ModelController.class, service.controllerValue)
                .addDependency(executorName, ExecutorService.class, service.executorServiceValue)
                .install();
    }

    @Override
    public void start(StartContext context) throws StartException {
        if(getClass().getClassLoader() instanceof ModuleClassLoader) {
            final ModelController controller = controllerValue.getValue();
            final ExecutorService executorService = executorServiceValue.getValue();
            final ModelControllerClient client = controller.createClient(executorService);

            final ModuleLoader moduleLoader = ModuleLoader.forClassLoader(getClass().getClassLoader());
            try {
                final Module module = moduleLoader.loadModule(moduleIdentifier);
                final Class<?> clazz = module.getClassLoader().loadClass(className);
                notificationRegistrar = (NotificationRegistrar)clazz.newInstance();


            } catch (Exception e) {
                throw new StartException(e);
            }

            notificationRegistry = new TrackingNotificationRegistry(controller.getNotificationRegistry());

            NotificationRegistrarContext nrc = new NotificationRegistrarContextImpl(
                    name, notificationRegistry, client, processType, runningMode, properties);

            notificationRegistrar.registerNotificationListeners(nrc);

        } else {
            //We are a unit test, ignore this stuff for now
        }
    }

    @Override
    public void stop(StopContext context) {
        if (notificationRegistry != null) {
            notificationRegistry.unregisterAll();
        }
        if (notificationRegistrar != null) {
            notificationRegistrar.cleanup();
        }
    }

    @Override
    public Void getValue() throws IllegalStateException, IllegalArgumentException {
        return null;
    }

    private static class TrackingNotificationRegistry implements NotificationRegistry {

        private final NotificationHandlerRegistration notificationRegistry;
        private volatile List<NotificationHandlerEntry> handlerEntries = Collections.synchronizedList(new ArrayList<>());

        public TrackingNotificationRegistry(NotificationHandlerRegistration notificationRegistry) {

            this.notificationRegistry = notificationRegistry;
        }

        @Override
        public void registerNotificationHandler(PathAddress source, NotificationHandler handler, NotificationFilter filter) {
            notificationRegistry.registerNotificationHandler(source, handler, filter);
            handlerEntries.add(new NotificationHandlerEntry(source, handler, filter));
        }

        void unregisterAll() {
            for (NotificationHandlerEntry entry : handlerEntries) {
                notificationRegistry.unregisterNotificationHandler(entry.getPathAddress(), entry.getHandler(), entry.getFilter());
            }
        }
    }

    private static class NotificationHandlerEntry {
        private final PathAddress pathAddress;
        private final NotificationHandler handler;
        private final NotificationFilter filter;

        public NotificationHandlerEntry(PathAddress pathAddress, NotificationHandler handler, NotificationFilter filter) {
            this.pathAddress = pathAddress;
            this.handler = handler;
            this.filter = filter;
        }

        public PathAddress getPathAddress() {
            return pathAddress;
        }

        public NotificationHandler getHandler() {
            return handler;
        }

        public NotificationFilter getFilter() {
            return filter;
        }
    }
}
