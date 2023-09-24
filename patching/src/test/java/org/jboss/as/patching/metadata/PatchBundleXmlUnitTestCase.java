/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.util.Scanner;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Emanuel Muckenhuber
 */
public class PatchBundleXmlUnitTestCase {

    @Test
    public void testBasic() throws Exception {

        try (final InputStream is = getResource("multi-patch-01.xml")) {
            final BundledPatch bundledPatch = PatchBundleXml.parse(is);
            Assert.assertNotNull(bundledPatch);
            Assert.assertFalse(bundledPatch.getPatches().isEmpty());
        }

    }

    @Test
    public void testMarshal() throws Exception {
        doMarshall("multi-patch-01.xml");
    }

    static void doMarshall(final String fileName) throws Exception {
        final String original = toString(fileName);

        try (final InputStream is = getResource(fileName)) {
            final BundledPatch patch = PatchBundleXml.parse(is);

            final StringWriter writer = new StringWriter();
            PatchBundleXml.marshal(writer, patch);
            final String marshalled = writer.toString();

            XMLUtils.compareXml(original, marshalled, false);
        }
    }

    static InputStream getResource(final String name) throws IOException {
        final URL resource = PatchBundleXmlUnitTestCase.class.getClassLoader().getResource(name);
        assertNotNull(name, resource);
        return resource.openStream();
    }

    static String toString(final String fileName) throws Exception {
        try (final InputStream is = getResource(fileName)){
            assertNotNull(is);
            is.mark(0);
            String out = new Scanner(is).useDelimiter("\\A").next();
            is.reset();
            return out;
        } catch (Exception e) {
            return "";
        }
    }

}
