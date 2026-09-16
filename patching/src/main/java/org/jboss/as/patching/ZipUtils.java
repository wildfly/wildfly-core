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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 *
 * @deprecated will be removed via WFCORE-7696 once the sole use in the full WildFly testsuite is removed
 */
@Deprecated(forRemoval = true)
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

}
