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

package org.jboss.as.server.deployment.module;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jboss.modules.ClassSpec;
import org.jboss.modules.PackageSpec;
import org.jboss.modules.Resource;
import org.junit.Test;
import org.wildfly.loaders.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ClassPathUtilsTestCase {

    private static final String[] windowsPaths = new String[] {
            ".\\\\\\\\",
            "..\\\\\\..\\\\..\\",
            "..\\\\\\..\\\\.\\.\\..\\\\",
            ".\\.\\..\\\\\\..\\\\.\\.\\..\\\\",
            ".\\.\\..\\\\\\..\\\\.\\.\\..\\\\cp.jar",
            "..\\..\\..\\cp.jar",
            ".\\a\\b\\c\\..\\..\\d",
            ".\\a\\..\\c\\..\\..\\d",
            ".\\a\\b\\c\\..\\..\\d\\..\\e\\f",
            ".\\a\\b\\c\\..\\..\\..\\..\\d\\..\\e\\f",
    };
    private static final String[] linuxPaths = new String[] {
            ".////",
            "..///..//../",
            "..///..//././..//",
            "././..///..//././..///",
            "././..///..//././..///cp.jar",
            "../../../cp.jar",
            "./a/b/c/../../d",
            "./a/../c/../../d",
            "./a/b/c/../../d/../e/f",
            "./a/b/c/../../../../d/../e/f",
    };
    private static final String[] mixedPaths = new String[] {
            "./\\/\\",
            "../\\/../\\../",
            "../\\/..//.\\.\\../\\",
            "./.\\..\\//..//.\\./..\\\\/",
            "./.\\..\\//..//.\\./..\\\\/cp.jar",
            "..\\../..\\cp.jar",
            "./a\\b/c\\../..\\d",
            "./a\\../c\\../..\\d",
            "./a\\b/c\\..\\\\..\\\\d/..\\e/f",
            "./a\\b/c\\..\\..\\..\\\\..\\\\d/..\\e/f",
    };
    private static final String[] expectedResults = new String[]{
            "",
            "../../../",
            "../../../",
            "../../../",
            "../../../cp.jar",
            "../../../cp.jar",
            "a/d",
            "../d",
            "a/e/f",
            "../e/f",
    };

    @Test
    public void testWindows() {
        synchronized (System.out) {
            for (int i = 0; i < windowsPaths.length; i++) {
                String windowsPath = windowsPaths[i];
                String targetPath = Utils.canonicalizeClassPathEntry(windowsPath);
                System.out.println("Windows: " + targetPath);
                assertEquals(expectedResults[i], targetPath);
                if (i < 4) {
                    assertFalse(Utils.isValidClassPathEntry(targetPath));
                } else {
                    assertTrue(Utils.isValidClassPathEntry(targetPath));
                }
            }
        }
    }

    @Test
    public void testLinux() {
        synchronized (System.out) {
            for (int i = 0; i < linuxPaths.length; i++) {
                String windowsPath = linuxPaths[i];
                String targetPath = Utils.canonicalizeClassPathEntry(windowsPath);
                System.out.println("Linux:   " + targetPath);
                assertEquals(expectedResults[i], targetPath);
                if (i < 4) {
                    assertFalse(Utils.isValidClassPathEntry(targetPath));
                } else {
                    assertTrue(Utils.isValidClassPathEntry(targetPath));
                }
            }
        }
    }

    @Test
    public void testMixed() {
        synchronized (System.out) {
            for (int i = 0; i < mixedPaths.length; i++) {
                String windowsPath = mixedPaths[i];
                String targetPath = Utils.canonicalizeClassPathEntry(windowsPath);
                System.out.println("Mixed:   " + targetPath);
                assertEquals(expectedResults[i], targetPath);
                if (i < 4) {
                    assertFalse(Utils.isValidClassPathEntry(targetPath));
                } else {
                    assertTrue(Utils.isValidClassPathEntry(targetPath));
                }
            }
        }
    }

    @Test
    public void testGetLoaderAndPathForClassPathEntry() {
        final ResourceLoader earLoader = new TestResourceLoader(null, "foo.ear");
        final ResourceLoader webLoader = new TestResourceLoader(earLoader, "bar.war");
        final ResourceLoader libLoader = new TestResourceLoader(webLoader, "WEB-INF/lib/lib.jar");
        System.out.println("EAR loader full path: " + Utils.getLoaderPath(earLoader));
        System.out.println("WEB loader full path: " + Utils.getLoaderPath(webLoader));
        System.out.println("LIB loader full path: " + Utils.getLoaderPath(libLoader));

        final ResourceLoader loaderResult1 = Utils.getLoaderForClassPathEntry("cp.jar", libLoader);
        final String pathResult1 = Utils.getPathForClassPathEntry("cp.jar", libLoader);
        System.out.println(loaderResult1 + " lookups " + pathResult1);
        assertEquals(webLoader, loaderResult1);
        assertEquals("cp.jar", pathResult1);

        final ResourceLoader loaderResult2 = Utils.getLoaderForClassPathEntry("../cp.jar", libLoader);
        final String pathResult2 = Utils.getPathForClassPathEntry("../cp.jar", libLoader);
        System.out.println(loaderResult2 + " lookups " + pathResult2);
        assertEquals(webLoader, loaderResult2);
        assertEquals("WEB-INF/lib/cp.jar", pathResult2);

        final ResourceLoader loaderResult3 = Utils.getLoaderForClassPathEntry("../../cp.jar", libLoader);
        final String pathResult3 = Utils.getPathForClassPathEntry("../../cp.jar", libLoader);
        System.out.println(loaderResult3 + " lookups " + pathResult3);
        assertEquals(webLoader, loaderResult3);
        assertEquals("cp.jar", pathResult3);

        final ResourceLoader loaderResult4 = Utils.getLoaderForClassPathEntry("../../../cp.jar", libLoader);
        final String pathResult4 = Utils.getPathForClassPathEntry("../../../cp.jar", libLoader);
        System.out.println(loaderResult4 + " lookups " + pathResult4);
        assertEquals(earLoader, loaderResult4);
        assertEquals("cp.jar", pathResult4);

        final ResourceLoader loaderResult5 = Utils.getLoaderForClassPathEntry("../../../lib/cp.jar", libLoader);
        final String pathResult5 = Utils.getPathForClassPathEntry("../../../lib/cp.jar", libLoader);
        System.out.println(loaderResult5 + " lookups " + pathResult5);
        assertEquals(earLoader, loaderResult5);
        assertEquals("lib/cp.jar", pathResult5);
    }

    private static final class TestResourceLoader implements ResourceLoader {

        private final ResourceLoader parent;
        private final String path;

        private TestResourceLoader(final ResourceLoader parent, final String path) {
            this.parent = parent;
            this.path = path;
        }

        @Override
        public String getPath() {
            return parent != null ? path : null;
        }

        @Override
        public String getFullPath() {
            return parent != null ? parent.getFullPath() + "/" + path : path;
        }

        @Override
        public ResourceLoader getParent() {
            return parent;
        }

        @Override
        public ResourceLoader getChild(final String path) {
            return null;
        }

        @Override
        public String getRootName() {
            return path;
        }

        @Override
        public String toString() {
            return path;
        }

        // the following methods are not implemented

        @Override
        public void setUsePhysicalCodeSource(final boolean usePhysicalCodeSource) {
        }

        @Override
        public File getRoot() {
            return null;
        }

        @Override
        public URL getRootURL() {
            return null;
        }

        @Override
        public Iterator<String> iteratePaths(String startPath, boolean recursive) {
            return null;
        }

        @Override
        public void addOverlay(String path, File content) {

        }

        @Override
        public Iterator<Resource> iterateResources(String startPath, boolean recursive) {
            return null;
        }

        @Override
        public ClassSpec getClassSpec(String fileName) throws IOException {
            return null;
        }

        @Override
        public PackageSpec getPackageSpec(String name) throws IOException {
            return null;
        }

        @Override
        public Resource getResource(String name) {
            return null;
        }

        @Override
        public String getLibrary(String name) {
            return null;
        }

        @Override
        public Collection<String> getPaths() {
            return null;
        }

        @Override
        public void close() {

        }

    }

}
