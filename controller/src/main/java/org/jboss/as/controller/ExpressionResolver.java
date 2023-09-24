/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import java.util.regex.Pattern;

import org.jboss.as.controller.extension.ExpressionResolverExtension;
import org.jboss.as.controller.extension.ResolverExtensionRegistry;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * Resolves {@link org.jboss.dmr.ModelType#EXPRESSION} expressions in a {@link ModelNode}.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
@FunctionalInterface
public interface ExpressionResolver {

    /** A {@link Pattern} that can be used to identify strings that include expression syntax */
    Pattern EXPRESSION_PATTERN = Pattern.compile(".*\\$\\{.*}.*");

    /**
     * Resolves any expressions in the passed in ModelNode.
     *
     * Expressions may represent system properties, vaulted date, or a custom format to be handled by an
     * {@link ExpressionResolverExtension} registered using the {@link ResolverExtensionRegistry}.
     *
     * @param node the ModelNode containing expressions.
     * @return a copy of the node with expressions resolved
     *
     * @throws ExpressionResolutionUserException if {@code expression} is a form understood by the resolver but in some
     *                                             way is unacceptable. This should only be thrown due to flaws in the
     *                                             provided {@code expression} or the configuration of resources used by
     *                                             a resolver extension, which are 'user' problems. It should not
     *                                             be used for internal problems in the resolver or any extension. If a
     *                                             if a security manager exists and its
     *                                             {@link SecurityManager#checkPermission checkPermission} method doesn't
     *                                             allow access to a relevant system property or environment variable,
     *                                             an {@code ExpressionResolutionUserException} should be thrown.
     * @throws OperationFailedException if an {@link ExpressionResolverExtension} throws one from its
     *            {@link ExpressionResolverExtension#initialize(OperationContext)} method.
     * @throws ExpressionResolver.ExpressionResolutionServerException if some other internal expression resolution failure occurs.
     */
    ModelNode resolveExpressions(ModelNode node) throws OperationFailedException;

    /**
     * Resolves any expressions in the passed in ModelNode.
     *
     * Expressions may represent system properties, vaulted date, or a custom format to be handled by an
     * {@link ExpressionResolverExtension} registered using the {@link ResolverExtensionRegistry}.
     *
     * For vaulted data the format is ${VAULT::vault_block::attribute_name::sharedKey}
     *
     * @param node the ModelNode containing expressions.
     * @param context the current {@code OperationContext} to provide additional contextual information.
     * @return a copy of the node with expressions resolved
     *
     * @throws ExpressionResolutionUserException if {@code expression} is a form understood by the resolver but in some
     *                                             way is unacceptable. This should only be thrown due to flaws in the
     *                                             provided {@code expression} or the configuration of resources used by
     *                                             the resolver extension, which are 'user' problems>. It should not
     *                                             be used for internal problems in the resolver extension. If a
     *                                             if a security manager exists and its
     *                                             {@link SecurityManager#checkPermission checkPermission} method doesn't
     *                                             allow access to a relevant system property or environment variable,
     *                                             an {@code ExpressionResolutionUserException} should be thrown
     * @throws OperationFailedException if an {@link ExpressionResolverExtension} throws one from its
     *            {@link ExpressionResolverExtension#initialize(OperationContext)} method.
     * @throws ExpressionResolver.ExpressionResolutionServerException if some other internal expression resolution failure occurs.
     */
    default ModelNode resolveExpressions(ModelNode node, OperationContext context) throws OperationFailedException {
        return resolveExpressions(node);
    }

    /**
     * An {@code ExpressionResolver} that can only resolve from system properties
     * and environment variables. Should not be used for most product resolution use cases as it does
     * not support resolution from resolver extensions.
     */
    ExpressionResolver SIMPLE = new ExpressionResolverImpl();

    /**
     * An {@code ExpressionResolver} suitable for test cases that can only resolve from system properties
     * and environment variables.
     * Should not be used for production code as it does not support resolution from resolver extensions.
     */
    ExpressionResolver TEST_RESOLVER = SIMPLE;

    /**
     * An expression resolver that will not throw an {@code OperationFailedException} when it encounters an
     * unresolvable expression, instead simply returning that expression. Should not be used for most product
     * resolution use cases as it does not support resolution from resolver extensions.
     */
    ExpressionResolver SIMPLE_LENIENT = new ExpressionResolverImpl(true);

    /**
     * An expression resolver that throws an {@code OperationFailedException} if any expressions are found.
     * Intended for use with APIs where an {@code ExpressionResolver} is required but the caller requires
     * that all expression have already been resolved.
     */
    ExpressionResolver REJECTING = new ExpressionResolverImpl() {
        @Override
        protected void resolvePluggableExpression(ModelNode node, OperationContext context) throws OperationFailedException {
            String expression = node.asString();
            if (EXPRESSION_PATTERN.matcher(expression).matches()) {
                throw ControllerLogger.ROOT_LOGGER.illegalUnresolvedModel(expression);
            }
            // It wasn't an expression anyway; convert the node to type STRING
            node.set(expression);
        }
    };

    /**
     * An expression resolver that throws an {@link ExpressionResolutionServerException} if any
     * {@link ExpressionResolverExtension#EXTENSION_EXPRESSION_PATTERN extension expressions} are found, otherwise
     * providing the same behavior as {@link #SIMPLE}.
     * Intended for use in cases where {@link ExpressionResolverExtension}s cannot be available but non-extension
     * expression resolution is wanted.
     */
    ExpressionResolver EXTENSION_REJECTING = new ExpressionResolverImpl() {
        @Override
        protected void resolvePluggableExpression(ModelNode node, OperationContext context) throws OperationFailedException {
            String expression = node.asString();
            if (ExpressionResolverExtension.EXTENSION_EXPRESSION_PATTERN.matcher(expression).matches()) {
                throw ControllerLogger.ROOT_LOGGER.resolverExtensionExpressionsNotAllowed(expression);
            } // else do nothing and standard resolution will take over
        }
    };

    /**
     * Runtime exception used to indicate some user-driven problem that prevented expression
     * resolution, for example:
     * <ol>
     *     <li>
     * A flaw in a user provided expression string that results in a {@link ExpressionResolverExtension}
     * not being able to resolve the expression.
     *     </li>
     *     <li>
     * A server configuration flaw that prevents initialization of runtime services used by the resolver extension.
     *     </li>
     * </ol>
     * <p>
     * This class implements {@link OperationClientException}, so if it is thrown during
     * execution of an {@code OperationStepHandler}, the management kernel will properly
     * handle the exception as a user mistake, not a server fault.
     * <p>
     * <strong>Note:</strong> this should only be thrown if the {@link ExpressionResolverExtension} is
     * sure the expression string is meant to be resolved by itself. Do not throw this in situations
     * where this is unclear.
     * <p>
     * <strong>Note:</strong> this should only be thrown to report problems resulting from user
     * errors. Use {@link ExpressionResolutionServerException} to report faults in
     * {@link ExpressionResolverExtension#resolveExpression(String, OperationContext)} execution that
     * are not due to user inputs.
     */
    class ExpressionResolutionUserException extends RuntimeException implements OperationClientException {

        public ExpressionResolutionUserException(String msg) {
            super(msg);
        }

        public ExpressionResolutionUserException(String msg, Throwable cause) {
            super(msg, cause);
        }

        @Override
        public ModelNode getFailureDescription() {
            return new ModelNode(getLocalizedMessage());
        }
    }

    /**
     * Runtime exception used to indicate a failure in some
     * {@link ExpressionResolverExtension#resolveExpression(String, OperationContext) resolver extension execution}
     * not due to problems with user input like the expression string being resolved or the configuration
     * of resources backing the resolver extension.
     * <p>
     * <strong>Note:</strong> this should only be thrown if the {@link ExpressionResolverExtension} is
     * sure the expression string is meant to be resolved by itself. Do not throw this in situations
     * where this is unclear.
     * <p>
     * <strong>Note:</strong> this should only be thrown to report internal failures in the
     * {@link ExpressionResolverExtension#resolveExpression(String, OperationContext)} execution.
     * Use {@link ExpressionResolutionUserException} to report problems with user inputs.
     */
    class ExpressionResolutionServerException extends RuntimeException {

        public ExpressionResolutionServerException(String msg) {
            super(msg);
        }

        public ExpressionResolutionServerException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
