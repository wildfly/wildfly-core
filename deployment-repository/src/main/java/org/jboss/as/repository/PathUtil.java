/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.repository;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

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
                Path targetDir = target.resolve(source.relativize(dir));
                if(! Files.exists(targetDir)) {
                    Files.copy(dir, targetDir, options);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), options);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                DeploymentRepositoryLogger.ROOT_LOGGER.cannotCopyFile(exc, file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Delete a path recursively, not throwing Exception if it fails or if the path is null.
     * @param path a Path pointing to a file or a directory that may not exists anymore.
     */
    public static void deleteSilentlyRecursively(final Path path) {
        if (path != null) {
            try {
                deleteRecursively(path);
            } catch (IOException ioex) {
                DeploymentRepositoryLogger.ROOT_LOGGER.cannotDeleteFile(ioex, path);
            }
        }
    }

    /**
     * Delete a path recursively.
     * @param path a Path pointing to a file or a directory that may not exists anymore.
     * @throws IOException
     */
    public static void deleteRecursively(final Path path) throws IOException {
        DeploymentRepositoryLogger.ROOT_LOGGER.debugf("Deleting %s recursively", path);
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
                    DeploymentRepositoryLogger.ROOT_LOGGER.cannotDeleteFile(exc, path);
                    throw exc;
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
            String relativePath = removeSuperflousSlashes(path);
            resolvedPath = rootPath.resolve(relativePath).normalize();
        }
        if(!resolvedPath.startsWith(rootPath)) {
            throw DeploymentRepositoryLogger.ROOT_LOGGER.forbiddenPath(path);
        }
        return resolvedPath;
    }

    private static String removeSuperflousSlashes(String path) {
        if (path.startsWith("/")) {
            return removeSuperflousSlashes(path.substring(1));
        }
        return path;
    }

    /**
     * Test if the target path is an archive.
     * @param path path to the file.
     * @return true if the path points to a zip file - false otherwise.
     * @throws IOException
     */
    public static final boolean isArchive(Path path) throws IOException {
        if (Files.exists(path) && Files.isRegularFile(path)) {
            try (ZipFile zip = new ZipFile(path.toFile())){
                return true;
            } catch (ZipException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Test if the target path is an archive.
     * @param in stream that reads the target path
     * @return true if the path points to a zip file - false otherwise.
     * @throws IOException
     */
    public static final boolean isArchive(InputStream in) throws IOException {
        if (in != null) {
            try (ZipInputStream zip = new ZipInputStream(in)){
                return zip.getNextEntry() != null;
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
    public static List<ContentRepositoryElement> listFiles(final Path rootPath, Path tempDir, final ContentFilter filter) throws IOException {
        List<ContentRepositoryElement> result = new ArrayList<>();
        if (Files.exists(rootPath)) {
            if(isArchive(rootPath)) {
                return listZipContent(rootPath, filter);
            }
            Files.walkFileTree(rootPath, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (filter.acceptFile(rootPath, file)) {
                        result.add(ContentRepositoryElement.createFile(formatPath(rootPath.relativize(file)), Files.size(file)));
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
                            result.add(ContentRepositoryElement.createFolder(directoryPath));
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
        } else {
            Path file = getFile(rootPath);
            if(isArchive(file)) {
                Path relativePath = file.relativize(rootPath);
                Path target = createTempDirectory(tempDir, "unarchive");
                unzip(file, target);
                return listFiles(target.resolve(relativePath), tempDir, filter);
            } else {
                throw new FileNotFoundException(rootPath.toString());
            }
        }
        return result;
    }

    private static List<ContentRepositoryElement> listZipContent(final Path zipFilePath, final ContentFilter filter) throws IOException {
        List<ContentRepositoryElement> result = new ArrayList<>();
        try (final ZipFile zip = new ZipFile(zipFilePath.toFile())) {
            final Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry entry = entries.nextElement();
                final String name = entry.getName();
                Path entryPath = zipFilePath.resolve(name);
                if (entry.isDirectory()) {
                    if(filter.acceptDirectory(zipFilePath, entryPath)) {
                        result.add(ContentRepositoryElement.createFolder(name));
                    }
                } else {
                    try (InputStream in = zip.getInputStream(entry)) {
                        if (filter.acceptFile(zipFilePath, entryPath, in)) {
                            result.add(ContentRepositoryElement.createFile(name, entry.getSize()));
                        }
                    }
                }
            }
        }
        return result;
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
            return Files.createTempDirectory(dir, prefix);
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
        while (entries.hasMoreElements()) {
            final ZipEntry entry = entries.nextElement();
            final String name = entry.getName();
            final Path current = resolveSecurely(targetDir, name);
            if (entry.isDirectory()) {
                if (!Files.exists(current)) {
                    Files.createDirectories(current);
                }
            } else {
                if (Files.notExists(current.getParent())) {
                    Files.createDirectories(current.getParent());
                }
                try (final InputStream eis = zip.getInputStream(entry)) {
                    Files.copy(eis, current);
                }
            }
            try {
                Files.getFileAttributeView(current, BasicFileAttributeView.class).setTimes(entry.getLastModifiedTime(), entry.getLastAccessTime(), entry.getCreationTime());
            } catch (IOException e) {
                //ignore, if we cannot set it, world will not end
            }
        }
    }

    public static String getFileExtension(Path path) {
        String fileName = path.getFileName().toString();
        int separator = fileName.lastIndexOf('.');
        if(separator > 0) {
            return fileName.substring(separator);
        }
        return "";
    }

    public static Path readFile(Path src, Path tempDir) throws IOException {
        if(isFile(src)) {
            return src;
        } else {
            Path file = getFile(src);
            if(isArchive(file)) {
                Path relativePath = file.relativize(src);
                Path target = createTempDirectory(tempDir, "unarchive");
                unzip(file, target);
                return readFile(target.resolve(relativePath), tempDir);
            } else {
                throw new FileNotFoundException(src.toString());
            }
        }
    }

    private static Path getFile(Path src) throws FileNotFoundException {
        if (src.getNameCount() > 1) {
            Path parent = src.getParent();
            if (isFile(parent)) {
                return parent;
            }
            return getFile(parent);
        }
        throw new FileNotFoundException(src.toString());
    }

    private static boolean isFile(Path src) {
        return Files.exists(src) && Files.isRegularFile(src);
    }
}
