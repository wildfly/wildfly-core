/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;

/**
 * {@link StandardResourceDescriptionResolver} variant that reuses a set of common attribute descriptions
 * for various pool resource types.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class ThreadPoolResourceDescriptionResolver extends StandardResourceDescriptionResolver {

    private static final Set<String> COMMON_ATTRIBUTE_NAMES;

    static {
        COMMON_ATTRIBUTE_NAMES = new HashSet<>(Arrays.asList(PoolAttributeDefinitions.NAME.getName(),
                PoolAttributeDefinitions.ALLOW_CORE_TIMEOUT.getName(), PoolAttributeDefinitions.CORE_THREADS.getName(),
                PoolAttributeDefinitions.HANDOFF_EXECUTOR.getName(), PoolAttributeDefinitions.KEEPALIVE_TIME.getName(),
                PoolAttributeDefinitions.MAX_THREADS.getName(), PoolAttributeDefinitions.QUEUE_LENGTH.getName(),
                PoolAttributeDefinitions.THREAD_FACTORY.getName(), PoolAttributeDefinitions.ACTIVE_COUNT.getName(),
                PoolAttributeDefinitions.COMPLETED_TASK_COUNT.getName(), PoolAttributeDefinitions.CURRENT_THREAD_COUNT.getName(),
                PoolAttributeDefinitions.LARGEST_THREAD_COUNT.getName(), PoolAttributeDefinitions.TASK_COUNT.getName(),
                PoolAttributeDefinitions.QUEUE_SIZE.getName()));

        // note we don't include REJECTED_COUNT as it has a different definition in different resources
    }

    private static final String COMMON_PREFIX = "threadpool.common";

    ThreadPoolResourceDescriptionResolver(final String keyPrefix, final String bundleBaseName, final ClassLoader bundleLoader) {
        super(keyPrefix, bundleBaseName, bundleLoader, true, true);
    }

    @Override
    public String getResourceAttributeDescription(String attributeName, Locale locale, ResourceBundle bundle) {
        if (COMMON_ATTRIBUTE_NAMES.contains(attributeName)) {
            return bundle.getString(getKey(attributeName));
        }
        return super.getResourceAttributeDescription(attributeName, locale, bundle);
    }

    @Override
    public String getResourceAttributeValueTypeDescription(String attributeName, Locale locale, ResourceBundle bundle, String... suffixes) {
        if (COMMON_ATTRIBUTE_NAMES.contains(attributeName)) {
            return bundle.getString(getVariableBundleKey(COMMON_PREFIX, new String[] {attributeName}, suffixes));
        }
        return super.getResourceAttributeValueTypeDescription(attributeName, locale, bundle, suffixes);
    }

    @Override
    public String getOperationParameterDescription(String operationName, String paramName, Locale locale, ResourceBundle bundle) {
        if (ModelDescriptionConstants.ADD.equals(operationName) && COMMON_ATTRIBUTE_NAMES.contains(paramName)) {
            return bundle.getString(getKey(paramName));
        }
        return super.getOperationParameterDescription(operationName, paramName, locale, bundle);
    }

    @Override
    public String getOperationParameterValueTypeDescription(String operationName, String paramName, Locale locale, ResourceBundle bundle, String... suffixes) {
        if (ModelDescriptionConstants.ADD.equals(operationName) && COMMON_ATTRIBUTE_NAMES.contains(paramName)) {
            return bundle.getString(getVariableBundleKey(COMMON_PREFIX, new String[] {paramName}, suffixes));
        }
        return super.getOperationParameterValueTypeDescription(operationName, paramName, locale, bundle, suffixes);
    }


    private String getKey(String... args) {
        return getVariableBundleKey(COMMON_PREFIX, args);
    }
}
