/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018, Red Hat, Inc., and individual contributors
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

package org.wildfly.jdk.version;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
@MessageLogger(projectCode = "WFLYJV", length = 4)
interface JdkVersionLogger extends BasicLogger {

    /**
     * The root logger with a category of the package.
     */
    JdkVersionLogger ROOT_LOGGER = Logger.getMessageLogger(JdkVersionLogger.class, "org.wildfly.jdk.version");

    /**
     * Creates an exception indicating the Java executable could not be found.
     *
     * @param javaExecutable the executable file to be located.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 1, value = "Could not find java executable %s.")
    IllegalStateException cannotFindJavaExe(String javaExecutable);

    /**
     * Creates an exception indicating the Java home directory does not exist.
     *
     * @param dir the directory to Java home.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 2, value = "Java home '%s' does not exist.")
    IllegalStateException invalidJavaHome(String dir);

    /**
     * Creates an exception indicating the Java home bin directory does not exist.
     *
     * @param binDir      the bin directory.
     * @param javaHomeDir the Java home directory.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 3, value = "Java home's bin '%s' does not exist. The home directory was determined to be %s.")
    IllegalStateException invalidJavaHomeBin(String binDir, String javaHomeDir);

}
