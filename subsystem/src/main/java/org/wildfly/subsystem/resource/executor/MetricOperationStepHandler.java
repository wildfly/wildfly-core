/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.executor;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.AbstractRuntimeOnlyHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;

/**
 * Generic {@link org.jboss.as.controller.OperationStepHandler} for runtime metrics.
 * @author Paul Ferraro
 */
public class MetricOperationStepHandler<C> extends AbstractRuntimeOnlyHandler implements ManagementResourceRegistrar {

    private final Map<String, Metric<C>> metrics = new HashMap<>();
    private final MetricExecutor<C> executor;

    public <M extends Enum<M> & Metric<C>> MetricOperationStepHandler(MetricExecutor<C> executor, Class<M> metricClass) {
        this(executor, EnumSet.allOf(metricClass));
    }

    public MetricOperationStepHandler(MetricExecutor<C> executor, Iterable<? extends Metric<C>> metrics) {
        this.executor = executor;
        for (Metric<C> metric : metrics) {
            this.metrics.put(metric.getName(), metric);
        }
    }

    @Override
    public void register(ManagementResourceRegistration registration) {
        for (Metric<C> metric : this.metrics.values()) {
            registration.registerMetric(metric.get(), this);
        }
    }

    @Override
    protected void executeRuntimeStep(OperationContext context, ModelNode operation) {
        String name = operation.get(ModelDescriptionConstants.NAME).asString();
        Metric<C> metric = this.metrics.get(name);
        try {
            ModelNode result = this.executor.execute(context, metric);
            if (result != null) {
                context.getResult().set(result);
            }
        } catch (OperationFailedException e) {
            context.getFailureDescription().set(e.getLocalizedMessage());
        }
        context.completeStep(OperationContext.ResultHandler.NOOP_RESULT_HANDLER);
    }
}
