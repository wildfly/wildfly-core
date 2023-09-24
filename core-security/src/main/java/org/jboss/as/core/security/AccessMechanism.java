/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.security;

/**
 * An enumeration representing the mechanism used to submit a request to the server.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public enum AccessMechanism {
    /**
     * The request was submitted directly as a management operation over the native interface or as a management operation
     * natively but after a HTTP upgrade.
     */
    NATIVE,
    /**
     * The request was submitted directyl as a manaagement operation over the HTTP interface.
     */
    HTTP,
    /**
     * The request was submitted over JMX and subsequently converted to a management operation.
     */
    JMX,
    /**
     * The request was submitted by an in-vm client on behalf of a user. For example, the server is embedded
     * and the user is using a {@code LocalModelControllerClient}.  An in-vm client not performing operations
     * on behalf of a user (e.g. a client used by the deployment-scanner) will not have this mechanism set.
     */
    IN_VM_USER
}
