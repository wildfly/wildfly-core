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
import static org.junit.Assert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import org.jboss.vfs.VirtualFile;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class ContentRepositoryTest {

    private ContentRepository repository;
    private File rootDir = new File("target", "repository");

    public ContentRepositoryTest() {
    }

    @Before
    public void createRepository() {
        rootDir.mkdirs();
        repository = ContentRepository.Factory.create(rootDir);
    }

    @After
    public void destroyRepository() {
        deleteRecursively(rootDir);
        repository = null;
    }

    private void deleteRecursively(File file) {
        if (file.exists()) {
            if (file.isDirectory()) {
                for (String name : file.list()) {
                    deleteRecursively(new File(file, name));
                }
            }
            file.delete();
        }
    }

    private String readFileContent(File file) throws Exception {
        try (InputStream in = new FileInputStream(file);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8];
            int length = 8;
            while ((length = in.read(buffer, 0, length)) > 0) {
                out.write(buffer, 0, length);
            }
            return out.toString("UTF-8");
        }
    }
    
    private void copyFileContent(File src, File target) throws Exception {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8];
            int length = 8;
            while ((length = in.read(buffer, 0, length)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }

    /**
     * Test of addContent method, of class ContentRepository.
     */
    @Test
    public void testAddContent() throws Exception {
        try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream("overlay.xhtml")) {
            String expResult = "8a3b4c2eb328b955a8fb4f9229a6a1037ff0c1e1";
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
        try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream("overlay.xhtml")) {
            String expResult = "8a3b4c2eb328b955a8fb4f9229a6a1037ff0c1e1";
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
        try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream("overlay.xhtml")) {
            String expResult = "8a3b4c2eb328b955a8fb4f9229a6a1037ff0c1e1";
            byte[] result = repository.addContent(stream);
            assertThat(result, is(notNullValue()));
            assertThat(HashUtil.bytesToHexString(result), is(expResult));
            VirtualFile content = repository.getContent(result);
            String contentHtml = readFileContent(content.getPhysicalFile());
            String expectedContentHtml = readFileContent(new File(this.getClass().getClassLoader().getResource("overlay.xhtml").toURI()));
            assertThat(contentHtml, is(expectedContentHtml));
        }
    }

    /**
     * Test of hasContent method, of class ContentRepository.
     */
    @Test
    public void testHasContent() throws Exception {
        String expResult = "8a3b4c2eb328b955a8fb4f9229a6a1037ff0c1e1";
        try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream("overlay.xhtml")) {
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
        String expResult = "8a3b4c2eb328b955a8fb4f9229a6a1037ff0c1e1";
        try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream("overlay.xhtml")) {
            assertThat(repository.hasContent(HashUtil.hexStringToByteArray(expResult)), is(false));
            byte[] result = repository.addContent(stream);
            assertThat(result, is(notNullValue()));
            assertThat(HashUtil.bytesToHexString(result), is(expResult));
            assertThat(repository.hasContent(HashUtil.hexStringToByteArray(expResult)), is(true));
            repository.removeContent(new ContentReference("overlay.xhtml", expResult));
            assertThat(repository.hasContent(HashUtil.hexStringToByteArray(expResult)), is(false));
            VirtualFile content = repository.getContent(result);
            assertThat(content.exists(), is(false));
        }
    }

    /**
     * Test of syncReferences method, of class ContentRepository.
     */
    @Test
    public void testSyncReferences() throws Exception {
        //Create obsolete content
        File obsoleteContent = new File(rootDir, "32" + File.separatorChar + "3b4c2eb328b955a8fb4f9229a6a1037ff0c1e1" +  File.separatorChar + "content");
        obsoleteContent.getParentFile().mkdirs();
        File exisitingContent = new File(rootDir, "8a" + File.separatorChar + "3b4c2eb328b955a8fb4f9229a6a1037ff0c1e1" +  File.separatorChar + "content");
        exisitingContent.getParentFile().mkdirs();
        File source = new File(this.getClass().getClassLoader().getResource("overlay.xhtml").toURI());
        copyFileContent(source, obsoleteContent);
        copyFileContent(source, exisitingContent);
        assertThat(exisitingContent.exists(), is(true));
        assertThat(obsoleteContent.exists(), is(true));
        Set<ContentReference> references = new HashSet<>();
        references.add(new ContentReference("existing", "8a3b4c2eb328b955a8fb4f9229a6a1037ff0c1e1"));
        references.add(new ContentReference("not-used", "643b4c2eb328b955a8fb4f9229a6a1037ff0c1e1"));
        repository.syncReferences(references);
        assertThat(exisitingContent.exists(), is(true));
        assertThat(obsoleteContent.exists(), is(true));        
        obsoleteContent.setLastModified(System.currentTimeMillis() - (600000L));
        obsoleteContent.getParentFile().setLastModified(System.currentTimeMillis() - (600000L));
        repository.syncReferences(references);
        assertThat(exisitingContent.exists(), is(true));
        assertThat(obsoleteContent.exists(), is(false));        
    }
}
