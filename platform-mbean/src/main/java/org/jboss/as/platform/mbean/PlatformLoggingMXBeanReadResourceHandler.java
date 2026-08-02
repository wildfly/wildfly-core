/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;
import java.lang.management.PlatformLoggingMXBean;
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
 * Handles read-resource for the resource representing {@link java.lang.management.PlatformLoggingMXBean}.
 *
 */
public class PlatformLoggingMXBeanReadResourceHandler extends AbstractPlatformMBeanReadResourceHandler<PlatformLoggingMXBean> {

    PlatformLoggingMXBeanReadResourceHandler(List<AccessConstraintDefinition> resourceConstaints) {
        super(resourceConstaints);
    }

    @Override
    PlatformLoggingMXBean getPlatformMBean() {
        return ManagementFactory.getPlatformMXBean(PlatformLoggingMXBean.class);
    }

    @Override
    void executeAttributeReads(OperationContext context, ModelNode operation, Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> metrics, Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> otherAttributes, FilteredData filteredData, PlatformLoggingMXBean mbean) throws OperationFailedException {


        for (String attribute : PlatformLoggingResourceDefinition.PLATFORM_LOGGING_READ_ATTRIBUTES) {
            executeAttributeRead(context, operation, attribute, PlatformLoggingMXBeanAttributeHandler::getResult,
                    mbean, otherAttributes, filteredData);
        }

        executeAttributeRead(context, operation, PlatformMBeanConstants.OBJECT_NAME.getName(),
                PlatformLoggingMXBeanAttributeHandler::getResult, mbean, otherAttributes, filteredData);
    }
}
