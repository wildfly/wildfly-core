/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.access;

/**
 * Encapsulates the {@link AuthorizationResult}s for a given caller's access to a particular resource.
 */
public interface ResourceAuthorization {

    /**
     * Get the authorization result for the entire resource for the given effect.
     * @param actionEffect the action effect
     * @return the authorization result
     */
    AuthorizationResult getResourceResult(Action.ActionEffect actionEffect);

    /**
     * Get the authorization result for an individual attribute.
     * @param attribute the attribute
     * @param actionEffect the action effect
     * @return the authorization result
     */
    AuthorizationResult getAttributeResult(String attribute, Action.ActionEffect actionEffect);

    /**
     * Get the authorization result for an individual operation.
     * @param operationName the operation name
     * @return the authorization result
     */
    AuthorizationResult getOperationResult(String operationName);
}
