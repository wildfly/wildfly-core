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

import static org.jboss.as.repository.ContentFilter.Factory.createFileFilter;
import static org.jboss.as.repository.PathUtil.deleteRecursively;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    ContentFilter ALL = new ContentFilter() {
        @Override
        public boolean acceptFile(Path rootPath, Path file) throws IOException {
            return true;
        }

        @Override
        public boolean acceptFile(Path rootPath, Path file, InputStream in) throws IOException {
            return true;
        }

        @Override
        public boolean acceptDirectory(Path rootPath, Path dir) throws IOException {
            return true;
        }
    };

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
        result = PathUtil.resolveSecurely(root, "/index.html");
        Assert.assertEquals(root.resolve("index.html"), result);
        result = PathUtil.resolveSecurely(root, "///index.html");
        Assert.assertEquals(root.resolve("index.html"), result);
    }

    /**
     * Test of isArchive method, of class PathUtil.
     */
    @Test
    public void testIsArchive() throws Exception {
        Path archive = root.resolve("test.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive))) {
            ZipEntry entry = new ZipEntry("testfile.xhtml");
            out.putNextEntry(entry);
            try (InputStream in = this.getClass().getClassLoader().getResourceAsStream("testfile.xhtml")) {
                StreamUtils.copyStream(in, out);
            }
            out.closeEntry();
        }
        Path simpleFile = root.resolve("overlay.zip");
        Files.copy(this.getClass().getClassLoader().getResourceAsStream("testfile.xhtml"), simpleFile);
        Assert.assertTrue(PathUtil.isArchive(archive));
        Assert.assertFalse(PathUtil.isArchive(root));
        Assert.assertFalse(PathUtil.isArchive(simpleFile));
    }

    /**
     * Test of isArchive method, of class PathUtil.
     */
    @Test
    public void testReadFile() throws Exception {
        Path archive = root.resolve("test.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive))) {
            ZipEntry entry = new ZipEntry("testfile.xhtml");
            out.putNextEntry(entry);
            try (InputStream in = this.getClass().getClassLoader().getResourceAsStream("testfile.xhtml")) {
                StreamUtils.copyStream(in, out);
            }
            out.closeEntry();
            entry = new ZipEntry("single/test/test.html");
            out.putNextEntry(entry);
            try (InputStream in = this.getClass().getClassLoader().getResourceAsStream("testfile.xhtml")) {
                StreamUtils.copyStream(in, out);
            }
            out.closeEntry();
        }
        try (InputStream in = this.getClass().getClassLoader().getResourceAsStream("testfile.xhtml")) {
            String content = readFileContent(in);
            Assert.assertEquals(readFileContent(PathUtil.readFile(archive.resolve("testfile.xhtml"), root)), content);
            Assert.assertEquals(readFileContent(PathUtil.readFile(archive.resolve("single/test/test.html"), root)), content);
        }
        try {
            readFileContent(PathUtil.readFile(archive.resolve("single/test.html"), root));
            Assert.fail("Shouldn't find a file at " + archive.resolve("single/test.html"));
        } catch (FileNotFoundException ex) {
        }
    }

    private String readFileContent(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes, "UTF-8");
    }

    private String readFileContent(InputStream in) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8];
            int length = 8;
            while ((length = in.read(buffer, 0, length)) > 0) {
                out.write(buffer, 0, length);
            }
            return out.toString("UTF-8");
        }
    }

    /**
     * Test of listFiles method, of class PathUtil.
     */
    @Test
    public void testListFiles() throws Exception {
        Path archive = root.resolve("test.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive))) {
            ZipEntry entry = new ZipEntry("testfile.xhtml");
            out.putNextEntry(entry);
            try (InputStream in = this.getClass().getClassLoader().getResourceAsStream("testfile.xhtml")) {
                StreamUtils.copyStream(in, out);
            }
            out.closeEntry();
        }
        Files.createDirectory(root.resolve("zip"));
        archive = root.resolve("zip").resolve("test.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive))) {
            ZipEntry entry = new ZipEntry("testfile.xhtml");
            out.putNextEntry(entry);
            try (InputStream in = this.getClass().getClassLoader().getResourceAsStream("testfile.xhtml")) {
                StreamUtils.copyStream(in, out);
            }
            out.closeEntry();
        }
        Files.copy(this.getClass().getClassLoader().getResourceAsStream("testfile.xhtml"), root.resolve("testfile.xhtml"));
        Files.createDirectories(root.resolve("empty").resolve("directory"));
        Files.createDirectories(root.resolve("htdocs").resolve("www"));
        Files.copy(this.getClass().getClassLoader().getResourceAsStream("testfile.xhtml"), root.resolve("htdocs").resolve("www").resolve("testfile.xhtml"));
        List<String> result = new ArrayList<>();
        for (ContentRepositoryElement element1 : PathUtil.listFiles(root, root, ALL)) {
            String repositoryElement2Path = element1.getPath();
            result.add(repositoryElement2Path);
        }
        Assert.assertEquals(9, result.size());
        Assert.assertTrue(result.contains("testfile.xhtml"));
        Assert.assertTrue(result.contains("test.zip"));
        Assert.assertTrue(result.contains("zip/test.zip"));
        Assert.assertTrue(result.contains("zip/"));
        Assert.assertTrue(result.contains("empty/directory/"));
        Assert.assertTrue(result.contains("empty/"));
        Assert.assertTrue(result.contains("htdocs/www/testfile.xhtml"));
        Assert.assertTrue(result.contains("htdocs/www/"));
        Assert.assertTrue(result.contains("htdocs/"));
        List<String> result2 = new ArrayList<>();
        for (ContentRepositoryElement repositoryElement1 : PathUtil.listFiles(root, root, explodableFileFilter(true))) {
            String contentRepositoryElement2Path = repositoryElement1.getPath();
            result2.add(contentRepositoryElement2Path);
        }
        result = result2;
        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.contains("test.zip"));
        Assert.assertTrue(result.contains("zip/test.zip"));
        List<String> list2 = new ArrayList<>();
        for (ContentRepositoryElement contentRepositoryElement1 : PathUtil.listFiles(root, root, directoryListFileFilter())) {
            String element1Path = contentRepositoryElement1.getPath();
            list2.add(element1Path);
        }
        result = list2;
        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.contains("testfile.xhtml"));
        Assert.assertTrue(result.contains("test.zip"));
        List<String> result1 = new ArrayList<>();
        for (ContentRepositoryElement element : PathUtil.listFiles(root, root, ContentFilter.Factory.createContentFilter(2, true))) {
            String repositoryElement1Path = element.getPath();
            result1.add(repositoryElement1Path);
        }
        result = result1;
        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.contains("test.zip"));
        Assert.assertTrue(result.contains("zip/test.zip"));
        List<String> list1 = new ArrayList<>();
        for (ContentRepositoryElement repositoryElement : PathUtil.listFiles(root, root, ContentFilter.Factory.createContentFilter(2, false))) {
            String contentRepositoryElement1Path = repositoryElement.getPath();
            list1.add(contentRepositoryElement1Path);
        }
        result = list1;
        Assert.assertEquals(8, result.size());
        Assert.assertTrue(result.contains("testfile.xhtml"));
        Assert.assertTrue(result.contains("test.zip"));
        Assert.assertTrue(result.contains("zip/"));
        Assert.assertTrue(result.contains("zip/test.zip"));
        Assert.assertTrue(result.contains("empty/"));
        Assert.assertTrue(result.contains("empty/directory/"));
        Assert.assertTrue(result.contains("htdocs/www/"));
        Assert.assertTrue(result.contains("htdocs/"));
        List<String> list = new ArrayList<>();
        for (ContentRepositoryElement contentRepositoryElement : PathUtil.listFiles(root, root, ContentFilter.Factory.createContentFilter(3, false))) {
            String path = contentRepositoryElement.getPath();
            list.add(path);
        }
        result = list;
        Assert.assertEquals(9, result.size());
        Assert.assertTrue(result.contains("testfile.xhtml"));
        Assert.assertTrue(result.contains("test.zip"));
        Assert.assertTrue(result.contains("zip/"));
        Assert.assertTrue(result.contains("zip/test.zip"));
        Assert.assertTrue(result.contains("empty/"));
        Assert.assertTrue(result.contains("empty/directory/"));
        Assert.assertTrue(result.contains("htdocs/"));
        Assert.assertTrue(result.contains("htdocs/www/"));
        Assert.assertTrue(result.contains("htdocs/www/testfile.xhtml"));
    }

    /**
     * Test of listFiles method, of class PathUtil.
     */
    @Test
    public void testListFileInArchives() throws Exception {
        Path archive = root.resolve("test.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive))) {
            ZipEntry entry = new ZipEntry("testfile.xhtml");
            out.putNextEntry(entry);
            try (InputStream in = this.getClass().getClassLoader().getResourceAsStream("testfile.xhtml")) {
                StreamUtils.copyStream(in, out);
            }
            out.closeEntry();
        }
        Path unexploded = Files.createDirectory(root.resolve("unexploded")).resolve("unexploded.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(unexploded))) {
            ZipEntry entry = new ZipEntry("testfile.xhtml");
            out.putNextEntry(entry);
            try (InputStream in = this.getClass().getClassLoader().getResourceAsStream("testfile.xhtml")) {
                StreamUtils.copyStream(in, out);
            }
            out.closeEntry();
            entry = new ZipEntry("test.zip");
            out.putNextEntry(entry);
            try (InputStream in = Files.newInputStream(root.resolve("test.zip"))) {
                StreamUtils.copyStream(in, out);
            }
            out.closeEntry();
            entry = new ZipEntry("zip/test2.zip");
            out.putNextEntry(entry);
            try (InputStream in = Files.newInputStream(root.resolve("test.zip"))) {
                StreamUtils.copyStream(in, out);
            }
            out.closeEntry();
        }
        Files.delete(archive);
        List<String> result = new ArrayList<>();
        for (ContentRepositoryElement repositoryElement1 : PathUtil.listFiles(unexploded, root, ALL)) {
            String contentRepositoryElement2Path = repositoryElement1.getPath();
            result.add(contentRepositoryElement2Path);
        }
        Assert.assertEquals(3, result.size());
        Assert.assertTrue(result.contains("testfile.xhtml"));
        Assert.assertTrue(result.contains("test.zip"));
        Assert.assertTrue(result.contains("zip/test2.zip"));
        List<String> list2 = new ArrayList<>();
        for (ContentRepositoryElement contentRepositoryElement1 : PathUtil.listFiles(unexploded, root, explodableFileFilter(true))) {
            String element1Path = contentRepositoryElement1.getPath();
            list2.add(element1Path);
        }
        result = list2;
        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.contains("test.zip"));
        Assert.assertTrue(result.contains("zip/test2.zip"));
        List<String> result1 = new ArrayList<>();
        for (ContentRepositoryElement element : PathUtil.listFiles(unexploded, root, directoryListFileFilter())) {
            String repositoryElement1Path = element.getPath();
            result1.add(repositoryElement1Path);
        }
        result = result1;
        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.contains("testfile.xhtml"));
        Assert.assertTrue(result.contains("test.zip"));
        List<String> list1 = new ArrayList<>();
        for (ContentRepositoryElement repositoryElement : PathUtil.listFiles(unexploded, root, ContentFilter.Factory.createContentFilter(2, true))) {
            String contentRepositoryElement1Path = repositoryElement.getPath();
            list1.add(contentRepositoryElement1Path);
        }
        result = list1;
        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.contains("test.zip"));
        Assert.assertTrue(result.contains("zip/test2.zip"));
        List<String> list = new ArrayList<>();
        for (ContentRepositoryElement contentRepositoryElement : PathUtil.listFiles(unexploded, root, ContentFilter.Factory.createContentFilter(2, false))) {
            String path = contentRepositoryElement.getPath();
            list.add(path);
        }
        result = list;
        Assert.assertEquals(3, result.size());
        Assert.assertTrue(result.contains("testfile.xhtml"));
        Assert.assertTrue(result.contains("test.zip"));
        Assert.assertTrue(result.contains("zip/test2.zip"));
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
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive))) {
            ZipEntry entry = new ZipEntry("testfile.xhtml");
            out.putNextEntry(entry);
            try (InputStream in = this.getClass().getClassLoader().getResourceAsStream("testfile.xhtml")) {
                StreamUtils.copyStream(in, out);
            }
            out.closeEntry();
        }
        PathUtil.unzip(archive, target);
        Assert.assertTrue(Files.exists(target));
        Path explodedFile = target.resolve("testfile.xhtml");
        Assert.assertTrue(Files.exists(explodedFile));
        Assert.assertTrue(Files.isRegularFile(explodedFile));
    }

    private static ContentFilter explodableFileFilter(boolean archiveOnly) {
        return createFileFilter(-1, archiveOnly);
    }

    private static ContentFilter directoryListFileFilter() {
        return createFileFilter(1, false);
    }
}
