/*
 * Copyright 2016 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.repository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import org.jboss.as.repository.logging.DeploymentRepositoryLogger;

/**
 * Utility class for manipulating Path.
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class PathUtil {

    /**
     * Copy a path recursively.
     * @param source a Path pointing to a file or a directory that must exist
     * @param target a Path pointing to a directory where the contents will be copied.
     * @param overwrite overwrite existing files - if set to false fails if the target file already exists.
     * @throws IOException
     */
    public static void copyRecursively(final Path source, final Path target, boolean overwrite) throws IOException {
        final CopyOption[] options;
        if (overwrite) {
            options = new CopyOption[]{StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING};
        } else {
            options = new CopyOption[]{StandardCopyOption.COPY_ATTRIBUTES};
        }
        Files.walkFileTree(source, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.copy(dir, target.resolve(source.relativize(dir)), options);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), options);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                DeploymentRepositoryLogger.ROOT_LOGGER.errorf(exc, "Error copying file %s", file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Delete a path recursively.
     * @param path a Path pointing to a file or a directory that may not exists anymore.
     * @throws IOException
     */
    public static void deleteRecursively(final Path path) throws IOException {
        DeploymentRepositoryLogger.ROOT_LOGGER.infof("Deleting %s recursively", path);
        if (Files.exists(path)) {
            Files.walkFileTree(path, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    DeploymentRepositoryLogger.ROOT_LOGGER.errorf(exc, "Error deleting file %s", file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    /**
     * Resolve a path from the rootPath checking that it doesn't go out of the rootPath.
     * @param rootPath the starting point for resolution.
     * @param path the path we want to resolve.
     * @return the resolved path.
     * @throws IllegalArgumentException if the resolved path is out of the rootPath or if the resolution failed.
     */
    public static final Path resolveSecurely(Path rootPath, String path) {
        Path resolvedPath;
        if(path == null || path.isEmpty()) {
            resolvedPath = rootPath.normalize();
        } else {
            resolvedPath = rootPath.resolve(path).normalize();
        }
        if(!resolvedPath.startsWith(rootPath))  {
            throw DeploymentRepositoryLogger.ROOT_LOGGER.forbiddenPath(path);
        }
        return resolvedPath;
    }

    /**
     * Test if the target path is an archive.
     * @param path path to the file.
     * @return true if the path points to a zip file - false otherwise.
     * @throws IOException
     */
    public static final boolean isArchive(Path path) throws IOException {
        if (Files.exists(path) && Files.isRegularFile(path)) {
            try {
                new ZipFile(path.toFile());
                return true;
            } catch (ZipException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * List files in a path according to the specified filter.
     * @param rootPath the path from which we are listing the files.
     * @param filter the filter to be applied.
     * @return the list of files / directory.
     * @throws IOException
     */
    public static List<String> listFiles(final Path rootPath, final ContentFilter filter) throws IOException {
        List<String> result = new ArrayList<>();
        if (Files.exists(rootPath)) {
            Files.walkFileTree(rootPath, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (filter.acceptFile(rootPath, file)) {
                        result.add(formatPath(rootPath.relativize(file)));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (filter.acceptDirectory(rootPath, dir)) {
                        String directoryPath = formatDirectoryPath(rootPath.relativize(dir));
                        if(! "/".equals(directoryPath)) {
                            result.add(directoryPath);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                private String formatDirectoryPath(Path path) {
                    return formatPath(path) + '/';
                }

                private String formatPath(Path path) {
                    return path.toString().replace(File.separatorChar, '/');
                }
            });
        }
        return result;
    }

    private static FileAttribute[] readFileAttributes(Path path) throws IOException {
        List<FileAttribute> attributes = new ArrayList<>(2);
        attributes.addAll(getPosixAttributes(path));
        attributes.addAll(getAclAttributes(path));
        if (!attributes.isEmpty()) {
            return attributes.toArray(new FileAttribute<?>[attributes.size()]);
        }
        return new FileAttribute[0];
    }

    private static List<FileAttribute<Set<PosixFilePermission>>> getPosixAttributes(Path file) throws IOException {
        if (Files.exists(file) && Files.getFileStore(file).supportsFileAttributeView(PosixFileAttributeView.class)) {
            PosixFileAttributeView posixView = Files.getFileAttributeView(file, PosixFileAttributeView.class);
            if (posixView != null) {
                return Collections.singletonList(PosixFilePermissions.asFileAttribute(posixView.readAttributes().permissions()));
            }
        }
        return Collections.emptyList();
    }

    private static List<FileAttribute<List<AclEntry>>> getAclAttributes(Path file) throws IOException {
        if (Files.exists(file) && Files.getFileStore(file).supportsFileAttributeView(AclFileAttributeView.class)) {
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

    /**
     * Create a temporary directory with the same attributes as its parent directory.
     * @param   dir
     *          the path to directory in which to create the directory
     * @param   prefix
     *          the prefix string to be used in generating the directory's name;
     *          may be {@code null}
     * @return  the path to the newly created directory that did not exist before
     *          this method was invoked
     * @throws IOException
     */
    public static Path createTempDirectory(Path dir, String prefix) throws IOException {
        try {
            return Files.createTempDirectory(dir, prefix, PathUtil.readFileAttributes(dir));
        } catch (UnsupportedOperationException ex) {
        }
        return Files.createTempDirectory(dir, prefix);
    }

    /**
     * Unzip a file to a target directory.
     * @param zip the path to the zip file.
     * @param target the path to the target directory into which the zip file will be unzipped.
     * @throws IOException
     */
    public static void unzip(Path zip, Path target) throws IOException {
        try (final ZipFile zipFile = new ZipFile(zip.toFile())){
            unzip(zipFile, target);
        }
    }

    private static void unzip(final ZipFile zip, final Path targetDir) throws IOException {
        final Enumeration<? extends ZipEntry> entries = zip.entries();
        while(entries.hasMoreElements()) {
            final ZipEntry entry = entries.nextElement();
            final String name = entry.getName();
            final Path current = targetDir.resolve(name);
            if (entry.isDirectory()) {
                if (!Files.exists(current)) {
                    Files.createDirectories(current);
                }
            } else {
                if (!Files.exists(current.getParent())) {
                    Files.createDirectories(current.getParent());
                }
                try (final InputStream eis = zip.getInputStream(entry)) {
                    Files.copy(eis, current);
                }
            }
            Files.getFileAttributeView(current, BasicFileAttributeView.class).setTimes(entry.getLastModifiedTime(), entry.getLastAccessTime(), entry.getCreationTime());
        }
    }
}
