/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.extension;

import org.jboss.as.controller.ExpressionResolver;

/**
 * Registry for {@link ExpressionResolverExtension extensions} to a server or host controller's {@link ExpressionResolver}.
 * The registry will be available using the {@code org.wildfly.management.expression-resolver-extension-registry}
 * capability.
 */
public interface ResolverExtensionRegistry {

    /**
     * Adds an extension to the set used by the {@link ExpressionResolver}.
     *
     * @param extension the extension. Cannot be {@code null}
     */
    void addResolverExtension(ExpressionResolverExtension extension);

    /**
     * Removes an extension from the set used by the {@link ExpressionResolver}.
     *
     * @param extension the extension. Cannot be {@code null}
     */
    void removeResolverExtension(ExpressionResolverExtension extension);
}
