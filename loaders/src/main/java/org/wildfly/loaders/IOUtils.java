/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2015, Red Hat, Inc., and individual contributors
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

package org.wildfly.loaders;

import org.jboss.modules.Resource;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class IOUtils {

    private static final String CURRENT_PATH = ".";
    private static final String REVERSE_PATH = "..";

    private IOUtils() {
        // forbidden instantiation
    }

    static void copy(final InputStream is, final OutputStream os) throws IOException {
        final byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) > 0) {
            os.write(buffer, 0, len);
        }
    }

    static void copyAndClose(final InputStream is, final OutputStream os) throws IOException {
        try {
            copy(is, os);
        } finally {
            safeClose(is);
            safeClose(os);
        }
    }

    static void safeClose(final Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (final Throwable t) {
            // ignored
        }
    }

    static void safeClose(final AutoCloseable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (final Throwable t) {
            // ignored
        }
    }

    static boolean delete(final File file) {
        if (file == null) return true;
        if (!file.exists()) return true;
        if (file.isDirectory()) {
            for (final File child : file.listFiles()) {
                if (child.isDirectory()) {
                    delete(child);
                } else {
                    child.delete();
                }
            }
        }
        return file.delete();
    }

    static void unzip(final File zipFile, final File targetDir) throws IOException {
        unzip(new FileInputStream(zipFile), targetDir);
    }

    static void unzip(final InputStream is, final File targetDir) throws IOException {
        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(is);
            ZipEntry entry;
            File newFile;
            while ((entry = zis.getNextEntry()) != null) {
                String fileName = entry.getName();
                if (fileName.equals(CURRENT_PATH) || fileName.equals(REVERSE_PATH)) continue;
                // create directory structure
                newFile = new File(targetDir + File.separator + fileName);
                new File(newFile.getParent()).mkdirs();
                // extract zip
                if (!entry.isDirectory()) {
                    // extract zip entry to the disk
                    FileOutputStream fos = null;
                    try {
                        fos = new FileOutputStream(newFile);
                        copy(zis, fos);
                    } finally {
                        safeClose(fos);
                    }
                }
            }
        } finally {
            safeClose(zis);
        }
    }

    static void unzip(final Iterator<Resource> zipResources, final File targetDir, final int prefixLength) throws IOException {
        Resource resource;
        File newFile;
        while (zipResources.hasNext()) {
            // obtain resource
            resource = zipResources.next();
            String fileName = resource.getName().substring(prefixLength);
            // create directory structure
            newFile = new File(targetDir + File.separator + fileName);
            new File(newFile.getParent()).mkdirs();
            // extract zip entry to the disk
            FileOutputStream fos = null;
            InputStream is = null;
            try {
                fos = new FileOutputStream(newFile);
                is = resource.openStream();
                copy(is, fos);
            } finally {
                safeClose(fos);
                safeClose(is);
            }
        }
    }
}
