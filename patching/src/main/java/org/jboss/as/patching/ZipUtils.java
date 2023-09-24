/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching;

import static org.jboss.as.patching.IoUtils.copyStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.jboss.as.patching.logging.PatchLogger;

/**
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class ZipUtils {

    public static void zip(File sourceDir, File zipFile) {
        try (final FileOutputStream os = new FileOutputStream(zipFile);
             final ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os))
        ) {
            for (final File file : sourceDir.listFiles()) {
                if (file.isDirectory()) {
                    addDirectoryToZip(file, file.getName(), zos);
                } else {
                    addFileToZip(file, null, zos);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed creating patch file " + zipFile, e); // Only used for generation and tests
        }
    }

    private static void addDirectoryToZip(File dir, String dirName, ZipOutputStream zos) throws IOException {

        final ZipEntry dirEntry = new ZipEntry(dirName + "/");
        zos.putNextEntry(dirEntry);
        zos.closeEntry();

        File[] children = dir.listFiles();
        if (children != null) {
            for (File file : children) {
                if (file.isDirectory()) {
                    addDirectoryToZip(file, dirName + "/" + file.getName(), zos);
                } else {
                    addFileToZip(file, dirName, zos);
                }
            }
        }
    }

    private static void addFileToZip(File file, String parent, ZipOutputStream zos) throws IOException {
        try (final FileInputStream is = new FileInputStream(file)){
            final String entryName = parent == null ? file.getName() : parent + "/" + file.getName();
            zos.putNextEntry(new ZipEntry(entryName));

            try (final BufferedInputStream bis = new BufferedInputStream(is)){
                copyStream(bis, zos);
            }

            zos.closeEntry();
        }
    }

    /**
     * unpack...
     *
     * @param zip the zip
     * @param patchDir the patch dir
     * @throws IOException
     */
    public static void unzip(final File zip, final File patchDir) throws IOException {
        try (final ZipFile zipFile = new ZipFile(zip)){
            unzip(zipFile, patchDir);
        }
    }

    /**
     * unpack...
     *
     * @param zip the zip
     * @param patchDir the patch dir
     * @throws IOException
     */
    private static void unzip(final ZipFile zip, final File patchDir) throws IOException {
        final Enumeration<? extends ZipEntry> entries = zip.entries();
        while(entries.hasMoreElements()) {
            final ZipEntry entry = entries.nextElement();
            final String name = entry.getName();
            final File current = new File(patchDir, name);
            if (! current.getCanonicalFile().toPath().startsWith(patchDir.getCanonicalFile().toPath())) {
                throw PatchLogger.ROOT_LOGGER.entryOutsideOfPatchDirectory(current.getCanonicalPath());
            }
            if (entry.isDirectory()) {
                continue;
            } else {
                if(! current.getParentFile().exists()) {
                    current.getParentFile().mkdirs();
                }
                try (final InputStream eis = zip.getInputStream(entry)){
                    Files.copy(eis, current.toPath());
                    //copy(eis, current);
                }
            }
        }
    }

}
