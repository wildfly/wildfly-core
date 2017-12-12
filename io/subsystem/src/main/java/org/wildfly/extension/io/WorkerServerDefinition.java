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
import org.xnio.XnioWorker;
import org.xnio.management.XnioServerMXBean;
import org.xnio.management.XnioWorkerMXBean;

/**
 * @author Tomaz Cerar (c) 2016 Red Hat Inc.
 */
public class WorkerServerDefinition extends SimpleResourceDefinition {
    private static final SimpleAttributeDefinition CONNECTION_COUNT = new SimpleAttributeDefinitionBuilder("connection-count", ModelType.INT)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition CONNECTION_LIMIT_HIGH_MARK = new SimpleAttributeDefinitionBuilder("connection-limit-high-water-mark", ModelType.INT)
            .setStorageRuntime()
            .build();
    private static final SimpleAttributeDefinition CONNECTION_LIMIT_LOW_MARK = new SimpleAttributeDefinitionBuilder("connection-limit-low-water-mark", ModelType.INT)
            .setStorageRuntime()
            .build();


    WorkerServerDefinition() {
        super(new Parameters(PathElement.pathElement("server"), IOExtension.getResolver("worker","server"))
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
                context.getResult().set(IOExtension.NO_METRICS);
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
                context.getResult().set(IOExtension.NO_METRICS);
            }
        }
    }


}
