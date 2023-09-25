/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.descriptions;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPRECATED;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Resource resolver that in case that doesn't find deprecated description uses subsystem-name.deprecated key.
 * This is useful when you need to deprecate whole subsystem and don't want to add deprecated key entries for each any every resource / attribute / operation
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public class DeprecatedResourceDescriptionResolver extends StandardResourceDescriptionResolver {
    private final String DEPRECATED_KEY;

    public DeprecatedResourceDescriptionResolver(String subsystemName, String keyPrefix, String bundleBaseName, ClassLoader bundleLoader) {
        super(keyPrefix, bundleBaseName, bundleLoader);
        this.DEPRECATED_KEY = subsystemName +"." + DEPRECATED;
    }

    public DeprecatedResourceDescriptionResolver(String subsystemName, String keyPrefix, String bundleBaseName, ClassLoader bundleLoader, boolean reuseAttributesForAdd, boolean useUnprefixedChildTypes) {
        super(keyPrefix, bundleBaseName, bundleLoader, reuseAttributesForAdd, useUnprefixedChildTypes);
        this.DEPRECATED_KEY = subsystemName +"." + DEPRECATED;
    }

    @Override
    public String getOperationDeprecatedDescription(String operationName, Locale locale, ResourceBundle bundle) {
        if (bundle.containsKey(getBundleKey(operationName, DEPRECATED))) {
            return super.getOperationDeprecatedDescription(operationName, locale, bundle);
        }
        return bundle.getString(DEPRECATED_KEY);
    }

    @Override
    public String getOperationParameterDeprecatedDescription(String operationName, String paramName, Locale locale, ResourceBundle bundle) {
        if (bundle.containsKey(getBundleKey(paramName, DEPRECATED))) {
            return super.getOperationParameterDeprecatedDescription(operationName, paramName, locale, bundle);
        }
        return bundle.getString(DEPRECATED_KEY);
    }

    @Override
    public String getResourceDeprecatedDescription(Locale locale, ResourceBundle bundle) {
        if (bundle.containsKey(getBundleKey(DEPRECATED))) {
            return super.getResourceDeprecatedDescription(locale, bundle);
        }
        return bundle.getString(DEPRECATED_KEY);
    }

    @Override
    public String getResourceAttributeDeprecatedDescription(String attributeName, Locale locale, ResourceBundle bundle) {
        if (bundle.containsKey(getBundleKey(attributeName, DEPRECATED))) {
            return super.getResourceAttributeDeprecatedDescription(attributeName, locale, bundle);
        }
        return bundle.getString(DEPRECATED_KEY);
    }
}
