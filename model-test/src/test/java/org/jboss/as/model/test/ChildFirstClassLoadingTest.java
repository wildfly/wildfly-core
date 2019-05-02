/*
 * Copyright (C) 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file
 * in the distribution for a full listing of individual contributors.
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
package org.jboss.as.model.test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.regex.Pattern;

import org.jboss.as.model.test.api.SingleChildFirst1;
import org.jboss.as.model.test.api.SingleChildFirst2;
import org.jboss.as.model.test.api.SingleParentFirst;
import org.jboss.as.model.test.api.Welcome;
import org.jboss.as.model.test.child.WelcomeChild;
import org.jboss.as.model.test.parent.WelcomeParent;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.impl.base.exporter.zip.ZipExporterImpl;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2013 Red Hat, inc.
 */
public class ChildFirstClassLoadingTest {

    public ChildFirstClassLoadingTest() {
    }

    private static URL childJarURL, parentJarURL;

    @BeforeClass
    public static void createJars() throws Exception {
        String tempDir = System.getProperty("java.io.tmpdir");

        JavaArchive childJar = ShrinkWrap.create(JavaArchive.class, "child.jar").addClasses(WelcomeChild.class).addAsServiceProvider(Welcome.class, WelcomeChild.class);
        File childFile = new File(tempDir + File.separator + childJar.getName());
        new ZipExporterImpl(childJar).exportTo(childFile, true);
        childJarURL = childFile.toURI().toURL();
        childFile.deleteOnExit();

        JavaArchive parentJar = ShrinkWrap.create(JavaArchive.class, "parent.jar").addClasses(WelcomeParent.class).addAsServiceProvider(Welcome.class, WelcomeParent.class);
        File parentFile = new File(tempDir + File.separator + parentJar.getName());
        new ZipExporterImpl(parentJar).exportTo(parentFile, true);
        parentJarURL = parentFile.toURI().toURL();
        parentFile.deleteOnExit();
    }

    @Test
    public void testWithoutExclusion() throws Exception {
        URLClassLoader parent = new URLClassLoader(new URL[]{parentJarURL}, this.getClass().getClassLoader());
        parent.loadClass("org.jboss.as.model.test.parent.WelcomeParent");
        ChildFirstClassLoader child = new ChildFirstClassLoader(parent, new HashSet<Pattern>(), new HashSet<Pattern>(), null, null, new URL[]{childJarURL});
        Class<?> welcomeParent = child.loadClass("org.jboss.as.model.test.parent.WelcomeParent");
        Class<?> welcomeChild = child.loadClass("org.jboss.as.model.test.child.WelcomeChild");
        Class<?> welcome = this.getClass().getClassLoader().loadClass("org.jboss.as.model.test.api.Welcome");
        welcomeChild.asSubclass(welcome);
        welcomeParent.asSubclass(welcome);
    }

    @Test(expected = NoClassDefFoundError.class)
    public void testWithExclusion() throws Exception {
        URLClassLoader parent = new URLClassLoader(new URL[]{parentJarURL}, this.getClass().getClassLoader());
        parent.loadClass("org.jboss.as.model.test.parent.WelcomeParent");
        ChildFirstClassLoader child = new ChildFirstClassLoader(parent, new HashSet<Pattern>(), new HashSet<Pattern>(),
                SingleClassFilter.createFilter(Welcome.class),
                null, new URL[]{childJarURL});
        Class<?> welcomeParent = child.loadClass("org.jboss.as.model.test.parent.WelcomeParent");
        Class<?> welcomeChild = child.loadClass("org.jboss.as.model.test.child.WelcomeChild");
    }

    @Test
    public void testSingleClassFromDirectory() throws Exception {
        ChildFirstClassLoaderBuilder builder = new ChildFirstClassLoaderBuilder(false);
        builder.addSingleChildFirstClass(SingleChildFirst1.class, SingleChildFirst2.class);
        ClassLoader loader = builder.build();
        Class<?> clazz = loader.loadClass(SingleChildFirst1.class.getName());
        Assert.assertSame(loader, clazz.getClassLoader());
        clazz = loader.loadClass(SingleChildFirst2.class.getName());
        Assert.assertSame(loader, clazz.getClassLoader());
        clazz = loader.loadClass(SingleParentFirst.class.getName());
        Assert.assertNotSame(loader, clazz.getClassLoader());
        clazz = loader.loadClass(ChildFirstClassLoadingTest.class.getName());
        Assert.assertNotSame(loader, clazz.getClassLoader());
    }

    @Test
    public void testSingleClassFromJar() throws Exception {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "single-class-from-jar-test.jar")
                .addClasses(SingleChildFirst1.class, SingleChildFirst2.class, SingleParentFirst.class);
        String tempDir = System.getProperty("java.io.tmpdir");

        File file = new File(tempDir + File.separator + jar.getName());
        try {
            new ZipExporterImpl(jar).exportTo(file, true);
            URLClassLoader tmp = new ChildFirstClassLoaderBuilder(false)
                    .addURL(file.toURI().toURL())
                    .build();
            Class<?> scf1 = tmp.loadClass(SingleChildFirst1.class.getName());
            Assert.assertSame(tmp, scf1.getClassLoader());
            Class<?> scf2 = tmp.loadClass(SingleChildFirst2.class.getName());
            Assert.assertSame(tmp, scf2.getClassLoader());

            URLClassLoader loader = new ChildFirstClassLoaderBuilder(false)
                    .addSingleChildFirstClass(scf1, scf2)
                    .build();
            Assert.assertSame(loader, loader.loadClass(SingleChildFirst1.class.getName()).getClassLoader());
            Assert.assertSame(loader, loader.loadClass(SingleChildFirst2.class.getName()).getClassLoader());
            Assert.assertNotSame(loader, loader.loadClass(SingleParentFirst.class.getName()).getClassLoader());
            loader.close();
        } finally {
            file.delete();
        }
    }

    @Test
    public void testServiceLoaderWithoutExclusion() throws Exception {
        URLClassLoader parent = new URLClassLoader(new URL[]{parentJarURL}, this.getClass().getClassLoader());
        parent.loadClass("org.jboss.as.model.test.parent.WelcomeParent");
        ChildFirstClassLoader child = new ChildFirstClassLoader(parent, new HashSet<Pattern>(), new HashSet<Pattern>(), null, null, new URL[]{childJarURL});
        Class<?> welcomeParent = child.loadClass("org.jboss.as.model.test.parent.WelcomeParent");
        Class<?> welcomeChild = child.loadClass("org.jboss.as.model.test.child.WelcomeChild");
        Class<?> welcome = this.getClass().getClassLoader().loadClass("org.jboss.as.model.test.api.Welcome");
        ServiceLoader loader = ServiceLoader.load(welcome, child);
        int loaded = 0;
        Set<Class<?>> impls = new HashSet<>(Arrays.asList(welcomeParent, welcomeChild));
        for (Object svc : loader) {
            impls.remove(svc.getClass());
            loaded++;
        }
        Assert.assertTrue(impls.toString(), impls.isEmpty());
        Assert.assertEquals(2, loaded);
    }

    @Test
    public void testServiceLoaderWithSpecificExclusion() throws Exception {
        serviceLoaderWithExclusionTest("META-INF/services/org.jboss.as.model.test.api.Welcome");
    }

    @Test
    public void testServiceLoaderWithWildcardExclusion() throws Exception {
        serviceLoaderWithExclusionTest("META-INF/services/.*");
    }

    private void serviceLoaderWithExclusionTest(String exclusion) throws Exception {
        URLClassLoader parent = new URLClassLoader(new URL[]{parentJarURL}, this.getClass().getClassLoader());
        parent.loadClass("org.jboss.as.model.test.parent.WelcomeParent");
        ChildFirstClassLoader child = new ChildFirstClassLoader(parent, new HashSet<Pattern>(), new HashSet<Pattern>(), null, Pattern.compile(exclusion), new URL[]{childJarURL});
        Class<?> welcomeParent = child.loadClass("org.jboss.as.model.test.parent.WelcomeParent");
        Class<?> welcomeChild = child.loadClass("org.jboss.as.model.test.child.WelcomeChild");
        Class<?> welcome = this.getClass().getClassLoader().loadClass("org.jboss.as.model.test.api.Welcome");
        ServiceLoader loader = ServiceLoader.load(welcome, child);
        int loaded = 0;
        Set<Class<?>> impls = new HashSet<>(Collections.singleton(welcomeChild));
        for (Object svc : loader) {
            impls.remove(svc.getClass());
            loaded++;
        }
        Assert.assertTrue(impls.toString(), impls.isEmpty());
        Assert.assertEquals(1, loaded);
    }
}
