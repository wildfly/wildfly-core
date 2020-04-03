/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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
