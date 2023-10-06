/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.common;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;

import java.util.EnumSet;
import java.util.logging.Level;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Operation that resolves an expression (but not against the vault) and returns the resolved value.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ResolveExpressionHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = "resolve-expression";

    public static final ResolveExpressionHandler INSTANCE = new ResolveExpressionHandler();

    public static final SimpleAttributeDefinition EXPRESSION = new SimpleAttributeDefinitionBuilder("expression", ModelType.STRING, true)
            .setAllowExpression(true).build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, ControllerResolver.getResolver("core"))
        .addParameter(EXPRESSION)
        .setReplyType(ModelType.STRING)
        .allowReturnNull()
        .setReadOnly()
        .setRuntimeOnly()
        .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SYSTEM_PROPERTY)
        .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.JVM) // use the JVM constraint as a guard against unauthorized reads of env vars
        .build();


    private ResolveExpressionHandler() {
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        // Resolving can involve reading a system property or env vars, so ensure the caller
        // is authorized to do that.
        context.authorize(operation, EnumSet.of(Action.ActionEffect.ADDRESS, Action.ActionEffect.READ_RUNTIME)).failIfDenied(operation);

        // Run at Stage.RUNTIME so we get the current values of system properties set by earlier steps in a composite
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                ModelNode toResolve = EXPRESSION.validateOperation(operation);
                if (toResolve.getType() == ModelType.STRING) {
                    toResolve = ParseUtils.parsePossibleExpression(toResolve.asString());
                }
                try {
                    ModelNode answer = ExpressionResolver.SIMPLE.resolveExpressions(toResolve);
                    if (!answer.equals(toResolve)) {
                        // SIMPLE will not resolve everything the context can, e.g. vault or credential store expressions.
                        // And we don't want to provide such resolution, as that kind of security-sensitive resolution should
                        // not escape the server process by being sent in a management op response. But if the true
                        // resolution differs from what SIMPLE did, we should just not resolve in our response and
                        // include a warning to that effect.
                        ModelNode fullyResolved = context.resolveExpressions(toResolve);
                        if (!answer.equals(fullyResolved)) {
                            answer = toResolve;
                            context.addResponseWarning(Level.WARNING,
                                    ControllerLogger.MGMT_OP_LOGGER.expressionUnresolvableUsingSimpleResolution(
                                            toResolve, operation.get(OP).asString()));
                        }
                    }
                    ModelNode result = context.getResult();
                    if (answer.isDefined()) {
                        result.set(answer.asString());
                    }
                    context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
                } catch (SecurityException e) {
                    throw ControllerLogger.ROOT_LOGGER.noPermissionToResolveExpression(toResolve, e);
                } catch (IllegalStateException | ExpressionResolver.ExpressionResolutionServerException e) {
                    deferFailureReporting(context, new ModelNode(e.getLocalizedMessage()));
                } catch (OperationFailedException | ExpressionResolver.ExpressionResolutionUserException e) {
                    deferFailureReporting(context, e.getFailureDescription());
                }
            }

            /**
             * Defer the failure reporting to avoid noise in the server log.
             * @param context
             * @param failureDescription
             */
            private void deferFailureReporting(OperationContext context, final ModelNode failureDescription) {
                // WFCORE-149 We are going to defer reporting this failure until the result handler runs
                // so we don't put noise in the server log.
                // But, we don't want that deferred reporting to disrupt the normal rollback behavior when
                // a failure occurs, so set rollback only
                if(context.isRollbackOnRuntimeFailure()) {
                    context.setRollbackOnly();
                }
                context.completeStep(new OperationContext.ResultHandler() {
                    @Override
                    public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                        context.getFailureDescription().set(failureDescription);
                    }
                });
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
