/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.management.client.content;

import java.security.NoSuchAlgorithmException;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MessageLogger(projectCode = "WFLYCNT", length = 4)
interface ManagedDMRContentLogger extends BasicLogger {

    /**
     * A logger with the category of the package.
     */
    ManagedDMRContentLogger ROOT_LOGGER = Logger.getMessageLogger(ManagedDMRContentLogger.class, "org.jboss.as.management.client.content");

    /**
     * Creates an exception indicating the expected content hash provided by the caller does not match the current value.
     *
     * @param expectedHash the expected hash of the content
     * @param address the address of the content
     * @param currentHash the current hash of the content
     *
     * @return an {@link OperationFailedException} for the error.
     */
    @Message(id = 1, value = "Invalid hash '%s' for content at address %s; current hash is '%s' -- perhaps the content has been updated by another caller?")
    OperationFailedException invalidHash(String expectedHash, PathAddress address, String currentHash);

    /**
     * Creates a runtime exception indicating the SHA-1 message digest algorithm is not available.
     *
     * @param cause the cause.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 2, value = "Cannot obtain Message Digest algorithm SHA-1")
    IllegalStateException messageDigestAlgorithmNotAvailable(@Cause NoSuchAlgorithmException cause);

    /**
     * Creates a runtime exception indicating the type element of resource being registered is incorrect.
     *
     * @param type the provided type.
     * @param legalType the valid type
     *
     * @return an {@link IllegalArgumentException} for the error.
     */
    @Message(id = 3, value = "Illegal child type %s -- must be %s")
    IllegalArgumentException illegalChildType(String type, String legalType);

    /**
     * Creates a runtime exception indicating an attempt was made to register a resource whose implementation is
     * not the correct class. This would indicate a programming error, not a user error,
     *
     * @param clazz the class of the resource that cannot be registered
     *
     * @return an {@link IllegalArgumentException} for the error.
     */
    @Message(id = 4, value = "Illegal child resource class %s")
    IllegalArgumentException illegalChildClass(Class<? extends Resource> clazz);

    /**
     * Creates a runtime exception indicating no content could be found using the given hash
     *
     * @param hash the hash of the content
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 5, value = "No content found with hash %s")
    IllegalStateException noContentFoundWithHash(String hash);

    /**
     * Creates a runtime exception indicating a ManagedDMRContentResource did not have a parent resource
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 6, value = "null parent")
    IllegalStateException nullParent();
}
