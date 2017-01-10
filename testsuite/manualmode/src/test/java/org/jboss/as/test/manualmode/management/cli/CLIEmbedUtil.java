package org.jboss.as.test.manualmode.management.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.codehaus.plexus.util.FileUtils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Shared utilities for testing embedded standalone / host-controller
 *
 * @author Ken Wills (c) 2016 Red Hat Inc.
 */

public class CLIEmbedUtil {

    static void copyConfig(final File root, String baseDirName, String base, String newName, boolean requiresExists) throws IOException {
        File configDir = new File(root, baseDirName + File.separatorChar + "configuration");
        File baseFile = new File(configDir, base);
        assertTrue(!requiresExists || baseFile.exists());
        File newFile = new File(configDir, newName);
        Files.copy(baseFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);
    }

    static void delConfigFile(final File root, String baseDirName, String base) throws IOException {
        File configDir = new File(root, baseDirName + File.separatorChar + "configuration");
        File baseFile = new File(configDir, base);
        baseFile.delete();
    }

    static void copyServerBaseDir(final File root, final String baseDirName, final String newbaseDirName, boolean force) throws IOException {
        // copy the base server directory (standalone etc to a new name to test changing jboss.server.base.dir etc)
        final File baseDir = new File(root + File.separator + baseDirName);
        assertTrue(baseDir.exists());
        final File newBaseDir = new File(root + File.separator + newbaseDirName);
        assertFalse(!force && newBaseDir.exists());
        FileUtils.copyDirectoryStructure(baseDir, newBaseDir);
        assertTrue(newBaseDir.exists());

        // remove anything we'll auto-create on startup
        final String[] cleanDirs = {"content", "data", "deployments", "log", "tmp"};
        for (final String dir : cleanDirs) {
            FileUtils.deleteDirectory(root + File.separator + newbaseDirName + File.separator + dir);
        }
    }

    static List<String> getOutputLines(String raw) throws IOException {
        if (raw == null) {
            return Collections.emptyList();
        }
        BufferedReader br = new BufferedReader(new StringReader(raw));
        List<String> result = new ArrayList<>();
        String line;
        while ((line = br.readLine()) != null) {
            result.add(line);
        }
        return result;
    }
}
