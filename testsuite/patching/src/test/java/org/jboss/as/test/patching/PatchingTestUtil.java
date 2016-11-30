/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

package org.jboss.as.test.patching;

import static java.lang.String.format;
import static org.jboss.as.patching.Constants.BASE;
import static org.jboss.as.patching.Constants.LAYERS;
import static org.jboss.as.patching.Constants.MODULES;
import static org.jboss.as.patching.Constants.OVERLAYS;
import static org.jboss.as.patching.Constants.SYSTEM;
import static org.jboss.as.patching.IoUtils.newFile;
import static org.jboss.as.patching.IoUtils.safeClose;
import static org.jboss.as.patching.logging.PatchLogger.ROOT_LOGGER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.jboss.as.patching.Constants;
import org.jboss.as.patching.HashUtils;
import org.jboss.as.patching.IoUtils;
import org.jboss.as.patching.ZipUtils;
import org.jboss.as.patching.metadata.BundledPatch;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.MiscContentItem;
import org.jboss.as.patching.metadata.ModificationType;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchBundleXml;
import org.jboss.as.patching.metadata.PatchXml;
import org.jboss.as.test.patching.util.module.Module;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

import com.google.common.base.Joiner;

/**
 * @author Jan Martiska, Jeff Mesnil
 */
public class PatchingTestUtil {

    private static final Logger logger = Logger.getLogger(PatchingTestUtil.class);

    private static final boolean isWindows = File.separatorChar == '\\';

    public static final String CONTAINER = "jboss";
    public static final String AS_DISTRIBUTION = System.getProperty("jbossas.dist");
    public static final String FILE_SEPARATOR = File.separator;
    public static final String RELATIVE_PATCHES_PATH = Joiner.on(FILE_SEPARATOR).join(new String[]{MODULES, SYSTEM, LAYERS, BASE, OVERLAYS});
    public static final String PATCHES_PATH = AS_DISTRIBUTION + FILE_SEPARATOR + RELATIVE_PATCHES_PATH;
    private static final String RELATIVE_MODULES_PATH = Joiner.on(FILE_SEPARATOR).join(new String[]{MODULES, SYSTEM, LAYERS, BASE});
    public static final String MODULES_PATH = AS_DISTRIBUTION + FILE_SEPARATOR + RELATIVE_MODULES_PATH;
    public static final File MODULES_DIRECTORY = newFile(new File(AS_DISTRIBUTION), MODULES);
    public static final File LAYERS_DIRECTORY = newFile(MODULES_DIRECTORY, SYSTEM, LAYERS);
    public static final File BASE_MODULE_DIRECTORY = newFile(LAYERS_DIRECTORY, BASE);
    public static final boolean DO_CLEANUP = Boolean.getBoolean("cleanup.tmp");

    public static final String AS_VERSION = ProductInfo.PRODUCT_VERSION;
    public static final String PRODUCT = ProductInfo.PRODUCT_NAME;

    public static String randomString() {
        return UUID.randomUUID().toString();
    }

    public static String randomString(String prefix) {
        return prefix + "-" + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);
    }

    /**
     * Converts the contents of a file into a String.
     *
     * @param filePath
     * @return
     * @throws java.io.FileNotFoundException
     */
    public static String readFile(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
    }

    public static void setFileContent(String filePath, String content) throws IOException {
        Files.write(Paths.get(filePath), content.getBytes(StandardCharsets.UTF_8));
    }

    public static void tree(File dir) {
        StringBuilder out = new StringBuilder();
        out.append(dir.getParentFile().getAbsolutePath() + "\n");
        tree0(out, dir, 1, "  ");
        logger.trace(out);
        ROOT_LOGGER.trace(out.toString());
    }

    private static void tree0(StringBuilder out, File dir, int indent, String tab) {
        StringBuilder shift = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            shift.append(tab);
        }
        out.append(shift + dir.getName() + "\n");
        for (File child : dir.listFiles()) {
            if (child.isDirectory()) {
                tree0(out, child, indent + 1, tab);
            } else {
                out.append(shift + tab + child.getName() + "\n");
            }
        }
    }

    public static File touch(File baseDir, String... segments) throws IOException {
        File f = baseDir;
        for (String segment : segments) {
            f = new File(f, segment);
        }
        f.getParentFile().mkdirs();
        f.createNewFile();
        return f;
    }

    public static void dump(File f, String content) throws IOException {
        final OutputStream os = new FileOutputStream(f);
        try {
            os.write(content.getBytes(StandardCharsets.UTF_8));
            os.close();
        } finally {
            IoUtils.safeClose(os);
        }
    }

    public static void dump(File f, byte[] content) throws IOException {
        final OutputStream os = new FileOutputStream(f);
        try {
            os.write(content);
            os.close();
        } finally {
            IoUtils.safeClose(os);
        }
    }

    public static File createModuleXmlFile(File mainDir, String moduleName, String... resources)
            throws IOException {
        StringBuilder content = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        content.append(
                format("<module xmlns=\"urn:jboss:module:1.2\" name=\"%s\" slot=\"main\">\n", moduleName));
        content.append("  <resources>\n");
        content.append("    <resource-root path=\".\"/>\n");
        for (String resource : resources) {
            content.append(format("    <resource-root path=\"%s\"/>\n", resource));
        }
        content.append("  </resources>\n");
        content.append("</module>\n");
        ROOT_LOGGER.trace(content);
        File moduleXMLFile = touch(mainDir, "module.xml");
        dump(moduleXMLFile, content.toString());
        return moduleXMLFile;
    }

    public static void createPatchXMLFile(File dir, Patch patch) throws Exception {
        File patchXMLfile = new File(dir, "patch.xml");
        FileOutputStream fos = new FileOutputStream(patchXMLfile);
        try {
            PatchXml.marshal(fos, patch);
        } finally {
            safeClose(fos);
        }
    }

    public static void createPatchBundleXMLFile(File dir, final List<BundledPatch.BundledPatchEntry> patches) throws Exception {
        File bundleXMLFile = new File(dir, "patches.xml");
        FileOutputStream fos = new FileOutputStream(bundleXMLFile);
        try {
            PatchBundleXml.marshal(fos, new BundledPatch() {
                @Override
                public List<BundledPatchEntry> getPatches() {
                    return patches;
                }
            });
        } finally {
            safeClose(fos);
        }
    }

    public static File createZippedPatchFile(File sourceDir, String zipFileName) {
        return createZippedPatchFile(sourceDir, zipFileName, null);
    }

    public static File createZippedPatchFile(File sourceDir, String zipFileName, File targetDir) {
        if (targetDir == null) {
            targetDir = sourceDir.getParentFile();
        }
        tree(sourceDir);
        File zipFile = new File(targetDir, zipFileName + ".zip");
        ZipUtils.zip(sourceDir, zipFile);
        return zipFile;
    }

    public static void assertPatchElements(File baseModuleDir, String[] expectedPatchElements) {
        assertPatchElements(baseModuleDir, expectedPatchElements, isWindows); // Skip this on windows
    }

    public static void assertPatchElements(File baseModuleDir, String[] expectedPatchElements, boolean skipCheck) {
        if (skipCheck) {
            return;
        }

        File modulesPatchesDir = new File(baseModuleDir, ".overlays");
        if (!modulesPatchesDir.exists()) {
            assertNull("Overlay directory does not exist, but it should", expectedPatchElements);
            return;
        }
        final List<File> patchDirs = Arrays.asList(modulesPatchesDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        }));
        if (expectedPatchElements == null) {
            assertTrue("Overlays directory should contain no directories, but contains: " + patchDirs.toString(), patchDirs.isEmpty());
        } else {
            final List<String> ids = Arrays.asList(expectedPatchElements);
            assertEquals("Overlays directory should contain " + expectedPatchElements.length + "  patches",
                    expectedPatchElements.length, patchDirs.size());
            for (File f : patchDirs) {
                assertTrue("Unexpected patch in .overlays directory: " + f.getName(), ids.contains(f.getName()));
            }
        }
    }

    public static void resetInstallationState(final File home, final File... layerDirs) {
        resetPatchStreams(home);
        for (final File root : layerDirs) {
            final File overlays = new File(root, Constants.OVERLAYS);
            IoUtils.recursiveDelete(overlays);
        }
    }

    protected static void resetPatchStreams(final File home) {
        IoUtils.recursiveDelete(new File(home, Constants.INSTALLATION));
    }

    static ContentModification updateModulesJar(final File installation, final File patchDir) throws IOException {
        final String fileName = "jboss-modules.jar";
        final File source = new File(installation, fileName);
        final File misc = new File(patchDir, "misc");
        misc.mkdirs();
        final File target = new File(misc, fileName);

        updateJar(source, target);

        final byte[] sourceHash = HashUtils.hashFile(source);
        final byte[] targetHash = HashUtils.hashFile(target);
        assert !Arrays.equals(sourceHash, targetHash);

        final MiscContentItem item = new MiscContentItem(fileName, new String[0], targetHash, false, false);
        return new ContentModification(item, sourceHash, ModificationType.MODIFY);

    }

    static void updateJar(final File source, final File target) throws IOException {
        final JavaArchive archive = ShrinkWrap.createFromZipFile(JavaArchive.class, source);
        archive.add(new StringAsset("test " + randomString()), "testFile");
        archive.as(ZipExporter.class).exportTo(target);
    }

    public static boolean isOneOffPatchContainedInHistory(List<ModelNode> patchingHistory, String patchID) {
        boolean found = false;
        for (ModelNode node : patchingHistory) {
            if (node.get("patch-id").asString().equals(patchID)) { found = true; }
        }
        return found;
    }

    public static ResourceItem createVersionItem(final String targetVersion) {
        final Asset newManifest = new Asset() {
            @Override
            public InputStream openStream() {
                return new ByteArrayInputStream(ProductInfo.createVersionString(targetVersion).getBytes(StandardCharsets.UTF_8));
            }
        };

        final JavaArchive versionModuleJar = ShrinkWrap.create(JavaArchive.class);
        if (!ProductInfo.isProduct) {
            versionModuleJar.addPackage("org.jboss.as.version");
        }
        versionModuleJar.addAsManifestResource(newManifest, "MANIFEST.MF");

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        versionModuleJar.as(ZipExporter.class).exportTo(baos);
        return new ResourceItem("as-version.jar", baos.toByteArray());
    }

    public static Module createVersionModule(final String targetVersion) {
        final ResourceItem item = createVersionItem(targetVersion);
        return new Module.Builder(ProductInfo.getVersionModule())
                .slot(ProductInfo.getVersionModuleSlot())
                .resourceRoot(item)
                .property("jboss.api", "private")
                .dependency("org.jboss.logging")
                .dependency("org.jboss.modules")
                .build();
    }


}
