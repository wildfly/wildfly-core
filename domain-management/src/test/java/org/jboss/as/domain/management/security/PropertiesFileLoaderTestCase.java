/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.domain.management.security;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

/**
 * A test case to for {@link PropertiesFileLoader}.
 *
 * @author <a href="mailto:kwills@redhat.com">Ken Wills</a>
 */
public class PropertiesFileLoaderTestCase {

    static final String[] properties = {
        "ABC=123",
        "DEF=456",
        "#this is a comment, not a property",
        "GHI=789"
    };

    private File createTempFile() throws Exception {
        return File.createTempFile("PropertiesFileLoaderTestCase", ".properties");
    }

    private void cleanupTempFile(final File tmpFile) {
        if (tmpFile != null)
            tmpFile.delete();
    }

    private void writeTestDataToFile(final File tmpFile) throws Exception {
        FileOutputStream fos = new FileOutputStream(tmpFile);
        for (String s : properties) {
            fos.write(s.getBytes());
            fos.write('\n');
        }
        fos.flush();
        fos.close();
    }

    private void verifyProperties(final Properties props, final int expectedSize) {
        verifyProperties(props, expectedSize, null, null);
    }

    private void verifyProperties(final Properties props, final int expectedSize, final String changedKey, final String expectedValue) {
        for (String s : properties) {
            if (s.startsWith("#")) // skip comments, the persist methods on PropertiesFileLoader will re-read those and write them back out
                continue;
            String[] parts = s.split("=");
            String user = parts[0];
            String password = parts[1];
            if (changedKey != null && changedKey.equals(user)) {
                if (expectedValue == null) {
                    Assert.assertNull(props.get(changedKey));
                } else {
                    Assert.assertEquals(expectedValue, props.get(changedKey));
                }
            } else {
                Assert.assertNotNull(props.getProperty(user));
                Assert.assertEquals(password, props.getProperty(user));
            }
        }
        // if we added a value, verify it here, this uses a pretty naive method, but good enough for this test
        if (props.size() > properties.length - 1) {
            Assert.assertEquals(props.get(changedKey), expectedValue);
        }
        // -1 because we don't count the comment
        Assert.assertEquals(properties.length - 1, expectedSize);
    }

    @Test
    public void testLoad() throws Exception {
        File tmpFile = null;
        try {
            tmpFile = createTempFile();
            writeTestDataToFile(tmpFile);

            PropertiesFileLoader loader = new PropertiesFileLoader(tmpFile.getAbsolutePath());
            loader.start(null);
            Properties props = loader.getProperties();
            verifyProperties(props, props.size());
            loader.stop(null);
        } finally {
            cleanupTempFile(tmpFile);
        }
    }

    @Test
    public void testAdd() throws Exception {
        File tmpFile = null;
        try {
            tmpFile = createTempFile();
            writeTestDataToFile(tmpFile);

            PropertiesFileLoader loader = new PropertiesFileLoader(tmpFile.getAbsolutePath());
            loader.start(null);
            Properties props = loader.getProperties();
            props.put("NEW", "VALUE");
            loader.persistProperties();
            loader.stop(null);

            // reload the file and make sure everything is there
            PropertiesFileLoader loader2 = new PropertiesFileLoader(tmpFile.getAbsolutePath());
            loader2.start(null);
            Properties props2 = loader2.getProperties();
            verifyProperties(props2, props2.size()-1, "NEW", "VALUE");
            loader2.stop(null);
        } finally {
            cleanupTempFile(tmpFile);
        }
    }

    @Test
    public void testDoublePersistWithEmptyValue() throws Exception {
        File tmpFile = null;
        try {
            tmpFile = createTempFile();
            writeTestDataToFile(tmpFile);

            PropertiesFileLoader loader = new PropertiesFileLoader(tmpFile.getAbsolutePath());
            loader.start(null);
            Properties props = loader.getProperties();
            props.put("EMPTY", "");
            loader.persistProperties();

            props = loader.getProperties();
            props.put("NEW", "VALUE");
            loader.persistProperties();
            loader.stop(null);

            //verify all properties are present
            PropertiesFileLoader loader2 = new PropertiesFileLoader(tmpFile.getAbsolutePath());
            loader2.start(null);
            Properties props2 = loader2.getProperties();
            verifyProperties(props2, props2.size()-2, "NEW", "VALUE");
            loader2.stop(null);

            //verify that key is present only once
            Assert.assertEquals(properties.length + 2,
                    Files.lines(Paths.get(tmpFile.getAbsolutePath()), Charset.defaultCharset()).count());

        } finally {
            cleanupTempFile(tmpFile);
        }
    }

    @Test
    public void testRemove() throws Exception {
        File tmpFile = null;
        try {
            tmpFile = createTempFile();
            writeTestDataToFile(tmpFile);

            PropertiesFileLoader loader = new PropertiesFileLoader(tmpFile.getAbsolutePath());
            loader.start(null);
            Properties props = loader.getProperties();
            props.remove("ABC");
            loader.persistProperties();
            loader.stop(null);

            // reload the file and make sure the removed item has been removed
            PropertiesFileLoader loader2 = new PropertiesFileLoader(tmpFile.getAbsolutePath());
            loader2.start(null);
            Properties props2 = loader2.getProperties();
            verifyProperties(props2, props2.size()+1, "ABC", null);
            loader2.stop(null);
        } finally {
            cleanupTempFile(tmpFile);
        }

    }

    @Test
    public void testChangeValue() throws Exception {
        File tmpFile = null;
        try {
            tmpFile = createTempFile();
            writeTestDataToFile(tmpFile);

            PropertiesFileLoader loader = new PropertiesFileLoader(tmpFile.getAbsolutePath());
            loader.start(null);
            Properties props = loader.getProperties();
            props.put("ABC", "321");
            loader.persistProperties();
            loader.stop(null);

            // reload the file and make sure the removed item has been removed
            PropertiesFileLoader loader2 = new PropertiesFileLoader(tmpFile.getAbsolutePath());
            loader2.start(null);
            Properties props2 = loader2.getProperties();
            verifyProperties(props2, props2.size(), "ABC", "321");
            loader2.stop(null);
        } finally {
            cleanupTempFile(tmpFile);
        }

    }

}
