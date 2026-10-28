/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.operations.global.FilteredData;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.dmr.ModelNode;

/**
 * Handles read-resource for the resource representing {@link java.lang.management.RuntimeMXBean}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class RuntimeMXBeanReadResourceHandler extends AbstractPlatformMBeanReadResourceHandler<RuntimeMXBean> {

    RuntimeMXBeanReadResourceHandler(List<AccessConstraintDefinition> resourceConstaints) {
        super(resourceConstaints);
    }

    @Override
    RuntimeMXBean getPlatformMBean() {
        return ManagementFactory.getRuntimeMXBean();
    }

    @Override
    void executeAttributeReads(OperationContext context, ModelNode operation, Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> metrics, Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> otherAttributes, FilteredData filteredData, RuntimeMXBean mbean) throws OperationFailedException {


        for (String attribute : RuntimeResourceDefinition.RUNTIME_READ_ATTRIBUTES) {
            executeAttributeRead(context, operation, attribute, RuntimeMXBeanAttributeHandler::getResult,
                    mbean, otherAttributes, filteredData);
        }

        for (String attribute : RuntimeResourceDefinition.RUNTIME_METRICS) {
            executeAttributeRead(context, operation, attribute, RuntimeMXBeanAttributeHandler::getResult,
                    mbean, metrics, filteredData);
        }

        executeAttributeRead(context, operation, PlatformMBeanConstants.OBJECT_NAME.getName(),
                RuntimeMXBeanAttributeHandler::getResult, mbean, otherAttributes, filteredData);
    }
}
