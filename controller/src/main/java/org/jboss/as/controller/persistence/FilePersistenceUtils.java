/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.persistence;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.xnio.IoUtils;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class FilePersistenceUtils {
    static final int[] ILLEGAL_CHARS = {34, 60, 62, 124, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 58, 42, 63, 92, 47};
    static {
        Arrays.sort(ILLEGAL_CHARS);
    }

    static String sanitizeFileName(String name) {
        if(name == null || name.isEmpty()) {
            return "";
        }
        StringBuilder cleanName = new StringBuilder();
        int len = name.codePointCount(0, name.length());
        for (int i = 0; i < len; i++) {
            int c = name.codePointAt(i);
            if (Arrays.binarySearch(ILLEGAL_CHARS, c) < 0) {
                cleanName.appendCodePoint(c);
            }
        }
        try {
            return new File(cleanName.toString()).getCanonicalFile().getName();
        } catch (IOException ex) {
            return "";
        }
    }

    static File createTempFile(File fileName) {
        return createTempFile(fileName.getParentFile(), fileName.getName());
    }

    static File createTempFile(File fileFolder, String fileName) {
        return new File(fileFolder, fileName + ".tmp");
    }

    static ExposedByteArrayOutputStream marshalXml(final AbstractConfigurationPersister persister, final ModelNode model) throws ConfigurationPersistenceException {
        ExposedByteArrayOutputStream marshalled = new ExposedByteArrayOutputStream(1024 * 8);
        try {
            try {
                BufferedOutputStream output = new BufferedOutputStream(marshalled);
                persister.marshallAsXml(model, output);
                output.close();
                marshalled.close();
            } finally {
                IoUtils.safeClose(marshalled);
            }
        } catch (Exception e) {
            throw ControllerLogger.ROOT_LOGGER.failedToMarshalConfiguration(e);
        }
        return marshalled;
    }

    static void copyFile(final File file, final File backup) throws IOException {
        Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
    }

    static void moveTempFileToMain(File tempFileName, File fileName) throws ConfigurationPersistenceException {
        //Rename the temp file written to the target file
        try {
            Files.move(tempFileName.toPath(), fileName.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw ControllerLogger.ROOT_LOGGER.failedToRenameTempFile(e, tempFileName, fileName);
        }
    }

    static void deleteFile(File file) {
        if (file.exists()) {
            if (!file.delete() && file.exists()) {
                file.deleteOnExit();
                throw new IllegalStateException(ControllerLogger.ROOT_LOGGER.couldNotDeleteFile(file));
            }
        }
    }

    static File writeToTempFile(InputStream is, File tempFileName, File fileName) throws IOException {
        Path targetPath = tempFileName.toPath();
        deleteFile(tempFileName);
        try {
            createTempFileWithAttributes(targetPath, fileName);
        } catch (IOException ioex) {
            ControllerLogger.ROOT_LOGGER.error(ioex.getLocalizedMessage(), ioex);
        }
        Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        return tempFileName;
    }

    static Path createTempFileWithAttributes(Path tempFilePath, File fileName) throws IOException {
        Path exisitingFilePath = fileName.toPath();
        List<FileAttribute> attributes = new ArrayList<>(2);
        attributes.addAll(getPosixAttributes(exisitingFilePath));
        attributes.addAll(getAclAttributes(exisitingFilePath));
        if (!attributes.isEmpty()) {
            try {
                return Files.createFile(tempFilePath, attributes.toArray(new FileAttribute<?>[attributes.size()]));
            } catch (UnsupportedOperationException ex) {
            }
        }
        return Files.createFile(tempFilePath);
    }

    private static List<FileAttribute<Set<PosixFilePermission>>> getPosixAttributes(Path file) throws IOException {
        if (Files.exists(file) && supportsFileOwnerAttributeView(file, PosixFileAttributeView.class)) {
            PosixFileAttributeView posixView = Files.getFileAttributeView(file, PosixFileAttributeView.class);
            if (posixView != null) {
                return Collections.singletonList(PosixFilePermissions.asFileAttribute(posixView.readAttributes().permissions()));
            }
        }
        return Collections.emptyList();
    }

    private static List<FileAttribute<List<AclEntry>>> getAclAttributes(Path file) throws IOException {
        if (Files.exists(file) && supportsFileOwnerAttributeView(file, AclFileAttributeView.class)) {
            AclFileAttributeView aclView = Files.getFileAttributeView(file, AclFileAttributeView.class);
            if (aclView != null) {
                final List<AclEntry> entries = aclView.getAcl();
                return Collections.singletonList(new FileAttribute<List<AclEntry>>() {

                    @Override
                    public List<AclEntry> value() {
                        return entries;
                    }

                    @Override
                    public String name() {
                        return "acl:acl";
                    }

                });
            }
        }
        return Collections.emptyList();
    }

    private static boolean supportsFileOwnerAttributeView(Path path, Class<? extends FileOwnerAttributeView> view) {
        FileStore store;
        try {
            store = Files.getFileStore(path);
        } catch (IOException e) {
            return false;
        }
        return store.supportsFileAttributeView(view);
    }

    static boolean isParentFolderWritable(File file){
        if ( !file.exists() || file.getParentFile() == null ){
            return false;
        }
        return Files.isWritable(file.getParentFile().toPath());
    }
}
