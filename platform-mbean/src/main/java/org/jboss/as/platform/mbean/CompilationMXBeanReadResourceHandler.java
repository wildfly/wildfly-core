/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.operations.global.FilteredData;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.dmr.ModelNode;

/**
 * Handles {@code read-resource} for the resource representing {@link java.lang.management.CompilationMXBean}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class CompilationMXBeanReadResourceHandler extends AbstractPlatformMBeanReadResourceHandler<CompilationMXBean> {

    CompilationMXBeanReadResourceHandler(List<AccessConstraintDefinition> resourceConstaints) {
        super(resourceConstaints);
    }

    @Override
    CompilationMXBean getPlatformMBean() {
        return ManagementFactory.getCompilationMXBean();
    }

    @Override
    void executeAttributeReads(OperationContext context,
                               ModelNode operation,
                               Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> metrics,
                               Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> otherAttributes,
                               FilteredData filteredData,
                               CompilationMXBean mbean) {


        for (String attribute : CompilationResourceDefinition.COMPILATION_READ_ATTRIBUTES) {
            executeAttributeRead(context, operation, attribute, CompilationMXBeanAttributeHandler::getResult,
                    mbean, otherAttributes, filteredData);
        }

        for (String attribute : CompilationResourceDefinition.COMPILATION_METRICS) {
            executeAttributeRead(context, operation, attribute, CompilationMXBeanAttributeHandler::getResult,
                    mbean, metrics, filteredData);
        }

        executeAttributeRead(context, operation, PlatformMBeanConstants.OBJECT_NAME.getName(),
                CompilationMXBeanAttributeHandler::getResult, mbean, otherAttributes, filteredData);
    }
}
