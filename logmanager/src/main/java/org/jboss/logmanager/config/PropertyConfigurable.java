/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.logmanager.config;

import java.util.List;

/**
 * An object which is configurable via object properties.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface PropertyConfigurable {

    /**
     * Set a property value from a string.
     *
     * @param propertyName the property name
     * @param value        the property value
     *
     * @throws IllegalArgumentException if the given value is not acceptable for this property
     */
    void setPropertyValueString(String propertyName, String value) throws IllegalArgumentException;

    /**
     * Get the string property value with the given name.
     *
     * @param propertyName the property name
     *
     * @return the property value string
     */
    String getPropertyValueString(String propertyName);

    /**
     * Get the property value.
     *
     * @param propertyName the property name
     *
     * @return the property value
     */
    ValueExpression<String> getPropertyValueExpression(String propertyName);

    /**
     * Sets the expression value for the property.
     *
     * @param propertyName the name of the property
     * @param expression   the expression used to resolve the value
     */
    void setPropertyValueExpression(String propertyName, String expression);

    /**
     * Sets the expression value for the property.
     * <p/>
     * This method will not parse the expression for the value and instead use the {@code value} parameter for the
     * value.
     *
     * @param propertyName the name of the property
     * @param expression   the expression used to resolve the value
     * @param value        the value to use
     */
    void setPropertyValueExpression(String propertyName, String expression, String value);

    /**
     * Determine whether the given property name is configured.
     *
     * @param propertyName the property name to test
     *
     * @return {@code true} if the name is configured, {@code false} otherwise
     */
    boolean hasProperty(String propertyName);

    /**
     * Remove a configured property.  Does not affect the underlying configured value; just removes it from the
     * configuration.
     *
     * @param propertyName the property name
     *
     * @return {@code true} if the property name was removed, {@code false} if it was not present
     */
    boolean removeProperty(String propertyName);

    /**
     * Get the names of the configured properties in order.
     *
     * @return the property names
     */
    List<String> getPropertyNames();

    /**
     * Determine whether the given property name is a constructor property.
     *
     * @param propertyName the name of the property to check.
     *
     * @return {@code true} if the property should be used as a construction property, otherwise {@code false}.
     */
    boolean hasConstructorProperty(String propertyName);

    /**
     * Returns a collection of the constructor properties.
     *
     * @return a collection of the constructor properties.
     */
    List<String> getConstructorProperties();

    /**
     * Adds a method name to be invoked after all properties have been set.
     *
     * @param methodName the name of the method
     *
     * @return {@code true} if the method was successfully added, otherwise {@code false}
     */
    boolean addPostConfigurationMethod(String methodName);

    /**
     * Returns a collection of the methods to be invoked after the properties have been set.
     *
     * @return a collection of method names or an empty list
     */
    List<String> getPostConfigurationMethods();

    /**
     * Sets the method names to be invoked after the properties have been set.
     *
     * @param methodNames the method names to invoke
     */
    void setPostConfigurationMethods(String... methodNames);

    /**
     * Sets the method names to be invoked after the properties have been set.
     *
     * @param methodNames the method names to invoke
     */
    void setPostConfigurationMethods(List<String> methodNames);

    /**
     * Removes the post configuration method.
     *
     * @param methodName the method to remove
     *
     * @return {@code true} if the method was removed, otherwise {@code false}
     */
    boolean removePostConfigurationMethod(String methodName);
}
