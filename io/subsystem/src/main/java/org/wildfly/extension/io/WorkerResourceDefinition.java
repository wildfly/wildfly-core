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

package org.wildfly.extension.io;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.DelegatingResource;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.registry.ResourceProvider;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.wildfly.extension.io.logging.IOLogger;
import org.xnio.Option;
import org.xnio.Options;
import org.xnio.XnioWorker;
import org.xnio.management.XnioWorkerMXBean;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 */
class WorkerResourceDefinition extends PersistentResourceDefinition {

    static final RuntimeCapability<Void> IO_WORKER_RUNTIME_CAPABILITY =
            RuntimeCapability.Builder.of(IOServices.IO_WORKER_CAPABILITY_NAME, true, XnioWorker.class).build();

    static final OptionAttributeDefinition WORKER_TASK_MAX_THREADS = new OptionAttributeDefinition.Builder(Constants.WORKER_TASK_MAX_THREADS, Options.WORKER_TASK_MAX_THREADS)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setAllowExpression(true)
            .build();
    static final OptionAttributeDefinition WORKER_TASK_KEEPALIVE = new OptionAttributeDefinition.Builder(Constants.WORKER_TASK_KEEPALIVE, Options.WORKER_TASK_KEEPALIVE)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setDefaultValue(new ModelNode(60_000))
            .setAllowExpression(true)
            .build();
    static final OptionAttributeDefinition STACK_SIZE = new OptionAttributeDefinition.Builder(Constants.STACK_SIZE, Options.STACK_SIZE)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setDefaultValue(new ModelNode(0L))
            .setAllowExpression(true)
            .build();
    static final OptionAttributeDefinition WORKER_IO_THREADS = new OptionAttributeDefinition.Builder(Constants.WORKER_IO_THREADS, Options.WORKER_IO_THREADS)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setAllowExpression(true)
            .build();

    static final OptionAttributeDefinition[] ATTRIBUTES = new OptionAttributeDefinition[]{
            WORKER_IO_THREADS,
            WORKER_TASK_KEEPALIVE,
            WORKER_TASK_MAX_THREADS,
            STACK_SIZE
    };

    private static final AttributeDefinition SHUTDOWN_REQUESTED = new SimpleAttributeDefinitionBuilder("shutdown-requested", ModelType.BOOLEAN).setStorageRuntime()
            .setUndefinedMetricValue(new ModelNode(false)).build();
    private static final AttributeDefinition CORE_WORKER_POOL_SIZE = new SimpleAttributeDefinitionBuilder("core-pool-size", ModelType.INT).setStorageRuntime()
            .setUndefinedMetricValue(new ModelNode(0)).build();
    private static final AttributeDefinition MAX_WORKER_POOL_SIZE = new SimpleAttributeDefinitionBuilder("max-pool-size", ModelType.INT).setStorageRuntime()
            .setUndefinedMetricValue(new ModelNode(0)).build();
    private static final AttributeDefinition IO_THREAD_COUNT = new SimpleAttributeDefinitionBuilder("io-thread-count", ModelType.INT).setStorageRuntime()
            .setUndefinedMetricValue(new ModelNode(0)).build();
    private static final AttributeDefinition QUEUE_SIZE = new SimpleAttributeDefinitionBuilder("queue-size", ModelType.INT).setStorageRuntime()
            .setUndefinedMetricValue(new ModelNode(0)).build();


    static final Map<String, OptionAttributeDefinition> ATTRIBUTES_BY_XMLNAME;

    static {
        Map<String, OptionAttributeDefinition> attrs = new HashMap<>();
        for (AttributeDefinition attr : ATTRIBUTES) {
            attrs.put(attr.getXmlName(), (OptionAttributeDefinition) attr);
        }
        ATTRIBUTES_BY_XMLNAME = Collections.unmodifiableMap(attrs);
    }


    static final WorkerResourceDefinition INSTANCE = new WorkerResourceDefinition();


    private WorkerResourceDefinition() {
        super(IOExtension.WORKER_PATH,
                IOExtension.getResolver(Constants.WORKER),
                WorkerAdd.INSTANCE,
                new ReloadRequiredRemoveStepHandler()
        );
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return (Collection) ATTRIBUTES_BY_XMLNAME.values();
    }

    @Override
    public void registerCapabilities(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerCapability(IO_WORKER_RUNTIME_CAPABILITY);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(WORKER_TASK_MAX_THREADS,
                new WorkerReadAttributeHandler(WORKER_TASK_MAX_THREADS.getOption()),
                new WorkerWriteAttributeHandler() {
                    @Override
                    boolean setValue(XnioWorker worker, ModelNode value) throws IOException {
                        return worker.setOption(Options.WORKER_TASK_MAX_THREADS, value.asInt()) == null;
                    }
                });
        resourceRegistration.registerReadWriteAttribute(WORKER_TASK_KEEPALIVE,
                new WorkerReadAttributeHandler(WORKER_TASK_KEEPALIVE.getOption()),
                new WorkerWriteAttributeHandler() {
                    @Override
                    boolean setValue(XnioWorker worker, ModelNode value) throws IOException {
                        return worker.setOption(Options.WORKER_TASK_KEEPALIVE, value.asInt()) == null;
                    }
                });

        resourceRegistration.registerReadWriteAttribute(STACK_SIZE,
                new WorkerReadAttributeHandler(STACK_SIZE.getOption()),
                new WorkerWriteAttributeHandler() {
                    @Override
                    boolean setValue(XnioWorker worker, ModelNode value) throws IOException {
                        return worker.setOption(Options.STACK_SIZE, value.asLong()) == null;
                    }
                });

        resourceRegistration.registerReadWriteAttribute(WORKER_IO_THREADS,
                new WorkerReadAttributeHandler(STACK_SIZE.getOption()),
                new WorkerWriteAttributeHandler() {
                    @Override
                    boolean setValue(XnioWorker worker, ModelNode value) throws IOException {
                        return worker.setOption(Options.WORKER_IO_THREADS, value.asInt()) == null;
                    }
                });

        WorkerMetricsHandler metricsHandler = new WorkerMetricsHandler();
        resourceRegistration.registerMetric(SHUTDOWN_REQUESTED, metricsHandler);
        resourceRegistration.registerMetric(CORE_WORKER_POOL_SIZE, metricsHandler);
        resourceRegistration.registerMetric(MAX_WORKER_POOL_SIZE, metricsHandler);
        resourceRegistration.registerMetric(IO_THREAD_COUNT, metricsHandler);
        resourceRegistration.registerMetric(QUEUE_SIZE, metricsHandler);
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        super.registerChildren(resourceRegistration);
        resourceRegistration.registerSubModel(new WorkerServerDefinition());
        resourceRegistration.registerSubModel(OutboundBindAddressResourceDefinition.getInstance());
    }

    private abstract static class AbstractWorkerAttributeHandler implements OperationStepHandler {
        private static void populateValueFromModel(OperationContext context, ModelNode operation) {
            final ModelNode subModel = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
            ModelNode result = subModel.get(operation.require(ModelDescriptionConstants.NAME).asString());
            context.getResult().set(result);
        }

        public void execute(OperationContext outContext, ModelNode operation) throws OperationFailedException {
            populateValueFromModel(outContext, operation);
            outContext.addStep((context, op) -> {
                XnioWorker worker = getXnioWorker(context);
                if (worker != null) {
                    executeWithWorker(context, op, worker);
                }
            }, OperationContext.Stage.RUNTIME);
        }
         abstract void executeWithWorker(OperationContext context, ModelNode operation, XnioWorker worker) throws OperationFailedException;
    }

    private abstract static class WorkerWriteAttributeHandler extends AbstractWriteAttributeHandler {

        @Override
        protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode value, ModelNode currentValue, HandbackHolder handbackHolder) throws OperationFailedException {
            XnioWorker worker = getXnioWorker(context);
            if (worker == null) { //worker can be null if it is not started yet, it can happen when there are no dependencies to it.
                return true;
            }
            try {
                return setValue(worker, value);
            } catch (IOException e) {
                throw new OperationFailedException(e);
            }
        }

        @Override
        protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Object handback) throws OperationFailedException {
            XnioWorker worker = getXnioWorker(context);
            if (worker == null) { //worker can be null if it is not started yet, it can happen when there are no dependencies to it.
                return;
            }
            try {
                setValue(worker, valueToRestore);
            } catch (IOException e) {
                throw new OperationFailedException(e);
            }
        }

        /**
         *
         * @return returns true if it requires reload
         */
        abstract boolean setValue(XnioWorker worker, ModelNode value) throws IOException;

    }

    private static class WorkerReadAttributeHandler extends AbstractWorkerAttributeHandler {
        final Option<?> option;

        public WorkerReadAttributeHandler(Option<?> option) {
            this.option = option;
        }

        @Override
        void executeWithWorker(OperationContext context, ModelNode operation, XnioWorker worker) throws OperationFailedException {
            try {
                Object result = worker.getOption(option);
                if (result!=null){
                    context.getResult().set(new ModelNode(result.toString()));
                }
            } catch (IOException e) {
                throw new OperationFailedException(e);
            }
        }
    }

    private static class WorkerMetricsHandler extends AbstractWorkerAttributeHandler{

        @Override
        void executeWithWorker(OperationContext context, ModelNode operation, XnioWorker worker) throws OperationFailedException {
            XnioWorkerMXBean metrics = worker.getMXBean();
            String name = operation.require(ModelDescriptionConstants.NAME).asString();
            context.getResult().set(getMetricValue(name, metrics));
        }
    }

    static XnioWorker getXnioWorker(OperationContext context) {
        String name = context.getCurrentAddressValue();
        if (!context.getCurrentAddress().getLastElement().getKey().equals(IOExtension.WORKER_PATH.getKey())) { //we are somewhere deeper, lets find worker name
            for (PathElement pe : context.getCurrentAddress()) {
                if (pe.getKey().equals(IOExtension.WORKER_PATH.getKey())) {
                    name = pe.getValue();
                    break;
                }
            }
        }
        return getXnioWorker(context.getServiceRegistry(false), name);
    }

    static XnioWorker getXnioWorker(ServiceRegistry serviceRegistry, String name) {
        ServiceName serviceName = IO_WORKER_RUNTIME_CAPABILITY.getCapabilityServiceName(name, XnioWorker.class);
        ServiceController<XnioWorker> controller = (ServiceController<XnioWorker>) serviceRegistry.getService(serviceName);
        if (controller == null || controller.getState() != ServiceController.State.UP) {
            return null;
        }
        return controller.getValue();
    }

    private static XnioWorkerMXBean getMetrics(ServiceRegistry serviceRegistry, String name) {
        XnioWorker worker = getXnioWorker(serviceRegistry, name);
        if (worker != null && worker.getMXBean() != null) {
            return worker.getMXBean();
        }
        return null;
    }


    private static ModelNode getMetricValue(String attributeName, XnioWorkerMXBean metric) throws OperationFailedException {
        if (SHUTDOWN_REQUESTED.getName().equals(attributeName)) {
            return new ModelNode(metric.isShutdownRequested());
        } else if (CORE_WORKER_POOL_SIZE.getName().equals(attributeName)) {
            return new ModelNode(metric.getCoreWorkerPoolSize());
        } else if (MAX_WORKER_POOL_SIZE.getName().equals(attributeName)) {
            return new ModelNode(metric.getMaxWorkerPoolSize());
        } else if (IO_THREAD_COUNT.getName().equals(attributeName)) {
            return new ModelNode(metric.getIoThreadCount());
        } else if (QUEUE_SIZE.getName().equals(attributeName)) {
            return new ModelNode(metric.getWorkerQueueSize());
        } else {
            throw new OperationFailedException(IOLogger.ROOT_LOGGER.noMetrics());
        }
    }

    static class WorkerResource extends DelegatingResource {
        private final ServiceRegistry serviceRegistry;
        private final PathAddress pathAddress;

        public WorkerResource(OperationContext context) {
            super(Resource.Factory.create());
            this.serviceRegistry = context.getServiceRegistry(false);
            this.pathAddress = context.getCurrentAddress();
            super.registerResourceProvider("server", new ResourceProvider() {
                @Override
                public boolean has(String name) {
                    return children().contains(name);
                }

                @Override
                public Resource get(String name) {
                    return PlaceholderResource.INSTANCE;
                }

                @Override
                public boolean hasChildren() {
                    return false;
                }

                @Override
                public Set<String> children() {
                    XnioWorkerMXBean metrics = getMetrics(serviceRegistry, pathAddress.getLastElement().getValue());
                    if (metrics == null) {
                        return Collections.emptySet();
                    }
                    Set<String> res = new LinkedHashSet<>();
                    metrics.getServerMXBeans().forEach(serverMXBean -> res.add(serverMXBean.getBindAddress()));
                    return res;
                }

                @Override
                public void register(String name, Resource resource) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void register(String value, int index, Resource resource) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Resource remove(String name) {
                    return null;
                }

                @Override
                public ResourceProvider clone() {
                    return this;
                }
            });
        }

        @Override
        public Set<String> getChildTypes() {
            return Collections.singleton("server");
        }

    }
}
