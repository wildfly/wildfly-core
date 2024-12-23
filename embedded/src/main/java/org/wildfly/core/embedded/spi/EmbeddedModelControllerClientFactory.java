/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.embedded.spi;

import java.util.concurrent.Executor;

import org.jboss.as.controller.client.LocalModelControllerClient;
import org.jboss.msc.service.ServiceName;

/**
 * Factory for obtaining a {@link org.jboss.as.controller.client.ModelControllerClient}
 * for use by an embedding process.
 *
 * @author Brian Stansberry
 */
public interface EmbeddedModelControllerClientFactory {

    /** Only for use within the WildFly kernel; may change or be removed at any time */
    ServiceName SERVICE_NAME = ServiceName.parse("org.wildfly.embedded.model-controller-client-factory");

    /**
     * Create an in-VM client whose operations are executed as if they were invoked by a user in the
     * RBAC {@code SuperUser} role, regardless of any security identity that is or isn't associated
     * with the calling thread when the client is invoked. <strong>This client generally should not
     * be used to handle requests from external callers, and if it is used great care should be
     * taken to ensure such use is not suborning the intended access control scheme.</strong>
     * <p>
     * In a VM with a {@link java.lang.SecurityManager SecurityManager} installed, invocations
     * against the returned client can only occur from a calling context with the
     * {@code org.jboss.as.controller.security.ControllerPermission#PERFORM_IN_VM_CALL PERFORM_IN_VM_CALL}
     * permission. Without this permission a {@link SecurityException} will be thrown.
     *
     * @param executor the executor to use for asynchronous operation execution. Cannot be {@code null}
     * @return the client. Will not return {@code null}
     *
     * @throws SecurityException if the caller does not have the
     *            {@code org.jboss.as.controller.security.ControllerPermission#CAN_ACCESS_MODEL_CONTROLLER CAN_ACCESS_MODEL_CONTROLLER}
     *            permission
     */
    LocalModelControllerClient createEmbeddedClient(Executor executor);
}
