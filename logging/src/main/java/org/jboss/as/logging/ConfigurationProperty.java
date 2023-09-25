/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.logging.resolvers.ModelNodeResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.config.PropertyConfigurable;

/**
 * Used for configuring logging properties.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface ConfigurationProperty<T> {

    /**
     * Returns the resolver for the attribute.
     *
     * @return the resolver.
     */
    ModelNodeResolver<T> resolver();

    /**
     * Returns the property name for the logging configuration property.
     *
     * @return the property name.
     */
    String getPropertyName();

    /**
     * Resolves the value of the model node the type. Uses the {@link org.jboss.as.logging.resolvers.ModelNodeResolver#resolveValue(org.jboss.as.controller.OperationContext,
     * org.jboss.dmr.ModelNode)} to resolve the value. If the {@link #resolver() resolver} is  {@code null}, either
     * {@code null} is returned or a default value based on the type implementation.
     *
     * @param context the operation context.
     * @param model   the model used to resolve the value from.
     *
     * @return the resolved value or {@code null}.
     *
     * @throws OperationFailedException if the value is invalid.
     */
    T resolvePropertyValue(OperationContext context, ModelNode model) throws OperationFailedException;

    /**
     * Sets the property on the configuration.
     * <p/>
     * If the result of {@link #resolvePropertyValue} is not
     * {@code null}, the value is set on the property with {@link String#valueOf(Object)}. If the result is {@code null}
     * and the model is defined, {@link org.jboss.dmr.ModelNode#asString()} is used for the property, otherwise the
     * property is removed from the handler.
     *
     * @param context       the operation context used to resolve the value from the model.
     * @param model         the model used to resolve the value.
     * @param configuration the configuration to set the value on.
     *
     * @throws OperationFailedException if the value is invalid.
     */
    void setPropertyValue(OperationContext context, ModelNode model, PropertyConfigurable configuration) throws OperationFailedException;
}
