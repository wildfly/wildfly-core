/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.audit;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.protocol.StreamUtils;
import org.xnio.IoUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 *  All methods on this class should be called with {@link org.jboss.as.controller.audit.ManagedAuditLoggerImpl}'s lock taken.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:istudens@redhat.com">Ivo Studensky</a>
 */
public abstract class AbstractFileAuditLogHandler extends AuditLogHandler {
    protected static final byte[] LINE_TERMINATOR = System.lineSeparator().getBytes(StandardCharsets.UTF_8);
    private final PathManagerService pathManager;
    private final String path;
    private final String relativeTo;

    private volatile File file;

    public AbstractFileAuditLogHandler(String name, String formatterName, int maxFailureCount, PathManagerService pathManager, String path, String relativeTo) {
        super(name, formatterName, maxFailureCount);
        this.pathManager = pathManager;
        this.path = path;
        this.relativeTo = relativeTo;
    }

    @Override
    void initialize() {
        if (file == null) {
            File file = new File(pathManager.resolveRelativePathEntry(path, relativeTo));
            if (file.exists() && file.isDirectory()) {
                throw ControllerLogger.ROOT_LOGGER.resolvedFileDoesNotExistOrIsDirectory(file);
            }

            File parent = file.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }

            initializeAtStartup(file);

            if (!file.exists()) {
                createNewFile(file);
            }

            this.file = file;
        }

        rotateLogFile(file);
    }

    protected abstract void initializeAtStartup(final File file);
    protected abstract void rotateLogFile(final File file);

    @Override
    void stop() {
        file = null;
    }

    @Override
    void writeLogItem(String formattedItem) throws IOException {
        final FileOutputStream fos = new FileOutputStream(file, true);
        final BufferedOutputStream output = new BufferedOutputStream(fos);
        try {
            output.write(formattedItem.getBytes(StandardCharsets.UTF_8));
            output.write(LINE_TERMINATOR);

            //Flush and force the file to sync
            output.flush();
            fos.getFD().sync();
        } finally {
            IoUtils.safeClose(output);
        }
    }

    @Override
    boolean isDifferent(AuditLogHandler other){
        if (other instanceof AbstractFileAuditLogHandler == false){
            return true;
        }
        AbstractFileAuditLogHandler otherHandler = (AbstractFileAuditLogHandler)other;
        if (!name.equals(otherHandler.name)){
            return true;
        }
        if (!getFormatterName().equals(otherHandler.getFormatterName())) {
            return true;
        }
        if (!path.equals(otherHandler.path)){
            return true;
        }
        if (!compare(relativeTo, otherHandler.relativeTo)){
            return true;
        }
        return false;
    }

    private boolean compare(Object one, Object two){
        if (one == null && two == null){
            return true;
        }
        if (one == null && two != null){
            return false;
        }
        if (one != null && two == null){
            return false;
        }
        return one.equals(two);
    }

    protected void copyFile(final File file, final File backup) throws IOException {
        final InputStream in = new BufferedInputStream(new FileInputStream(file));
        try {
            final FileOutputStream fos = new FileOutputStream(backup);
            final BufferedOutputStream output = new BufferedOutputStream(fos);
            try {
                StreamUtils.copyStream(in, output);

                //Flush and force the file to sync
                output.flush();
                fos.getFD().sync();
                fos.close();
            } finally {
                StreamUtils.safeClose(output);
            }
        } finally {
            StreamUtils.safeClose(in);
        }
    }

    protected void rename(File file, File to) throws IOException {
        if (!file.renameTo(to) && file.exists()) {
            copyFile(file, to);
            file.delete();
        }
    }

    /**
     * This creates a new audit log file with default permissions.
     *
     * @param file File to create
     */
    protected void createNewFile(final File file) {
        try {
            file.createNewFile();
            setFileNotWorldReadablePermissions(file);
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    /**
     * This procedure sets permissions to the given file to not allow everybody to read it.
     *
     * Only when underlying OS allows the change.
     *
     * @param file File to set permissions
     */
    private void setFileNotWorldReadablePermissions(File file) {
       file.setReadable(false, false);
       file.setWritable(false, false);
       file.setExecutable(false, false);
       file.setReadable(true, true);
       file.setWritable(true, true);
    }

}
