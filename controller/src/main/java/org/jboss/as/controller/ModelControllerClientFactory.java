/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.concurrent.Executor;

/**
 * Factory for obtaining a {@link org.jboss.as.controller.client.ModelControllerClient}
 * for use in the same VM as the target {@link ModelController}.
 *
 * @author Brian Stansberry
 */
public interface ModelControllerClientFactory {

    /**
     * Create an in-VM client whose operations are executed with authorization checks performed
     * based on the security identity that is associated with the calling thread when the
     * client is invoked. Operations are not automatically treated as if invoked by a user
     * in the RBAC {@code SuperUser} role, and thus may be rejected due to failed authorization
     * checks.
     *
     * @param executor the executor to use for asynchronous operation execution. Cannot be {@code null}
     * @return the client. Will not return {@code null}
     *
     * @throws SecurityException if the caller does not have the
     *            {@link org.jboss.as.controller.security.ControllerPermission#CAN_ACCESS_MODEL_CONTROLLER CAN_ACCESS_MODEL_CONTROLLER}
     *            permission
     */
    LocalModelControllerClient createClient(Executor executor);

    /**
     * Create an in-VM client whose operations are executed as if they were invoked by a user in the
     * RBAC {@code SuperUser} role, regardless of any security identity that is or isn't associated
     * with the calling thread when the client is invoked. <strong>This client generally should not
     * be used to handle requests from external callers, and if it is used great care should be
     * taken to ensure such use is not suborning the intended access control scheme.</strong>
     * <p>
     * In a VM with a {@link java.lang.SecurityManager SecurityManager} installed, invocations
     * against the returned client can only occur from a calling context with the
     * {@link org.jboss.as.controller.security.ControllerPermission#PERFORM_IN_VM_CALL PERFORM_IN_VM_CALL}
     * permission. Without this permission a {@link SecurityException} will be thrown.
     * <p>
     * Calling this method is equivalent to a call to
     * {@link #createSuperUserClient(Executor, boolean) createSuperUserClient(executor, false)}.
     *
     * @param executor the executor to use for asynchronous operation execution. Cannot be {@code null}
     * @return the client. Will not return {@code null}
     *
     * @throws SecurityException if the caller does not have the
     *            {@link org.jboss.as.controller.security.ControllerPermission#CAN_ACCESS_MODEL_CONTROLLER CAN_ACCESS_MODEL_CONTROLLER}
     *            permission
     */
    default LocalModelControllerClient createSuperUserClient(Executor executor) {
        return createSuperUserClient(executor, false);
    }

    /**
     * Create an in-VM client whose operations are executed as if they were invoked by a user in the
     * RBAC {@code SuperUser} role, regardless of any security identity that is or isn't associated
     * with the calling thread when the client is invoked. <strong>This client generally should not
     * be used to handle requests from external callers, and if it is used great care should be
     * taken to ensure such use is not suborning the intended access control scheme.</strong>
     * <p>
     * In a VM with a {@link java.lang.SecurityManager SecurityManager} installed, invocations
     * against the returned client can only occur from a calling context with the
     * {@link org.jboss.as.controller.security.ControllerPermission#PERFORM_IN_VM_CALL PERFORM_IN_VM_CALL}
     * permission. Without this permission a {@link SecurityException} will be thrown.
     *
     * @param executor the executor to use for asynchronous operation execution. Cannot be {@code null}
     * @param forUserCalls if {@code true} the operation executed by this client should be regarded as coming
     *                     from an end user. For example, such operations cannot target
     *                     {@link org.jboss.as.controller.registry.OperationEntry.EntryType#PRIVATE}
     *                     operations
     * @return the client. Will not return {@code null}
     *
     * @throws SecurityException if the caller does not have the
     *            {@link org.jboss.as.controller.security.ControllerPermission#CAN_ACCESS_MODEL_CONTROLLER CAN_ACCESS_MODEL_CONTROLLER}
     *            permission
     */
    LocalModelControllerClient createSuperUserClient(Executor executor, boolean forUserCalls);
}
