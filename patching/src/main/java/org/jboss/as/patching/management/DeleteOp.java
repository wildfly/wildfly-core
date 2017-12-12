/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.patching.management;

import org.jboss.as.patching.logging.PatchLogger;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Collection;

/**
 * Recursively removes files from a given directory. If any of the files couldn't be removed, it restores all deleted files.
 *
 * @author Bartosz Spyrko-Smietanko
 */
class DeleteOp {
    public static final String BACKUP_FOLDER = ".bkp";
    private final File fileToDelete;
    private final FilenameFilter fileFilter;
    private final File backupRoot;

    DeleteOp(File toDelete, FilenameFilter fileFilter) {
        this.fileToDelete = toDelete;
        this.fileFilter = fileFilter;

        backupRoot = new File(toDelete.getParentFile(), BACKUP_FOLDER);
    }

    /**
     * remove files from directory. All-or-nothing operation - if any of the files fails to be removed, all deleted files are restored.
     *
     * The operation is performed in two steps - first all the files are moved to a backup folder, and afterwards backup folder is removed.
     * If an error occurs in the first step of the operation, all files are restored and the operation ends with status {@code false}.
     * If an error occurs in the second step, the operation ends with status {@code false}, but the files are not rolled back.
     *
     * @throws IOException if an error occurred
     */
    public void execute() throws IOException {
        try {
            prepare();

            boolean commitResult = commit();
            if (commitResult == false) {
                throw PatchLogger.ROOT_LOGGER.failedToDeleteBackup();
            }
        } catch (PrepareException pe){
            rollback();

            throw PatchLogger.ROOT_LOGGER.failedToDelete(pe.getPath());
        }

    }

    /**
     * performs all delete operations together - if error occurs in one of them, all the operations are rolled back.
     * For details see {@link DeleteOp#execute()}
     *
     * @param deleteOps list of {@code DeleteOp} to perform
     * @throws IOException if an error occurred
     */
    public static void execute(Collection<DeleteOp> deleteOps) throws IOException {
        try {
            for (DeleteOp deleteOp : deleteOps) {
                deleteOp.prepare();
            }

            // best effort cleanup - delete what's possible and report error if anything remains
            boolean commitResult = true;
            for (DeleteOp op : deleteOps) {
                commitResult &= op.commit();
            }
            if (!commitResult) {
                throw PatchLogger.ROOT_LOGGER.failedToDeleteBackup();
            }

        } catch (PrepareException pe) {
            for (DeleteOp deleteOp : deleteOps) {
                deleteOp.rollback();
            }

            throw PatchLogger.ROOT_LOGGER.failedToDelete(pe.getPath());
        }

    }

    private void prepare() throws PrepareException {
        backupRoot.mkdir();

        moveToBackup(fileToDelete, backupRoot, fileFilter);
    }

    private void moveToBackup(File file, File bkp, FilenameFilter fileFilter) throws PrepareException {
        if (!fileFilter.accept(file.getParentFile(), file.getName())) {
            // the file is ignored - do nothing
            return;
        }

        if (file.isDirectory()) {

            final File backupFolder = createBackupFolder(file, bkp);
            for (File child : file.listFiles(fileFilter)) {
                moveToBackup(child, backupFolder, fileFilter);
            }
            if (isEmptyFolder(file)) {
                boolean deleted = file.delete();
                if (!deleted) {
                    throw new PrepareException(file);
                }
            }
        } else {
            final File dest = new File(bkp, file.getName());
            boolean moved = file.renameTo(dest);
            if (!moved) {
                throw new PrepareException(file);
            }
        }
    }

    private File createBackupFolder(File file, File bkp) throws PrepareException {
        final File backupFolder = new File(bkp, file.getName());
        if (backupFolder.exists() || !backupFolder.mkdir()) {
            throw new PrepareException(file);
        }
        return backupFolder;
    }

    private static boolean isEmptyFolder(File file) {
        return file.list() != null && file.list().length == 0;
    }

    private boolean commit() {
        final File deleteRoot = new File(backupRoot, fileToDelete.getName());

        boolean commitResult = true;

        if (deleteRoot.exists()) {
            commitResult = doDelete(deleteRoot);
        }

        if (isEmptyFolder(backupRoot)) {
            commitResult &= backupRoot.delete();
        }

        return commitResult;
    }

    private boolean doDelete(File file) {
        boolean result = true;
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                result &= doDelete(child);
            }
        }
        if (!file.delete()) {
            PatchLogger.ROOT_LOGGER.cannotDeleteFile(file.getAbsolutePath());
            result = false;
        }
        return result;
    }

    private void rollback() {
        try {
            if (!backupRoot.exists()) {
                return; // it's OK - no files were originally backed up
            }
            final File source = new File(backupRoot, fileToDelete.getName());
            doRollback(source, fileToDelete);

            if (isEmptyFolder(backupRoot)) {
                backupRoot.delete();
            }
        } catch (RollbackException e) {
            PatchLogger.ROOT_LOGGER.deleteRollbackError(e.getPath(), e.getMessage());
        }
    }

    private static void doRollback(File source, File destination) throws RollbackException {
        if (source.isDirectory()) {
            if (!destination.exists()) {
                destination.mkdir();
            } else if (!destination.isDirectory()) {
                throw new RollbackException(source, "file with the same name exists");
            }

            for (File child : source.listFiles()) {
                doRollback(child, new File(destination, child.getName()));
            }
            if (isEmptyFolder(source)) {
                if (!source.delete()) {
                    throw new RollbackException(source, "unable to delete folder");
                }
            } else {
                throw new RollbackException(source, "directory has unexpected files");
            }
        } else {
            if (destination.exists()) {
                throw new RollbackException(source, "file with the same name already exists");
            }
            if (!source.renameTo(destination)) {
                throw new RollbackException(source, "unable to move file");
            }
        }
    }

    private static class PrepareException extends RuntimeException {
        private final String path;

        public PrepareException(File file) {
            this.path = file.getAbsolutePath();
        }

        public String getPath() {
            return path;
        }
    }

    private static class RollbackException extends Exception {
        private String path;

        public RollbackException(File source, String msg) {
            super(msg);
            this.path = source.getAbsolutePath();
        }

        public String getPath() {
            return path;
        }
    }
}
