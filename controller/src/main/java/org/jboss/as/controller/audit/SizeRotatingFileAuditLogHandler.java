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
import java.nio.charset.StandardCharsets;

/**
 *  All methods on this class should be called with {@link org.jboss.as.controller.audit.ManagedAuditLoggerImpl}'s lock taken.
 *
 * @author <a href="mailto:istudens@redhat.com">Ivo Studensky</a>
 */
public class SizeRotatingFileAuditLogHandler extends AbstractFileAuditLogHandler {
    // by default, rotate at 10MB
    private long rotateSize = 0xa0000L;
    private int maxBackupIndex = 1;

    private volatile long currentSize = 0;

    public SizeRotatingFileAuditLogHandler(final String name, final String formatterName, final int maxFailureCount, final PathManagerService pathManager, final String path, final String relativeTo, final long rotateSize, final int maxBackupIndex) {
        super(name, formatterName, maxFailureCount, pathManager, path, relativeTo);
        this.rotateSize = rotateSize;
        this.maxBackupIndex = maxBackupIndex;
    }

    @Override
    protected void initializeAtStartup(final File file) {
        currentSize = file.length();
    }

    @Override
    protected void rotateLogFile(final File file) {
        if (currentSize > this.rotateSize) {
            // rotate
            if (maxBackupIndex > 0) {
                // first, drop the max file (if any), then move each file to the next higher slot.
                new File(file.getAbsolutePath() + "." + maxBackupIndex).delete();
                for (int i = maxBackupIndex - 1; i >= 1; i--) {
                    final File from = new File(file.getAbsolutePath() + "." + i);
                    final File to = new File(file.getAbsolutePath() + "." + (i + 1));
                    try {
                        rename(from, to);
                    } catch (IOException e) {
                        throw ControllerLogger.ROOT_LOGGER.couldNotBackUp(e, from.getAbsolutePath(), to.getAbsolutePath());
                    }
                }
                final File backup = new File(file.getAbsolutePath() + ".1");
                try {
                    rename(file, backup);
                } catch (IOException e) {
                    throw ControllerLogger.ROOT_LOGGER.couldNotBackUp(e, file.getAbsolutePath(), backup.getAbsolutePath());
                }
            } else {
                // just ditch out the content of audit log if maxBackupIndex == 0
                file.delete();
            }

            createNewFile(file);
            currentSize = 0;
        }
    }

    @Override
    void writeLogItem(String formattedItem) throws IOException {
        super.writeLogItem(formattedItem);
        currentSize += formattedItem.getBytes(StandardCharsets.UTF_8).length;
        currentSize += LINE_TERMINATOR.length;
    }

    @Override
    boolean isDifferent(AuditLogHandler other){
        if (other instanceof SizeRotatingFileAuditLogHandler == false){
            return true;
        }
        SizeRotatingFileAuditLogHandler otherHandler = (SizeRotatingFileAuditLogHandler)other;
        if (rotateSize != otherHandler.rotateSize) {
            return true;
        }
        if (maxBackupIndex != otherHandler.maxBackupIndex) {
            return true;
        }
        if (super.isDifferent(other)) {
            return true;
        }
        return false;
    }

}
