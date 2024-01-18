/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import static org.wildfly.extension.io.WorkerResourceDefinition.getXnioWorker;

import java.util.Optional;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.extension.io.logging.IOLogger;
import org.xnio.XnioWorker;
import org.xnio.management.XnioServerMXBean;
import org.xnio.management.XnioWorkerMXBean;

/**
 * @author Tomaz Cerar (c) 2016 Red Hat Inc.
 */
public class WorkerServerDefinition extends SimpleResourceDefinition {
    private static final PathElement PATH = PathElement.pathElement("server");
    private static final SimpleAttributeDefinition CONNECTION_COUNT = new SimpleAttributeDefinitionBuilder("connection-count", ModelType.INT)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition CONNECTION_LIMIT_HIGH_MARK = new SimpleAttributeDefinitionBuilder("connection-limit-high-water-mark", ModelType.INT)
            .setStorageRuntime()
            .build();
    private static final SimpleAttributeDefinition CONNECTION_LIMIT_LOW_MARK = new SimpleAttributeDefinitionBuilder("connection-limit-low-water-mark", ModelType.INT)
            .setStorageRuntime()
            .build();

    static final ModelNode NO_METRICS = new ModelNode(IOLogger.ROOT_LOGGER.noMetrics());

    WorkerServerDefinition() {
        super(new Parameters(PATH, IOExtension.RESOLVER.createChildResolver(PathElement.pathElement(WorkerResourceDefinition.PATH.getKey(), PATH.getKey())))
                .setRuntime());
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerMetric(CONNECTION_COUNT, new ServerMetricsHandler() {
            @Override
            ModelNode getMetricValue(XnioServerMXBean metrics) {
                return new ModelNode(metrics.getConnectionCount());
            }
        });
        resourceRegistration.registerReadOnlyAttribute(CONNECTION_LIMIT_HIGH_MARK, new ServerMetricsHandler() {
            @Override
            ModelNode getMetricValue(XnioServerMXBean metrics) {
                return new ModelNode(metrics.getConnectionLimitHighWater());
            }
        });
        resourceRegistration.registerReadOnlyAttribute(CONNECTION_LIMIT_LOW_MARK, new ServerMetricsHandler() {
            @Override
            ModelNode getMetricValue(XnioServerMXBean metrics) {
                return new ModelNode(metrics.getConnectionLimitLowWater());
            }
        });
    }

    private abstract static class ServerMetricsHandler implements OperationStepHandler {

        abstract ModelNode getMetricValue(XnioServerMXBean metrics);

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            XnioWorker worker = getXnioWorker(context);
            if (worker == null || worker.getMXBean() == null) {
                context.getResult().set(NO_METRICS);
                return;
            }
            XnioWorkerMXBean metrics = worker.getMXBean();
            Optional<XnioServerMXBean> serverMetrics = Optional.empty();
            for (XnioServerMXBean xnioServerMXBean : metrics.getServerMXBeans()) {
                if (xnioServerMXBean.getBindAddress().equals(context.getCurrentAddressValue())) {
                    serverMetrics = Optional.of(xnioServerMXBean);
                    break;
                }
            }
            if (serverMetrics.isPresent()) {
                context.getResult().set(getMetricValue(serverMetrics.get()));
            } else {
                context.getResult().set(NO_METRICS);
            }
        }
    }
}
