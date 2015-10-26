/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * The result of an access control decision.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class AuthorizationResult {

    public enum Decision {
        PERMIT,
        DENY
    }

    public static final AuthorizationResult PERMITTED = new AuthorizationResult(Decision.PERMIT);

    private static final ModelNode NO_EXPLANATION = new ModelNode();
    static {
        NO_EXPLANATION.protect();
    }

    private final Decision decision;
    private final ModelNode explanation;

    /**
     * Creates an authorization result with no explanation.
     *
     * @param decision  the authorization decision. Cannot be {@code null}
     */
    public AuthorizationResult(Decision decision) {
        this(decision, NO_EXPLANATION);
    }

    /**
     * Creates an authorization result with an optional explanation.
     *
     * @param decision  the authorization decision. Cannot be {@code null}
     * @param explanation the explanation for the decision. May be {@code null}
     */
    public AuthorizationResult(Decision decision, ModelNode explanation) {
        this.decision = decision;
        this.explanation = explanation == null ? NO_EXPLANATION : explanation;
    }

    /**
     * Gets the authorization decision.
     *
     * @return the decision. Will not be {@code null}
     */
    public Decision getDecision() {
        return decision;
    }

    /**
     * Gets the explanation for the authorization decision. Will be an undefined node if no explanation
     * was passed to the constructor.
     *
     * @return the explanation, an immutable model node. Will not be {@code null}, but may be an undefined node
     */
    public ModelNode getExplanation() {
        return explanation;
    }

    /**
     * Utility method to throw a standard failure if {@link #getDecision()} is
     * {@link org.jboss.as.controller.access.AuthorizationResult.Decision#DENY}.
     * <p>
     * This variant extracts the target address from the {@code address} field in the {@code operation} param
     * and then calls the {@linkplain #failIfDenied(ModelNode, PathAddress) overloaded variant}.
     * </p>
     *
     * @param operation the operation the triggered this authorization result. Cannot be {@code null}
     * @throws OperationFailedException if {@link #getDecision()} is
     *                                  {@link org.jboss.as.controller.access.AuthorizationResult.Decision#DENY}
     */
    public void failIfDenied(ModelNode operation) throws OperationFailedException {
        failIfDenied(operation, PathAddress.pathAddress(operation.get(OP_ADDR)));
    }

    /**
     * Utility method to throw a standard failure if {@link #getDecision()} is
     * {@link org.jboss.as.controller.access.AuthorizationResult.Decision#DENY}.
     *
     * @param operation the operation the triggered this authorization result. Cannot be {@code null}
     * @param targetAddress the target address of the request that triggered this authorization result. Cannot be {@code null}
     * @throws OperationFailedException if {@link #getDecision()} is
     *                                  {@link org.jboss.as.controller.access.AuthorizationResult.Decision#DENY}
     */
    public void failIfDenied(ModelNode operation, PathAddress targetAddress) throws OperationFailedException {
        if (decision == AuthorizationResult.Decision.DENY) {
            throw ControllerLogger.ACCESS_LOGGER.unauthorized(operation.get(OP).asString(),
                    targetAddress, explanation);
        }

    }
}
