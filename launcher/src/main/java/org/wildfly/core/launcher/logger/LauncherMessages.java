/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.launcher.logger;

import java.nio.file.Path;

import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@MessageBundle(projectCode = "WFLYLNCHR", length = 4)
public interface LauncherMessages {

    LauncherMessages MESSAGES = Messages.getBundle(LauncherMessages.class);

    /**
     * Creates a message indicating the path does not exist.
     *
     * @param path the path that does not exist
     *
     * @return an exception for the error
     */
    @Message(id = 1, value = "The path '%s' does not exist")
    IllegalArgumentException pathDoesNotExist(Path path);

    @Message(id = 2, value = "The directory '%s' is not a valid directory")
    IllegalArgumentException invalidDirectory(Path dir);

    @Message(id = 3, value = "Invalid directory, could not find '%s' in '%s'")
    IllegalArgumentException invalidDirectory(String filename, Path dir);

    @Message(id = 4, value = "Path '%s' is not a regular file.")
    IllegalArgumentException pathNotAFile(Path path);

    @Message(id = 5, value = "The parameter %s cannot be null.")
    IllegalArgumentException nullParam(String name);

    @Message(id = 6, value = "Invalid hostname: %s")
    IllegalArgumentException invalidHostname(CharSequence hostname);

    @Message(id = 7, value = "The argument %s is not allowed for %s.")
    IllegalArgumentException invalidArgument(String argument, String methodName);
}
