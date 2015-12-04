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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.jboss.modules.Resource;
import org.jboss.modules.PathUtils;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.exporter.ExplodedExporter;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ResourceLoadersTest {

    private static final String JAR_RESOURCE_NAME = "WEB-INF/lib/lib.jar";

    private static final String WAR_RESOURCE_NAME = "app.war";

    private static final String EAR_RESOURCE_NAME = "foo.ear";

    private static final String[] JAR_PATHS = {
            "",
            "org",
            "org/wildfly",
            "org/wildfly/loaders",
    };

    private static final String[] WAR_PATHS = {
            "",
            "WEB-INF",
            "WEB-INF/classes",
            "WEB-INF/classes/org",
            "WEB-INF/classes/org/wildfly",
            "WEB-INF/classes/org/wildfly/loaders",
            "WEB-INF/lib"
    };

    private static final String[] EAR_PATHS = {
            "",
            "META-INF",
    };

    private static final String[] JAR_RESOURCE_NAMES = {
            "org/wildfly/loaders/Messages.class",
            "org/wildfly/loaders/Messages.java",
    };
    private static final String[] WAR_RESOURCE_NAMES = {
            "WEB-INF/web.xml",
            "WEB-INF/classes/org/wildfly/loaders/GreetingServlet.class",
            "WEB-INF/classes/org/wildfly/loaders/GreetingServlet.java",
    };
    private static final String[] EAR_RESOURCE_NAMES = {
            "META-INF/application.xml",
    };

    static {
        // initialize
        final String loadersModuleDir = System.getProperty("user.dir");
        final File sourcesDir = new File(loadersModuleDir, "src/test/java");
        final File classesDir = new File(loadersModuleDir, "target/test-classes");
        // define lib.jar
        final JavaArchive libJar = ShrinkWrap.create(JavaArchive.class, "lib.jar");
        libJar.addClass(Messages.class);
        libJar.add(new FileAsset(new File(sourcesDir, "org/wildfly/loaders/Messages.java")), "org/wildfly/loaders/Messages.java");
        // define app.war
        final WebArchive appWar = ShrinkWrap.create(WebArchive.class, "app.war");
        appWar.setWebXML(new File(sourcesDir, "org/wildfly/loaders/web.xml"));
        appWar.addAsLibraries(libJar);
        appWar.addClass(GreetingServlet.class);
        appWar.add(new FileAsset(new File(sourcesDir, "org/wildfly/loaders/GreetingServlet.java")), "WEB-INF/classes/org/wildfly/loaders/GreetingServlet.java");
        // define foo.ear
        final EnterpriseArchive fooEar = ShrinkWrap.create(EnterpriseArchive.class, "foo.ear");
        fooEar.addAsManifestResource(new FileAsset(new File(sourcesDir, "org/wildfly/loaders/application.xml")), "application.xml");
        fooEar.addAsModule(appWar);
        // scenario 1 - everything exploded
        File targetDir = new File(classesDir, "loader-test-exploded");
        targetDir.mkdirs();
        libJar.as(ExplodedExporter.class);
        appWar.as(ExplodedExporter.class);
        fooEar.as(ExplodedExporter.class).exportExploded(targetDir);
        // scenario 2 - foo.ear and lib.jar packaged, app.war exploded
        targetDir = new File(classesDir, "loader-test-mixed1");
        targetDir.mkdirs();
        libJar.as(ZipExporter.class);
        appWar.as(ExplodedExporter.class);
        fooEar.as(ZipExporter.class).exportTo(new File(targetDir, "foo.ear"));
        // scenario 3 - foo.ear and lib.jar exploded, app.war packaged
        targetDir = new File(classesDir, "loader-test-mixed2");
        targetDir.mkdirs();
        libJar.as(ExplodedExporter.class);
        appWar.as(ZipExporter.class);
        fooEar.as(ExplodedExporter.class).exportExploded(targetDir);
        // scenario 4 - everything packaged
        targetDir = new File(classesDir, "loader-test-packaged");
        targetDir.mkdirs();
        libJar.as(ZipExporter.class);
        appWar.as(ZipExporter.class);
        fooEar.as(ZipExporter.class).exportTo(new File(targetDir, "foo.ear"));
    }

    @Test
    public void testExplodedEar() throws Exception {
        test("/loader-test-exploded/" + EAR_RESOURCE_NAME);
    }

    @Test
    public void testPackagedEar() throws Exception {
        test("/loader-test-packaged/" + EAR_RESOURCE_NAME);
    }

    @Test
    public void testMixed1Ear() throws Exception {
        test("/loader-test-mixed1/" + EAR_RESOURCE_NAME);
    }

    @Test
    public void testMixed2Ear() throws Exception {
        test("/loader-test-mixed2/" + EAR_RESOURCE_NAME);
    }

    private File getResourceRoot(String path) throws URISyntaxException {
        return new File(getClass().getResource(path).toURI());
    }

    private ResourceLoader wrap(File resourceRoot) throws IOException {
        return ResourceLoaders.newResourceLoader(EAR_RESOURCE_NAME, resourceRoot);
    }

    private ResourceLoader nest(String subResourceLoaderName, ResourceLoader delegate, String subResourcePath) throws IOException {
        return ResourceLoaders.newResourceLoader(subResourceLoaderName, delegate, subResourcePath);
    }

    private void dumpResourceLoader(ResourceLoader loader) throws Exception {
        synchronized (System.out) {
            System.out.println("------------------------------");
            System.out.print(loader.getPath() + " -> ");
            System.out.println(loader.getRootName() + " -> " + loader.getRootURL() + " paths: ");
            Collection<String> paths = loader.getPaths();
            for (String path : paths) {
                System.out.println(" * " + path);
            }
            System.out.println(loader.getRootName() + " iteratePaths: ");
            Collection<String> iteratePaths = iteratorToCollection(loader.iteratePaths("", true));
            for (String path : iteratePaths) {
                System.out.println(" * " + path);
            }
            System.out.println(loader.getRootName() + " resources: ");
            Iterator<Resource> i = loader.iterateResources("", true);
            Resource r;
            while (i.hasNext()) {
                r = i.next();
                System.out.println(" * " + r.getName());
            }
        }
    }

    private void test(String ear) throws Exception {
        File earRoot = getResourceRoot(ear);
        ResourceLoader earLoader = wrap(earRoot);
        dumpResourceLoader(earLoader);
        // test ear resources
        for (String earRN : EAR_RESOURCE_NAMES) {
            // direct access
            Resource earResource = earLoader.getResource(earRN);
            assertTrue(earResource.getName().equals(earRN));
            assertFalse(isDirectory(earResource));
            // iterator access
            earResource = getResource(earLoader, earRN);
            assertTrue(earResource.getName().equals(earRN));
            assertFalse(isDirectory(earResource));
        }
        boolean explodedWar = earLoader.getPaths().contains(WAR_RESOURCE_NAME);
        if (!explodedWar) {
            // direct access
            Resource earResource = earLoader.getResource(WAR_RESOURCE_NAME);
            assertTrue(earResource.getName().equals(WAR_RESOURCE_NAME));
            assertFalse(isDirectory(earResource));
            // iterator access
            earResource = getResource(earLoader, WAR_RESOURCE_NAME);
            assertTrue(earResource.getName().equals(WAR_RESOURCE_NAME));
            assertFalse(isDirectory(earResource));
        }
        String warLoaderName = PathUtils.canonicalize(EAR_RESOURCE_NAME + "/" + WAR_RESOURCE_NAME);
        ResourceLoader warLoader = nest(warLoaderName, earLoader, WAR_RESOURCE_NAME);
        dumpResourceLoader(warLoader);
        // test ear paths
        Collection<String> earPaths = earLoader.getPaths();
        Collection<String> iteratorEarPaths = iteratorToCollection(earLoader.iteratePaths("", true));
        for (String earPath : EAR_PATHS) {
            assertTrue(earPaths.contains(earPath));
            assertTrue(iteratorEarPaths.contains(earPath));
        }
        earLoader.getPaths();
        // test war resources
        for (String warRN : WAR_RESOURCE_NAMES) {
            // direct access
            Resource warResource = warLoader.getResource(warRN);
            assertTrue(warResource.getName().equals(warRN));
            assertFalse(isDirectory(warResource));
            // iterator access
            warResource = getResource(warLoader, warRN);
            assertTrue(warResource.getName().equals(warRN));
            assertFalse(isDirectory(warResource));
            if (explodedWar) {
                // direct access
                Resource earResource = earLoader.getResource(WAR_RESOURCE_NAME + "/" + warRN);
                assertTrue(earResource.getName().equals(WAR_RESOURCE_NAME + "/" + warRN));
                assertFalse(isDirectory(earResource));
                // iterator access
                earResource = getResource(earLoader, WAR_RESOURCE_NAME + "/" + warRN);
                assertTrue(earResource.getName().equals(WAR_RESOURCE_NAME + "/" + warRN));
                assertFalse(isDirectory(earResource));
            }
        }
        boolean explodedJar = warLoader.getPaths().contains(JAR_RESOURCE_NAME);
        if (!explodedJar) {
            if (!explodedWar) {
                // direct access
                Resource warResource = warLoader.getResource(JAR_RESOURCE_NAME);
                assertTrue(warResource.getName().equals(JAR_RESOURCE_NAME));
                assertFalse(isDirectory(warResource));
                // iterator access
                warResource = getResource(warLoader, JAR_RESOURCE_NAME);
                assertTrue(warResource.getName().equals(JAR_RESOURCE_NAME));
                assertFalse(isDirectory(warResource));
            } else {
                // direct access
                Resource earResource = earLoader.getResource(WAR_RESOURCE_NAME + "/" + JAR_RESOURCE_NAME);
                assertTrue(earResource.getName().equals(WAR_RESOURCE_NAME + "/" + JAR_RESOURCE_NAME));
                assertFalse(isDirectory(earResource));
                // iterator access
                earResource = getResource(earLoader, WAR_RESOURCE_NAME + "/" + JAR_RESOURCE_NAME);
                assertTrue(earResource.getName().equals(WAR_RESOURCE_NAME + "/" + JAR_RESOURCE_NAME));
                assertFalse(isDirectory(earResource));
            }
        }
        String jarLoaderName = PathUtils.canonicalize(warLoaderName + "/" + JAR_RESOURCE_NAME);
        ResourceLoader jarLoader = nest(jarLoaderName, warLoader, JAR_RESOURCE_NAME);
        dumpResourceLoader(jarLoader);
        // test war paths
        Collection<String> warPaths = warLoader.getPaths();
        Collection<String> iteratorWarPaths = iteratorToCollection(warLoader.iteratePaths("", true));
        Collection<String> iteratorEarWarPaths = iteratorToCollection(earLoader.iteratePaths(WAR_RESOURCE_NAME, true));
        for (String warPath : WAR_PATHS) {
            assertTrue(warPaths.contains(warPath));
            assertTrue(iteratorWarPaths.contains(warPath));
            if (explodedWar) {
                String earResourceName = warPath.equals("") ? WAR_RESOURCE_NAME : WAR_RESOURCE_NAME + "/" + warPath;
                assertTrue(iteratorEarWarPaths.contains(earResourceName));
                assertTrue(earPaths.contains(earResourceName));
                assertTrue(iteratorEarPaths.contains(earResourceName));
            }
        }
        // test jar resources
        for (String jarRN : JAR_RESOURCE_NAMES) {
            // direct access
            Resource jarResource = jarLoader.getResource(jarRN);
            assertTrue(jarResource.getName().equals(jarRN));
            assertFalse(isDirectory(jarResource));
            // iterator access
            jarResource = getResource(jarLoader, jarRN);
            assertTrue(jarResource.getName().equals(jarRN));
            assertFalse(isDirectory(jarResource));
            if (explodedJar) {
                // direct access
                Resource warResource = warLoader.getResource(JAR_RESOURCE_NAME + "/" + jarRN);
                assertTrue(warResource.getName().equals(JAR_RESOURCE_NAME + "/" + jarRN));
                assertFalse(isDirectory(warResource));
                // iterator access
                warResource = getResource(warLoader, JAR_RESOURCE_NAME + "/" + jarRN);
                assertTrue(warResource.getName().equals(JAR_RESOURCE_NAME + "/" + jarRN));
                assertFalse(isDirectory(warResource));
                if (explodedWar) {
                    // direct access
                    Resource earResource = earLoader.getResource(WAR_RESOURCE_NAME + "/" + JAR_RESOURCE_NAME + "/" + jarRN);
                    assertTrue(earResource.getName().equals(WAR_RESOURCE_NAME + "/" + JAR_RESOURCE_NAME + "/" + jarRN));
                    assertFalse(isDirectory(earResource));
                    // iterator access
                    earResource = getResource(earLoader, WAR_RESOURCE_NAME + "/" + JAR_RESOURCE_NAME + "/" + jarRN);
                    assertTrue(earResource.getName().equals(WAR_RESOURCE_NAME + "/" + JAR_RESOURCE_NAME + "/" + jarRN));
                    assertFalse(isDirectory(earResource));
                }
            }
        }
        // test jar paths
        Collection<String> jarPaths = jarLoader.getPaths();
        Collection<String> iteratorJarPaths = iteratorToCollection(jarLoader.iteratePaths("", true));
        Collection<String> iteratorWarJarPaths = iteratorToCollection(warLoader.iteratePaths(JAR_RESOURCE_NAME, true));
        Collection<String> iteratorEarJarPaths = iteratorToCollection(earLoader.iteratePaths(WAR_RESOURCE_NAME + "/" + JAR_RESOURCE_NAME, true));

        for (String jarPath : JAR_PATHS) {
            assertTrue(jarPaths.contains(jarPath));
            assertTrue(iteratorJarPaths.contains(jarPath));
            if (explodedJar) {
                String warResourceName = jarPath.equals("") ? JAR_RESOURCE_NAME : JAR_RESOURCE_NAME + "/" + jarPath;
                assertTrue(warPaths.contains(warResourceName));
                assertTrue(iteratorWarJarPaths.contains(warResourceName));
                assertTrue(iteratorWarPaths.contains(warResourceName));
                if (explodedWar) {
                    warResourceName = WAR_RESOURCE_NAME + "/" + warResourceName;
                    assertTrue(iteratorEarJarPaths.contains(warResourceName));
                    assertTrue(iteratorEarWarPaths.contains(warResourceName));
                    String earResourceName = WAR_RESOURCE_NAME + "/" + JAR_RESOURCE_NAME + (jarPath.equals("") ? "" : "/" + jarPath);
                    assertTrue(earPaths.contains(earResourceName));
                    assertTrue(iteratorEarPaths.contains(earResourceName));
                }
            }
        }
        // assert loaders paths count
        assertTrue(jarPaths.size() == JAR_PATHS.length);
        assertTrue(iteratorJarPaths.size() == JAR_PATHS.length);
        if (explodedJar) {
            assertTrue(iteratorWarJarPaths.size() == JAR_PATHS.length);
            assertTrue(warPaths.size() == (WAR_PATHS.length + JAR_PATHS.length));
            assertTrue(iteratorWarPaths.size() == (WAR_PATHS.length + JAR_PATHS.length));
            if (explodedWar) {
                assertTrue(iteratorEarJarPaths.size() == JAR_PATHS.length);
                assertTrue(iteratorEarWarPaths.size() == (WAR_PATHS.length + JAR_PATHS.length));
                assertTrue(earPaths.size() == (EAR_PATHS.length + WAR_PATHS.length + JAR_PATHS.length));
                assertTrue(iteratorEarPaths.size() == (EAR_PATHS.length + WAR_PATHS.length + JAR_PATHS.length));
            } else {
                assertTrue(iteratorEarJarPaths.size() == 0);
                assertTrue(iteratorEarWarPaths.size() == 0);
                assertTrue(earPaths.size() == (EAR_PATHS.length));
                assertTrue(iteratorEarPaths.size() == (EAR_PATHS.length));
            }
        } else {
            assertTrue(iteratorWarJarPaths.size() == 0);
            assertTrue(iteratorEarJarPaths.size() == 0);
            assertTrue(warPaths.size() == WAR_PATHS.length);
            assertTrue(iteratorWarPaths.size() == WAR_PATHS.length);
            if (explodedWar) {
                assertTrue(iteratorEarWarPaths.size() == WAR_PATHS.length);
                assertTrue(earPaths.size() == (EAR_PATHS.length + WAR_PATHS.length));
                assertTrue(iteratorEarPaths.size() == (EAR_PATHS.length + WAR_PATHS.length));
            } else {
                assertTrue(iteratorEarWarPaths.size() == 0);
                assertTrue(earPaths.size() == (EAR_PATHS.length));
                assertTrue(iteratorEarPaths.size() == (EAR_PATHS.length));
            }
        }
        // assert loaders resource iterator size
        int earResourcesCount = getResourcesIteratorSize(earLoader);
        int warResourcesCount = getResourcesIteratorSize(warLoader);
        int jarResourcesCount = getResourcesIteratorSize(jarLoader);
        assertTrue(jarResourcesCount == JAR_RESOURCE_NAMES.length);
        if (explodedJar) {
            assertTrue(warResourcesCount == WAR_RESOURCE_NAMES.length + JAR_RESOURCE_NAMES.length);
            if (explodedWar) {
                assertTrue(earResourcesCount == EAR_RESOURCE_NAMES.length + WAR_RESOURCE_NAMES.length + JAR_RESOURCE_NAMES.length);
            } else {
                assertTrue(earResourcesCount == EAR_RESOURCE_NAMES.length + 1);
            }
        } else {
            assertTrue(warResourcesCount == WAR_RESOURCE_NAMES.length + 1);
            if (explodedWar) {
                assertTrue(earResourcesCount == EAR_RESOURCE_NAMES.length + WAR_RESOURCE_NAMES.length + 1);
            } else {
                assertTrue(earResourcesCount == EAR_RESOURCE_NAMES.length + 1);
            }
        }
    }

    private boolean isDirectory(Resource resource) {
        return resource.getName().endsWith("/");
    }

    private Resource getResource(ResourceLoader loader, String resourceName) {
        Iterator<Resource> i = loader.iterateResources("", true);
        Resource resource;
        while (i.hasNext()) {
            resource = i.next();
            if (resource.getName().equals(resourceName)) return resource;
        }
        throw new IllegalStateException();
    }

    private int getResourcesIteratorSize(ResourceLoader loader) {
        Iterator<Resource> i = loader.iterateResources("", true);
        int retVal = 0;
        while (i.hasNext()) {
            i.next();
            retVal++;
        }
        return retVal;
    }

    private static Collection<String> iteratorToCollection(final Iterator<String> iterator) {
        final List<String> retVal = new ArrayList<>();
        while (iterator.hasNext()) {
            retVal.add(iterator.next());
        }
        return retVal;
    }

}
