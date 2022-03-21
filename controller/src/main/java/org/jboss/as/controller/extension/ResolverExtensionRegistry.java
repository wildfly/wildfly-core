/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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
