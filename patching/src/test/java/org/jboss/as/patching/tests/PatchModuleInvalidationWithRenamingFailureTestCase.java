/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.patching.tests;

import org.jboss.as.patching.HashUtils;
import org.jboss.as.patching.IoUtils;
import org.jboss.as.patching.installation.PatchableTarget;
import org.jboss.as.patching.runner.PatchUtils;
import org.jboss.as.patching.runner.TestUtils;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.patching.runner.TestUtils.createModule0;
import static org.jboss.as.patching.runner.TestUtils.randomString;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
@RunWith(BMUnitRunner.class)
public class PatchModuleInvalidationWithRenamingFailureTestCase extends AbstractPatchingTest {

    private static final String MODULE_NAME = "org.jboss.test.module";
    private static final String RESOURCE = "SimpleResource.jar";

    private static final TestUtils.ContentTask CONTENT_TASK = new TestUtils.ContentTask() {
        @Override
        public String[] writeContent(File target) throws IOException {
            writeJar(new File(target, RESOURCE));
            return new String[]{RESOURCE};
        }
    };

    @Test
    @BMRule(name = "Test renaming failure",
            targetClass = "java.io.File",
            targetMethod = "renameTo",
            targetLocation = "AT ENTRY",
            condition = "\"SimpleResource.jar.patched\".equals($1.getName())",
            action = "return false"
    )
    public void test() throws Exception {
        final PatchingTestBuilder test = createDefaultBuilder();
        final File root = test.getRoot();
        final File installation = new File(root, JBOSS_INSTALLATION);
        final File moduleRoot = new File(installation, "modules/system/layers/base".replace('/', File.separatorChar));
        final File module0 = createModule0(moduleRoot, MODULE_NAME, CONTENT_TASK);
        final File resource = new File(module0, "main/SimpleResource.jar".replace('/', File.separatorChar));
        final File resourceBackup = new File(module0, "main/SimpleResource.jar.patched".replace('/', File.separatorChar));
        final byte[] existingHash = HashUtils.hashFile(module0);
        final byte[] resultingHash = Arrays.copyOf(existingHash, existingHash.length);
        assertLoadable(resource);
        final PatchingTestStepBuilder oop1 = test.createStepBuilder();
        oop1.setPatchId("oop1")
                .oneOffPatchIdentity(PRODUCT_VERSION)
                .oneOffPatchElement("base-oop1", "base", false)
                .updateModule(MODULE_NAME, existingHash, resultingHash, CONTENT_TASK);

        apply(oop1);
        assertThat(resourceBackup.exists(), is(false));
        assertThat(resource.exists(), is(true));
        final File failures = new File(new File(installation, ".installation"), "cleanup-renaming-files");
        assertThat(failures.exists(), is(true));
        List<String> failedRenaming = PatchUtils.readRefs(failures);
        assertThat(failedRenaming.size(), is(1));
        assertThat(failedRenaming.get(0), is(resource.getAbsolutePath()));
    }

    File getModuleResource(final String patchID, final String moduleName) throws IOException {
        return getModuleResource("base", patchID, moduleName);
    }

    File getModuleResource(final String layer, final String patchID, final String moduleName) throws IOException {
        final PatchableTarget.TargetInfo info = getLayer(layer).loadTargetInfo();
        final File root = info.getDirectoryStructure().getModulePatchDirectory(patchID);
        final File moduleRoot = TestUtils.getModuleRoot(root, moduleName);
        return new File(moduleRoot, RESOURCE);
    }

    static File writeJar(final File target) {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class)
                .addClass(TestClass.class)
                .add(new StringAsset(randomString()), "testResource")
                .addManifest();
        archive.as(ZipExporter.class).exportTo(target);
        return target;
    }

    static void assertLoadable(final File jar) throws Exception {
        final URL[] urls = new URL[]{jar.toURI().toURL()};
        final URLClassLoader cl = new URLClassLoader(urls);
        Assert.assertNotNull(cl.getResource("testResource"));
        final Class<?> clazz = cl.loadClass("org.jboss.as.patching.tests.TestClass");
        final Constructor<?> constructor = clazz.getConstructor(String.class);
        final Object instance = constructor.newInstance("test");
        Assert.assertNotNull(instance);
        cl.close();
    }

    static void assertNotLoadable(final File jar) throws Exception {
        final URL[] urls = new URL[]{jar.toURI().toURL()};
        final URLClassLoader cl = new URLClassLoader(urls, null);
        Assert.assertNull(cl.getResource("testResource"));
        try {
            cl.loadClass("org.jboss.as.patching.tests.TestClass");
            Assert.fail("shouldn't be able to load the test class");
        } catch (ClassNotFoundException ok) {
        }

        ZipFile file = null;
        try {
            file = new ZipFile(jar);
            Assert.fail("should not be able to open" + jar);
        } catch (ZipException expected) {
            // ok
            return;
        } finally {
            IoUtils.safeClose(file);
        }
    }
}
