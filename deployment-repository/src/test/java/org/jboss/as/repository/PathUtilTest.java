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

import static org.jboss.as.repository.ContentFilter.ALL;
import static org.jboss.as.repository.PathUtil.deleteRecursively;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jboss.as.protocol.StreamUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for PathUtil.
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class PathUtilTest {

    private final Path root = new File("target", "temp").toPath().resolve("pathutil");

    public PathUtilTest() {
    }

    @Before
    public void createRepository() throws IOException {
        if (Files.exists(root)) {
            deleteRecursively(root);
        }
        Files.createDirectories(root);
    }

    @After
    public void destroyRepository() throws IOException {
        deleteRecursively(root);
    }

    /**
     * Test of resolveSecurely method, of class PathUtil.
     */
    @Test
    public void testResolveSecurely() throws Exception {
        Path result = PathUtil.resolveSecurely(root, "../pathutil/test");
        Assert.assertEquals(root.resolve("test"), result);
        try {
            PathUtil.resolveSecurely(root, "../test");
            Assert.fail("We shouldn't be able to go out");
        } catch (IllegalArgumentException ioex) {
            Assert.assertTrue(ioex.getMessage().contains("WFLYDR0013"));
        }
        try {
            PathUtil.resolveSecurely(root, "test/../../test");
            Assert.fail("We shouldn't be able to go out");
        } catch (IllegalArgumentException ioex) {
            Assert.assertTrue(ioex.getMessage().contains("WFLYDR0013"));
        }
    }

    /**
     * Test of isArchive method, of class PathUtil.
     */
    @Test
    public void testIsArchive() throws Exception {
        Path archive = root.resolve("test.zip");
        try (OutputStream fileOut = Files.newOutputStream(archive)) {
            try (ZipOutputStream out = new ZipOutputStream(fileOut)) {
                ZipEntry entry = new ZipEntry("overlay.xhtml");
                out.putNextEntry(entry);
                StreamUtils.copyStream(this.getClass().getClassLoader().getResourceAsStream("overlay.xhtml"), out);
                out.closeEntry();
            }
        }
        Path simpleFile = root.resolve("overlay.zip");
        Files.copy(this.getClass().getClassLoader().getResourceAsStream("overlay.xhtml"), simpleFile);
        Assert.assertTrue(PathUtil.isArchive(archive));
        Assert.assertFalse(PathUtil.isArchive(root));
        Assert.assertFalse(PathUtil.isArchive(simpleFile));
    }

    /**
     * Test of listFiles method, of class PathUtil.
     */
    @Test
    public void testListFiles() throws Exception {
        Path archive = root.resolve("test.zip");
        try (OutputStream fileOut = Files.newOutputStream(archive)) {
            try (ZipOutputStream out = new ZipOutputStream(fileOut)) {
                ZipEntry entry = new ZipEntry("overlay.xhtml");
                out.putNextEntry(entry);
                StreamUtils.copyStream(this.getClass().getClassLoader().getResourceAsStream("overlay.xhtml"), out);
                out.closeEntry();
            }
        }
        Files.createDirectory(root.resolve("zip"));
        archive = root.resolve("zip").resolve("test.zip");
        try (OutputStream fileOut = Files.newOutputStream(archive)) {
            try (ZipOutputStream out = new ZipOutputStream(fileOut)) {
                ZipEntry entry = new ZipEntry("overlay.xhtml");
                out.putNextEntry(entry);
                StreamUtils.copyStream(this.getClass().getClassLoader().getResourceAsStream("overlay.xhtml"), out);
                out.closeEntry();
            }
        }
        Files.copy(this.getClass().getClassLoader().getResourceAsStream("overlay.xhtml"), root.resolve("overlay.xhtml"));
        Files.createDirectories(root.resolve("empty").resolve("directory"));
        Files.createDirectories(root.resolve("htdocs").resolve("www"));
        Files.copy(this.getClass().getClassLoader().getResourceAsStream("overlay.xhtml"), root.resolve("htdocs").resolve("www").resolve("overlay.xhtml"));
        List<String> result = PathUtil.listFiles(root, ALL);
        Assert.assertEquals(9, result.size());
        Assert.assertTrue(result.contains("overlay.xhtml"));
        Assert.assertTrue(result.contains("test.zip"));
        Assert.assertTrue(result.contains("zip/test.zip"));
        Assert.assertTrue(result.contains("zip/"));
        Assert.assertTrue(result.contains("empty/directory/"));
        Assert.assertTrue(result.contains("empty/"));
        Assert.assertTrue(result.contains("htdocs/www/overlay.xhtml"));
        Assert.assertTrue(result.contains("htdocs/www/"));
        Assert.assertTrue(result.contains("htdocs/"));
        result = PathUtil.listFiles(root, ContentFilter.Factory.explodableFileFilter(true));
        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.contains("test.zip"));
        Assert.assertTrue(result.contains("zip/test.zip"));
        result = PathUtil.listFiles(root, ContentFilter.Factory.directoryListeFileFilter());
        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.contains("overlay.xhtml"));
        Assert.assertTrue(result.contains("test.zip"));
        result = PathUtil.listFiles(root, ContentFilter.Factory.createContentFilter(2, true));
        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.contains("test.zip"));
        Assert.assertTrue(result.contains("zip/test.zip"));
        result = PathUtil.listFiles(root, ContentFilter.Factory.createContentFilter(2, false));
        Assert.assertEquals(8, result.size());
        Assert.assertTrue(result.contains("overlay.xhtml"));
        Assert.assertTrue(result.contains("test.zip"));
        Assert.assertTrue(result.contains("zip/"));
        Assert.assertTrue(result.contains("zip/test.zip"));
        Assert.assertTrue(result.contains("empty/"));
        Assert.assertTrue(result.contains("empty/directory/"));
        Assert.assertTrue(result.contains("htdocs/www/"));
        Assert.assertTrue(result.contains("htdocs/"));
        result = PathUtil.listFiles(root, ContentFilter.Factory.createContentFilter(3, false));
        Assert.assertEquals(9, result.size());
        Assert.assertTrue(result.contains("overlay.xhtml"));
        Assert.assertTrue(result.contains("test.zip"));
        Assert.assertTrue(result.contains("zip/"));
        Assert.assertTrue(result.contains("zip/test.zip"));
        Assert.assertTrue(result.contains("empty/"));
        Assert.assertTrue(result.contains("empty/directory/"));
        Assert.assertTrue(result.contains("htdocs/"));
        Assert.assertTrue(result.contains("htdocs/www/"));
        Assert.assertTrue(result.contains("htdocs/www/overlay.xhtml"));
    }

    /**
     * Test of createTempDirectory method, of class PathUtil.
     */
    @Test
    public void testCreateTempDirectory() throws Exception {
        Path result = PathUtil.createTempDirectory(root, "test");
        Assert.assertTrue(Files.exists(result));
        Assert.assertTrue(Files.isDirectory(result));
        Assert.assertTrue(result.getFileName().toString().startsWith("test"));
    }

    /**
     * Test of unzip method, of class PathUtil.
     */
    @Test
    public void testUnzip() throws Exception {
        Path archive = root.resolve("test.zip");
        Path target = root.resolve("target");
        try (OutputStream fileOut = Files.newOutputStream(archive)) {
            try (ZipOutputStream out = new ZipOutputStream(fileOut)) {
                ZipEntry entry = new ZipEntry("overlay.xhtml");
                out.putNextEntry(entry);
                StreamUtils.copyStream(this.getClass().getClassLoader().getResourceAsStream("overlay.xhtml"), out);
                out.closeEntry();
            }
        }
        PathUtil.unzip(archive, target);
        Assert.assertTrue(Files.exists(target));
        Path explodedFile = target.resolve("overlay.xhtml");
        Assert.assertTrue(Files.exists(explodedFile));
        Assert.assertTrue(Files.isRegularFile(explodedFile));
    }
}
