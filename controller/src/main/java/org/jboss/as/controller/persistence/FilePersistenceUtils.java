/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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

    static File writeToTempFile(ExposedByteArrayOutputStream marshalled, File tempFileName, File fileName) throws IOException {
        Path targetPath = tempFileName.toPath();
        deleteFile(tempFileName);
        createTempFileWithAttributes(targetPath, fileName);
        try (InputStream is = marshalled.getInputStream()) {
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
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
        return  file.getParentFile().canWrite();
    }
}
