/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller;

/**
 * Object that can be used to extend the functionality of an {@link ExpressionResolver} by handling
 * expression strings in formats not understood by the expression resolver.
 */
public interface ExpressionResolverExtension {

    /**
     * Initialize the extension using the given {@link OperationContext}. May be called multiple times
     * for a given extension, so extensions should handle that appropriately.
     *
     * @param context the {@link OperationContext}. Will not be {@code null}
     * @throws OperationFailedException if a problem initializing occurs that indicates a user mistake
     *                                  (e.g. an improper configuration of a resource used by the extension.)
     *                                  Do not use for non-user-driven problems; use runtime exceptions for those.
     *                                  Throwing a runtime exception that implements {@link OperationClientException}
     *                                  is also a valid way to handle user mistakes.
     */
    void initialize(OperationContext context) throws OperationFailedException;

    /**
     * Resolve a given simple expression string, returning {@code null} if the string is not of a form
     * recognizable to the plugin.
     *
     * @param expression a string that begins with <code>${</code> and ends with <code>}</code> and that does not have
     *                   any substrings that match that pattern.
     * @param context    the current {@code OperationContext} to provide additional contextual information.
     * @return a string representing the resolve expression, or {@code null} if {@code expression} is not of a
     * form understood by the plugin.
     * @throws ExpressionResolver.ExpressionResolutionUserException   if {@code expression} is a form understood by the plugin but in some
     *                                             way is unacceptable. This should only be thrown due to flaws in the
     *                                             provided {@code expression} or the configuration of resources used by
     *                                             the resolver extension, which are 'user' problems. It should not
     *                                             be used for internal problems in the resolver extension.
     * @throws ExpressionResolver.ExpressionResolutionServerException if some other internal expression resolution failure occurs.
     */
    String resolveExpression(String expression, OperationContext context);

}
