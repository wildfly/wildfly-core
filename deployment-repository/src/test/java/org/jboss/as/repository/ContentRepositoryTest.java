/*
 * Copyright (C) 2014 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.repository;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class ContentRepositoryTest {
    private static final boolean IS_WINDOWS = AccessController.doPrivileged((PrivilegedAction<Boolean>) () ->
            System.getProperty("os.name", null).toLowerCase(Locale.ENGLISH).contains("windows"));

    private ContentRepository repository;
    private final File rootDir = new File("target", "repository");

    public ContentRepositoryTest() {
    }

    @Before
    public void createRepository() {
        if (rootDir.exists()) {
            deleteRecursively(rootDir);
        }
        rootDir.mkdirs();
        repository = ContentRepository.Factory.create(rootDir, 0L);
    }

    @After
    public void destroyRepository() {
        deleteRecursively(rootDir);
        repository = null;
    }

    private void deleteRecursively(File file) {
        if (file.exists()) {
            if (file.isDirectory()) {
                for (File child : file.listFiles()) {
                    deleteRecursively(child);
                }
            }
            file.delete();
        }
    }

    private String readFileContent(File file) throws Exception {
        try (InputStream in = getFileInputStream(file);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8];
            int length = 8;
            while ((length = in.read(buffer, 0, length)) > 0) {
                out.write(buffer, 0, length);
            }
            return out.toString("UTF-8");
        }
    }

    /**
     * Test of addContent method, of class ContentRepository.
     */
    @Test
    public void testAddContent() throws Exception {
        try (InputStream stream = getResourceAsStream("overlay.xhtml")) {
            String expResult = "0c40ffacd15b0f66d5081a93407d3ff5e3c65a71";
            byte[] result = repository.addContent(stream);
            assertThat(result, is(notNullValue()));
            assertThat(HashUtil.bytesToHexString(result), is(expResult));
        }
    }

    /**
     * Test of addContentReference method, of class ContentRepository.
     */
    @Test
    public void testAddContentReference() throws Exception {
        try (InputStream stream = getResourceAsStream("overlay.xhtml")) {
            String expResult = "0c40ffacd15b0f66d5081a93407d3ff5e3c65a71";
            byte[] result = repository.addContent(stream);
            assertThat(result, is(notNullValue()));
            assertThat(HashUtil.bytesToHexString(result), is(expResult));
            ContentReference reference = new ContentReference("contentReferenceIdentifier", result);
            repository.addContentReference(reference);
        }
    }

    /**
     * Test of getContent method, of class ContentRepository.
     */
    @Test
    public void testGetContent() throws Exception {
        try (InputStream stream = getResourceAsStream("overlay.xhtml")) {
            String expResult = "0c40ffacd15b0f66d5081a93407d3ff5e3c65a71";
            byte[] result = repository.addContent(stream);
            assertThat(result, is(notNullValue()));
            assertThat(HashUtil.bytesToHexString(result), is(expResult));
            File content = repository.getContent(result);
            String contentHtml = readFileContent(content);
            String expectedContentHtml = readFileContent(new File(this.getClass().getClassLoader().getResource("overlay.xhtml").toURI()));
            assertThat(contentHtml, is(expectedContentHtml));
        }
    }

    /**
     * Test of hasContent method, of class ContentRepository.
     */
    @Test
    public void testHasContent() throws Exception {
        String expResult = "0c40ffacd15b0f66d5081a93407d3ff5e3c65a71";
        try (InputStream stream = getResourceAsStream("overlay.xhtml")) {
            assertThat(repository.hasContent(HashUtil.hexStringToByteArray(expResult)), is(false));
            byte[] result = repository.addContent(stream);
            assertThat(result, is(notNullValue()));
            assertThat(HashUtil.bytesToHexString(result), is(expResult));
            assertThat(repository.hasContent(HashUtil.hexStringToByteArray(expResult)), is(true));
        }
    }

    /**
     * Test of removeContent method, of class ContentRepository.
     */
    @Test
    public void testRemoveContent() throws Exception {
        String expResult = "0c40ffacd15b0f66d5081a93407d3ff5e3c65a71";
        Path grandparent = rootDir.toPath().resolve("0c");
        Path parent = grandparent.resolve("40ffacd15b0f66d5081a93407d3ff5e3c65a71");
        Path expectedContent = parent.resolve("content");
        assertFalse(expectedContent + " shouldn't exist", Files.exists(expectedContent));
        assertFalse(expectedContent.getParent() + " shouldn't exist", Files.exists(parent));
        assertFalse(expectedContent.getParent().getParent() + " shouldn't exist", Files.exists(grandparent));
        byte[] result;
        try (InputStream stream = getResourceAsStream("overlay.xhtml")) {
            assertThat(repository.hasContent(HashUtil.hexStringToByteArray(expResult)), is(false));
            result = repository.addContent(stream);
        }
        assertThat(result, is(notNullValue()));
        assertThat(HashUtil.bytesToHexString(result), is(expResult));
        assertThat(repository.hasContent(HashUtil.hexStringToByteArray(expResult)), is(true));
        assertTrue(expectedContent + " should have been created", Files.exists(expectedContent));
        assertTrue(parent + " should have been created", Files.exists(parent));
        assertTrue(grandparent + " should have been created", Files.exists(grandparent));
        repository.removeContent(new ContentReference("overlay.xhtml", expResult));
        assertThat(repository.hasContent(HashUtil.hexStringToByteArray(expResult)), is(false));
        assertFalse(expectedContent + " should have been deleted", Files.exists(expectedContent));
        assertFalse(parent.toAbsolutePath() + " should have been deleted", Files.exists(parent));
        assertFalse(grandparent + " should have been deleted", Files.exists(grandparent));
        File content = repository.getContent(result);
        assertFalse(content.exists());
    }

    /**
     * Test that an empty dir will be removed during cleaning.
     */
    @Test
    public void testCleanEmptyParentDir() throws Exception {
        File emptyGrandParent = new File(rootDir, "ae");
        emptyGrandParent.mkdir();
        File emptyParent = new File(emptyGrandParent, "ffacd15b0f66d5081a93407d3ff5e3c65a71");
        emptyParent.mkdir();
        assertThat(emptyGrandParent.exists(), is(true));
        assertThat(emptyParent.exists(), is(true));
        Map<String, Set<String>> result = repository.cleanObsoleteContent(); //To mark content for deletion
        assertThat(result.get(ContentRepository.MARKED_CONTENT).size(), is(1));
        assertThat(result.get(ContentRepository.DELETED_CONTENT).size(), is(0));
        assertThat(result.get(ContentRepository.MARKED_CONTENT).contains(emptyParent.getAbsolutePath()), is(true));
        Thread.sleep(10);
        result = repository.cleanObsoleteContent();
        assertThat(emptyGrandParent.exists(), is(false));
        assertThat(emptyParent.exists(), is(false));
        assertThat(result.get(ContentRepository.MARKED_CONTENT).size(), is(0));
        assertThat(result.get(ContentRepository.DELETED_CONTENT).size(), is(1));
        assertThat(result.get(ContentRepository.DELETED_CONTENT).contains(emptyParent.getAbsolutePath()), is(true));
    }

    /**
     * Test that an empty dir will be removed during cleaning.
     */
    @Test
    public void testCleanEmptyGrandParentDir() throws Exception {
        File emptyGrandParent = new File(rootDir, "ae");
        emptyGrandParent.mkdir();
        assertThat(emptyGrandParent.exists(), is(true));
        Map<String, Set<String>> result = repository.cleanObsoleteContent(); //Mark content for deletion
        assertThat(result.get(ContentRepository.MARKED_CONTENT).size(), is(1));
        assertThat(result.get(ContentRepository.DELETED_CONTENT).size(), is(0));
        assertThat(result.get(ContentRepository.MARKED_CONTENT).contains(emptyGrandParent.getAbsolutePath()), is(true));
        Thread.sleep(10);
        result = repository.cleanObsoleteContent();
        assertThat(emptyGrandParent.exists(), is(false));
        assertThat(result.get(ContentRepository.DELETED_CONTENT).size(), is(1));
        assertThat(result.get(ContentRepository.MARKED_CONTENT).size(), is(0));
        assertThat(result.get(ContentRepository.DELETED_CONTENT).contains(emptyGrandParent.getAbsolutePath()), is(true));
    }

    /**
     * Test that an empty dir with a system metadata file .DS_Store will be removed during cleaning.
     */
    @Test
    public void testCleanEmptyParentDirWithSystemMetaDataFile() throws Exception {
        File emptyGrandParent = new File(rootDir, "ae");
        emptyGrandParent.mkdir();
        File metaDataFile = new File(emptyGrandParent, ".DS_Store");
        metaDataFile.createNewFile();
        assertThat(emptyGrandParent.exists(), is(true));
        assertThat(metaDataFile.exists(), is(true));
        Map<String, Set<String>> result = repository.cleanObsoleteContent(); // To mark content for deletion
        assertThat(result.get(ContentRepository.MARKED_CONTENT).size(), is(1));
        assertThat(result.get(ContentRepository.DELETED_CONTENT).size(), is(0));
        assertThat(result.get(ContentRepository.MARKED_CONTENT).contains(metaDataFile.getAbsolutePath()), is(true));
        Thread.sleep(10);
        result = repository.cleanObsoleteContent();
        assertThat(emptyGrandParent.exists(), is(false));
        assertThat(metaDataFile.exists(), is(false));
        assertThat(result.get(ContentRepository.MARKED_CONTENT).size(), is(0));
        assertThat(result.get(ContentRepository.DELETED_CONTENT).size(), is(1));
        assertThat(result.get(ContentRepository.DELETED_CONTENT).contains(metaDataFile.getAbsolutePath()), is(true));
    }

    /**
     * Test that an empty dir will be removed during cleaning.
     */
    @Test
    public void testCleanNotEmptyGrandParentDir() throws Exception {
        String expResult = "0c40ffacd15b0f66d5081a93407d3ff5e3c65a71";
        Path grandparent = rootDir.toPath().resolve("0c");
        Path parent = grandparent.resolve("40ffacd15b0f66d5081a93407d3ff5e3c65a71");
        Path other = grandparent.resolve("40ffacd15b0f66d5081a93407d3ff5e3c65a81");
        Files.createDirectories(other);
        Path expectedContent = parent.resolve("content");
        assertFalse(expectedContent + " shouldn't exist", Files.exists(expectedContent));
        assertFalse(parent + " shouldn't exist", Files.exists(parent));
        byte[] result;
        try (InputStream stream = getResourceAsStream("overlay.xhtml")) {
            assertThat(repository.hasContent(HashUtil.hexStringToByteArray(expResult)), is(false));
            result = repository.addContent(stream);
        }
        assertThat(result, is(notNullValue()));
        assertThat(HashUtil.bytesToHexString(result), is(expResult));
        assertThat(repository.hasContent(HashUtil.hexStringToByteArray(expResult)), is(true));
        assertTrue(expectedContent + " should have been created", Files.exists(expectedContent));
        assertTrue(parent + " should have been created", Files.exists(parent));
        repository.removeContent(new ContentReference("overlay.xhtml", expResult));
        assertFalse(repository.hasContent(HashUtil.hexStringToByteArray(expResult)));
        assertFalse(expectedContent + " should have been deleted", Files.exists(expectedContent));
        assertFalse(parent.toAbsolutePath() + " should have been deleted", Files.exists(parent));
        assertTrue(other + " should not have been deleted", Files.exists(other));
        assertTrue(grandparent + " should not have been deleted", Files.exists(grandparent));
        File content = repository.getContent(result);
        assertFalse(content.exists());
    }

    /**
     * Test that an dir not empty with no content will not be removed during cleaning.
     */
    @Test
    public void testNotEmptyDir() throws Exception {
        Path parentDir = rootDir.toPath().resolve("ae").resolve("ffacd15b0f66d5081a93407d3ff5e3c65a71");
        Path overlay = parentDir.resolve("overlay.xhtml");
        Path content = parentDir.resolve("content");
        Files.createDirectories(overlay.getParent());
        try (InputStream stream = getResourceAsStream("overlay.xhtml")) {
            Files.copy(stream, overlay);
            Files.copy(overlay, content);
            assertThat(Files.exists(content), is(true));
            assertThat(Files.exists(overlay), is(true));
            Map<String, Set<String>> result = repository.cleanObsoleteContent(); //Mark content for deletion
            assertThat(result.get(ContentRepository.MARKED_CONTENT).size(), is(1));
            assertThat(result.get(ContentRepository.DELETED_CONTENT).size(), is(0));
            assertThat(result.get(ContentRepository.MARKED_CONTENT).contains(parentDir.toFile().getAbsolutePath()), is(true));
            Thread.sleep(10);
            result = repository.cleanObsoleteContent();
            assertThat(Files.exists(content), is(false));
            assertThat(Files.exists(overlay), is(true));
            assertThat(result.get(ContentRepository.MARKED_CONTENT).size(), is(0));
            assertThat(result.get(ContentRepository.DELETED_CONTENT).size(), is(1));
            assertThat(result.get(ContentRepository.DELETED_CONTENT).contains(parentDir.toFile().getAbsolutePath()), is(true));
        } finally {
            Files.deleteIfExists(overlay);
            Files.deleteIfExists(overlay.getParent());
            Files.deleteIfExists(overlay.getParent().getParent());
        }

    }

    private InputStream getResourceAsStream(final String name) throws IOException {
        final InputStream result = getClass().getClassLoader().getResourceAsStream(name);
        // If we're on Windows we want to replace the stream with one that ignores \r
        if (result != null && IS_WINDOWS) {
            return new CarriageReturnRemovalInputStream(result);
        }
        return result;
    }

    private InputStream getFileInputStream(final File file) throws IOException {
        if (IS_WINDOWS) {
            return new CarriageReturnRemovalInputStream(new FileInputStream(file));
        }
        return new FileInputStream(file);
    }

    private static class CarriageReturnRemovalInputStream extends InputStream {
        private final InputStream delegate;

        private CarriageReturnRemovalInputStream(final InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int result = delegate.read();
            if (result == '\r') {
                result = delegate.read();
            }
            return result;
        }

        @Override
        public int read(final byte[] b) throws IOException {
            Objects.nonNull(b);
            int result = 0;
            while (result > -1 && result < b.length) {
                int c = read();
                if (c == -1) {
                    return result == 0 ? -1 : result;
                }
                b[result++] = (byte) c;
            }
            return result;
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            Objects.nonNull(b);
            if (off < 0 || len < 0 || len > b.length - off) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return 0;
            }
            int result = 0;
            while (result > -1 && result < len) {
                int c = read();
                if (c == -1) {
                    return result == 0 ? -1 : result;
                }
                b[off + result++] = (byte) c;
            }
            return result;
        }

        @Override
        public long skip(final long n) throws IOException {
            return delegate.skip(n);
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public void mark(final int readlimit) {
            delegate.mark(readlimit);
        }

        @Override
        public void reset() throws IOException {
            delegate.reset();
        }

        @Override
        public boolean markSupported() {
            return delegate.markSupported();
        }
    }
}
