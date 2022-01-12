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

import java.util.regex.Pattern;

/**
 * Object that can be used to extend the functionality of an {@link ExpressionResolver} by handling
 * expression strings in formats not understood by the expression resolver.
 * <p>
 * Extension expressions must be of the form {@code ${extensionidentifier::someextensionspecificdetails}}, where
 * {@code extensionidentifier} is some string that identifies the desired extension. All resolver extensions in
 * a process must have unique identifiers. Best practice is for subsystems that register extensions to allow end user
 * configuration control over the identifier so they can provide non-conflicting identifiers.  The
 * {@code someextensionspecificdetails} part of the expression is an opaque string understood by the relevant
 * resolver extension.
 * </p>
 */
public interface ExpressionResolverExtension {

    /** A {@link Pattern} that strings must match for any ExpressionResolverExtension to handle them. */
    Pattern EXTENSION_EXPRESSION_PATTERN = Pattern.compile("\\$\\{.+::.+}");

    /**
     * Initialize the extension using the given {@link OperationContext}. May be called multiple times
     * for a given extension, so extensions should handle that appropriately. Note that this method
     * may be invoked in {@link OperationContext.Stage#MODEL}. Implementations are not required to support initialization
     * in [@code OperationContext.Stage.MODEL} but should throw {@link org.jboss.as.controller.ExpressionResolver.ExpressionResolutionServerException}
     * if they do not.
     *
     * @param context the {@link OperationContext}. Will not be {@code null}
     * @throws OperationFailedException if a problem initializing occurs that indicates a user mistake
     *                                  (e.g. an improper configuration of a resource used by the extension.)
     *                                  Do not use for non-user-driven problems; use runtime exceptions for those.
     *                                  Throwing a runtime exception that implements {@link OperationClientException}
     *                                  is also a valid way to handle user mistakes.
     * @throws org.jboss.as.controller.ExpressionResolver.ExpressionResolutionServerException if a non-user-driven
     *                                 problem occurs, including an invocation during {@link OperationContext.Stage#MODEL}
     *                                 if that is not supported.
     */
    void initialize(OperationContext context) throws OperationFailedException;

    /**
     * Resolve a given simple expression string, returning {@code null} if the string is not of a form
     * recognizable to the plugin.
     * <p>
     * <strong>Note:</strong> A thread invoking this method must immediately precede the invocation with a call to
     * {@link #initialize(OperationContext)}.
     * </p>
     *
     * @param expression a string that matches {@link #EXTENSION_EXPRESSION_PATTERN} and that does not have
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
