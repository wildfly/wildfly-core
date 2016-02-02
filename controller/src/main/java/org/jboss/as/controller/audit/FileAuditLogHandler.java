/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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
package org.jboss.as.controller.audit;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.services.path.PathManagerService;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *  All methods on this class should be called with {@link org.jboss.as.controller.audit.ManagedAuditLoggerImpl}'s lock taken.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:istudens@redhat.com">Ivo Studensky</a>
 */
public class FileAuditLogHandler extends AbstractFileAuditLogHandler {
    //SimpleDateFormat is not good to store among threads, since it stores intermediate results in its fields
    //Methods on this class will only ever be called from one thread (see class javadoc) so although it looks shared here it is not
    private static final SimpleDateFormat OLD_FILE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd_HHmmss");

    private final boolean rotateAtStartup;

    public FileAuditLogHandler(String name, String formatterName, int maxFailureCount, PathManagerService pathManager,
                               String path, String relativeTo, boolean rotateAtStartup) {
        super(name, formatterName, maxFailureCount, pathManager, path, relativeTo);
        this.rotateAtStartup = rotateAtStartup;
    }

    @Override
    protected void initializeAtStartup(final File file) {
        // rotate on every startup
        if (file.exists() && rotateAtStartup) {
            final File backup = new File(file.getParentFile(), file.getName() + OLD_FILE_FORMATTER.format(new Date()));
            try {
                rename(file, backup);
            } catch (IOException e) {
                throw ControllerLogger.ROOT_LOGGER.couldNotBackUp(e, file.getAbsolutePath(), backup.getAbsolutePath());
            }
        }
    }

    @Override
    protected void rotateLogFile(final File file) {
        // nothing to do here, we rotate only at startup, see initializeAtStartup method
    }

    boolean isDifferent(AuditLogHandler other){
        if (!(other instanceof FileAuditLogHandler)){
            return true;
        }
        if (rotateAtStartup != ((FileAuditLogHandler) other).rotateAtStartup) {
            return true;
        }
        return super.isDifferent(other);
    }
}
