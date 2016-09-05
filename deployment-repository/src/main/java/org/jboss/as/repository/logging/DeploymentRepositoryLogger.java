/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.repository.logging;

import static org.jboss.logging.Logger.Level.ERROR;
import static org.jboss.logging.Logger.Level.INFO;
import static org.jboss.logging.Logger.Level.WARN;

import java.nio.file.Path;
import org.jboss.as.repository.ExplodedContentException;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MessageLogger(projectCode = "WFLYDR", length = 4)
public interface DeploymentRepositoryLogger extends BasicLogger {

    /**
     * A logger with the category of the package name.
     */
    DeploymentRepositoryLogger ROOT_LOGGER = Logger.getMessageLogger(DeploymentRepositoryLogger.class, "org.jboss.as.repository");

    /**
     * Logs an informational message indicating the content was added at the location, represented by the {@code path}
     * parameter.
     *
     * @param path the name of the path.
     */
    @LogMessage(level = INFO)
    @Message(id = 1, value = "Content added at location %s")
    void contentAdded(String path);

    @LogMessage(level = INFO)
    @Message(id = 2, value = "Content removed from location %s")
    void contentRemoved(String path);

    @LogMessage(level = WARN)
    @Message(id = 3, value = "Cannot delete temp file %s, will be deleted on exit")
    void cannotDeleteTempFile(@Cause Throwable cause, String path);

    /**
     * Creates an exception indicating a failure to create the directory represented by the {@code path} parameter.
     *
     * @param path the path name.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 4, value = "Cannot create directory %s")
    IllegalStateException cannotCreateDirectory(@Cause Throwable cause, String path);

    /**
     * Creates an exception indicating the inability to obtain SHA-1.
     *
     * @param cause the cause of the error.
     * @param name  the name of the class.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 5, value = "Cannot obtain SHA-1 %s")
    IllegalStateException cannotObtainSha1(@Cause Throwable cause, String name);

    /**
     * Creates an exception indicating the directory, represented by the {@code path} parameter, is not writable.
     *
     * @param path the path name.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 6, value = "Directory %s is not writable")
    IllegalStateException directoryNotWritable(String path);

    /**
     * Creates an exception indicating the path, represented by the {@code path} parameter, is not a directory.
     *
     * @param path the path name.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 7, value = "%s is not a directory")
    IllegalStateException notADirectory(String path);

    // id = 8; redundant parameter null check message

    @LogMessage(level = INFO)
    @Message(id = 9, value = "Content %s is obsolete and will be removed")
    void obsoleteContentCleaned(String contentIdentifier);

    @LogMessage(level = ERROR)
    @Message(id = 10, value = "Couldn't delete content %s")
    void contentDeletionError(@Cause Throwable cause, String name);

    @LogMessage(level = INFO)
    @Message(id = 11, value = "Couldn't list directory files for %s")
    void localContentListError(String name);

    @Message(id = 12, value = "Cannot hash current deployment content %s")
    RuntimeException hashingError(@Cause Throwable cause, Path path);

    @Message(id = 13, value = "Access denied to the content at %s in the deployment")
    IllegalArgumentException forbiddenPath(String path);

    @LogMessage(level = ERROR)
    @Message(id = 14, value = "Error deleting deployment %s")
    void couldNotDeleteDeployment(@Cause Exception ex, String path);

    @Message(id = 15, value = "%s is not an archive file")
    IllegalStateException notAnArchive(String path);

    @Message(id = 16, value = "Achive file %s not found")
    ExplodedContentException archiveNotFound(String path);

    @LogMessage(level = INFO)
    @Message(id = 17, value = "Content exploded at location %s")
    void contentExploded(String path);

    @Message(id = 18, value = "Error exploding content for %s")
    ExplodedContentException errorExplodingContent(@Cause Exception ex, String path);

    @Message(id = 19, value = "Deployment is locked by another operation")
    ExplodedContentException errorLockingDeployment();

    @Message(id = 20, value = "Error accessing deployment files")
    ExplodedContentException errorAccessingDeployment(@Cause Exception ex);

    @Message(id = 21, value = "Error updating content of exploded deployment")
    ExplodedContentException errorUpdatingDeployment(@Cause Exception ex);

    @Message(id = 22, value = "Error copying files of exploded deployment to %s")
    ExplodedContentException errorCopyingDeployment(@Cause Exception ex, String target);

    @LogMessage(level = ERROR)
    @Message(id = 23, value = "Error deleting file %s")
    void cannotDeleteFile(@Cause Exception ex, Path path);

    @LogMessage(level = ERROR)
    @Message(id = 24, value = "Error copying file %s")
    void cannotCopyFile(@Cause Exception ex, Path path);
}
