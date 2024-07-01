/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.descriptions;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Resource description resolver that does no resolving at all.
 *
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 */
public class NonResolvingResourceDescriptionResolver extends StandardResourceDescriptionResolver {
    public static final NonResolvingResourceDescriptionResolver INSTANCE = new NonResolvingResourceDescriptionResolver();

    /**
     * No-arg constructor.
     * @deprecated use {@link #INSTANCE} instead
     */
    @Deprecated(forRemoval = true)
    public NonResolvingResourceDescriptionResolver() {
        super("", "", NonResolvingResourceDescriptionResolver.class.getClassLoader());
    }

    @Override
    public ResourceBundle getResourceBundle(Locale locale) {
        return EmptyResourceBundle.INSTANCE;
    }

    @Override
    public String getResourceDescription(Locale locale, ResourceBundle bundle) {
        return "description";
    }

    @Override
    public String getResourceAttributeDescription(String attributeName, Locale locale, ResourceBundle bundle) {
        return attributeName;
    }

    @Override
    public String getResourceAttributeValueTypeDescription(String attributeName, Locale locale, ResourceBundle bundle, String... suffixes) {
        return attributeName;
    }

    @Override
    public String getOperationDescription(String operationName, Locale locale, ResourceBundle bundle) {
        return operationName;
    }

    @Override
    public String getOperationParameterDescription(String operationName, String paramName, Locale locale, ResourceBundle bundle) {
        return operationName + "-" + paramName;
    }

    @Override
    public String getOperationParameterValueTypeDescription(String operationName, String paramName, Locale locale, ResourceBundle bundle, String... suffixes) {
        return operationName + "-" + paramName;
    }

    @Override
    public String getOperationReplyDescription(String operationName, Locale locale, ResourceBundle bundle) {
        return null;
    }

    @Override
    public String getOperationReplyValueTypeDescription(String operationName, Locale locale, ResourceBundle bundle, String... suffixes) {
        return operationName;
    }

    @Override
    public String getChildTypeDescription(String childType, Locale locale, ResourceBundle bundle) {
        return childType;
    }

    @Override
    public String getResourceDeprecatedDescription(Locale locale, ResourceBundle bundle) {
        return "resource.deprecated";
    }

    @Override
    public String getResourceAttributeDeprecatedDescription(String attributeName, Locale locale, ResourceBundle bundle) {
        return attributeName;
    }

    @Override
    public String getOperationDeprecatedDescription(String operationName, Locale locale, ResourceBundle bundle) {
        return operationName;
    }

    @Override
    public String getOperationParameterDeprecatedDescription(String operationName, String paramName, Locale locale, ResourceBundle bundle) {
        return operationName;
    }

    @Override
    public StandardResourceDescriptionResolver getChildResolver(String key) {
        return NonResolvingResourceDescriptionResolver.INSTANCE;
    }

    private static class EmptyResourceBundle extends ResourceBundle {
        private static final EmptyResourceBundle INSTANCE = new EmptyResourceBundle();

        @Override
        protected Object handleGetObject(String key) {
            return key;
        }

        @Override
        public Enumeration<String> getKeys() {
            return Collections.emptyEnumeration();
        }
    }
}
