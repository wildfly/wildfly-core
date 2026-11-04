/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
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
 * Handles read-resource for the resource representing {@link java.lang.management.ThreadMXBean}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ThreadMXBeanReadResourceHandler extends AbstractPlatformMBeanReadResourceHandler<ThreadMXBean> {

    ThreadMXBeanReadResourceHandler(List<AccessConstraintDefinition> resourceConstaints) {
        super(resourceConstaints);
    }

    @Override
    ThreadMXBean getPlatformMBean() {
        return ManagementFactory.getThreadMXBean();
    }

    @Override
    void executeAttributeReads(OperationContext context, ModelNode operation, Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> metrics, Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> otherAttributes, FilteredData filteredData, ThreadMXBean mbean) throws OperationFailedException {
        for (String attribute : ThreadResourceDefinition.THREADING_READ_ATTRIBUTES) {
            executeAttributeRead(context, operation, attribute, ThreadMXBeanAttributeHandler::getResult,
                    mbean, otherAttributes, filteredData);
        }

        for (String attribute : ThreadResourceDefinition.THREADING_READ_WRITE_ATTRIBUTES) {
            executeAttributeRead(context, operation, attribute, ThreadMXBeanAttributeHandler::getResult,
                    mbean, otherAttributes, filteredData);
        }

        for (String attribute : ThreadResourceDefinition.THREADING_EXTENDED_READ_ATTRIBUTES) {
            executeAttributeRead(context, operation, attribute, ThreadMXBeanAttributeHandler::getExtendedResult,
                    mbean, otherAttributes, filteredData);
        }

        for (String attribute : ThreadResourceDefinition.THREADING_EXTENDED_READ_WRITE_ATTRIBUTES) {
            executeAttributeRead(context, operation, attribute, ThreadMXBeanAttributeHandler::getExtendedResult,
                    mbean, otherAttributes, filteredData);
        }

        for (String attribute : ThreadResourceDefinition.THREADING_METRICS) {
            executeAttributeRead(context, operation, attribute, ThreadMXBeanAttributeHandler::getResult,
                    mbean, metrics, filteredData);
        }

        for (String attribute : ThreadResourceDefinition.THREADING_EXTENDED_METRICS) {
            executeAttributeRead(context, operation, attribute, ThreadMXBeanAttributeHandler::getExtendedResult,
                    mbean, metrics, filteredData);
        }

        executeAttributeRead(context, operation, PlatformMBeanConstants.OBJECT_NAME.getName(),
                ThreadMXBeanAttributeHandler::getResult, mbean, otherAttributes, filteredData);
    }
}
