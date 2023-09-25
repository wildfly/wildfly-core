/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.client.old.server.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.xnio.IoUtils;

/**
 * @author Kabir Khan
 */
public class OldVersionCopier {

    static String OLD_VERSIONS_DIR = "jboss.test.client.old.server.dir";

    private final Version.AsVersion version;
    private final File oldVersionsBaseDir;
    private final File targetOldVersions = new File("target/old-versions/");


    private OldVersionCopier(Version.AsVersion version, File oldVersionsBaseDir) {
        this.version = version;
        this.oldVersionsBaseDir = oldVersionsBaseDir;
    }

    static File expandOldVersion(Version.AsVersion version) {
        OldVersionCopier copier = new OldVersionCopier(version, obtainOldVersionsDir());
        return copier.expandAsInstance(version);
    }


    private static File obtainOldVersionsDir() {
        String error = "System property '" + OLD_VERSIONS_DIR + "' must be set to a directory containing old versions";
        String oldVersionsDir = System.getProperty(OLD_VERSIONS_DIR);
        if (oldVersionsDir == null) {
            throw new RuntimeException(error);
        }
        File file = new File(oldVersionsDir);
        if (!file.exists() || !file.isDirectory()) {
            throw new RuntimeException(error);
        }
        return file;
    }


    private File expandAsInstance(Version.AsVersion version) {
        createIfNotExists(targetOldVersions);

        File file = new File(oldVersionsBaseDir, version.getZipFileName());
        if (!file.exists()) {
            throw new RuntimeException("Old version not found in " + file.getAbsolutePath());
        }
        try {
            File expanded = expandAsInstance(file);
            return expanded;
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    private File expandAsInstance(final File file) throws Exception {
        File versionDir = new File(targetOldVersions, version.getFullVersionName());
        createIfNotExists(versionDir);

        final ZipFile zipFile = new ZipFile(file);
        try {
            for (Enumeration<? extends ZipEntry> en = zipFile.entries() ; en.hasMoreElements() ; ) {
                final ZipEntry entry = en.nextElement();
                final File output = new File(versionDir, entry.getName());
                if (entry.isDirectory()) {
                    createIfNotExists(output);
                } else {
                    inputStreamToFile(zipFile.getInputStream(entry), output);
                }

            }
        } finally {
            IoUtils.safeClose(zipFile);
        }

        File[] files = versionDir.listFiles();
        if (files.length != 1) {
            //If this really becomes a problem, inspect the directory structures
            throw new RuntimeException("The unzipped file contains more than one file in " + versionDir.getAbsolutePath() + ". Unable to determine the true distribution");
        }
        return files[0];
    }

    private void inputStreamToFile(InputStream input, File output) throws Exception {
        final InputStream in = new BufferedInputStream(input);
        try {
            final OutputStream out = new BufferedOutputStream(new FileOutputStream(output));
            try {
                byte[] buf = new byte[1024];
                int len = in.read(buf);
                while (len != -1) {
                    out.write(buf, 0, len);
                    len = in.read(buf);
                }
            } finally {
                IoUtils.safeClose(out);
            }
        } finally {
            IoUtils.safeClose(in);
        }
    }

    private void createIfNotExists(File file) {
        if (!file.exists()) {
            if (!file.mkdirs() && file.exists()) {
                throw new RuntimeException("Could not create " + targetOldVersions);
            }
        }
    }
}