/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;

import static org.jboss.as.patching.Constants.BASE;
import static org.jboss.as.patching.Constants.BUNDLES;
import static org.jboss.as.patching.Constants.LAYERS;
import static org.jboss.as.patching.Constants.MODULES;
import static org.jboss.as.patching.IoUtils.mkdir;
import static org.jboss.as.patching.runner.TestUtils.randomString;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.jboss.as.patching.Constants;
import org.jboss.as.patching.DirectoryStructure;
import org.jboss.as.patching.IoUtils;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.installation.InstallationManager;
import org.jboss.as.patching.installation.InstallationManagerImpl;
import org.jboss.as.patching.installation.InstalledIdentity;
import org.jboss.as.patching.tool.ContentVerificationPolicy;
import org.jboss.as.patching.tool.PatchTool;
import org.jboss.as.patching.tool.PatchingResult;
import org.jboss.as.version.ProductConfig;
import org.junit.After;
import org.junit.Before;


/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2012, Red Hat Inc
 */
public abstract class AbstractTaskTestCase {

    protected File tempDir;
    protected DirectoryStructure env;
    protected ProductConfig productConfig;

    @Before
    public void setup() throws Exception {
        tempDir = mkdir(new File(System.getProperty("java.io.tmpdir")), "patching-" + randomString());
        File jbossHome = mkdir(tempDir, "jboss-installation");
        mkdir(jbossHome, MODULES, "system", LAYERS, BASE);
        mkdir(jbossHome, BUNDLES, "system", LAYERS, BASE);
        env = TestUtils.createLegacyTestStructure(jbossHome);
        productConfig = new ProductConfig("product", "version", "consoleSlot");
    }

    @After
    public void tearDown() {
        if (!IoUtils.recursiveDelete(tempDir)) {
            tempDir.deleteOnExit();
        }
    }

    private InstallationManager loadInstallationManager() throws IOException {
        List<File> moduleRoots = new ArrayList<File>();
        moduleRoots.add(env.getInstalledImage().getModulesDir());
        List<File> bundleRoots = new ArrayList<File>();
        bundleRoots.add(env.getInstalledImage().getBundlesDir());
        return new InstallationManagerImpl(env.getInstalledImage(), moduleRoots, bundleRoots, productConfig);
    }

    public InstalledIdentity loadInstalledIdentity() throws IOException {
        return loadInstallationManager().getDefaultIdentity();
    }

    protected PatchTool newPatchTool() throws IOException {
        return PatchTool.Factory.create(loadInstallationManager());
    }

    protected PatchingResult executePatch(final File file) throws IOException, PatchingException {
        return executePatch(newPatchTool(), file);
    }

    protected PatchingResult executePatch(final PatchTool tool, final File file) throws IOException, PatchingException {
        final PatchingResult result = tool.applyPatch(file, ContentVerificationPolicy.STRICT);
        result.commit();
        return result;
    }

    protected PatchingResult rollback(String patchId) throws IOException, PatchingException {
        return rollback(newPatchTool(), patchId);
    }

    protected PatchingResult rollback(PatchTool tool, String patchId) throws IOException, PatchingException {
        return rollback(tool, patchId, false);
    }

    protected PatchingResult rollback(String patchId, final boolean rollbackTo) throws IOException, PatchingException {
        return rollback(newPatchTool(), patchId, rollbackTo);
    }

    protected PatchingResult rollback(PatchTool tool, String patchId, final boolean rollbackTo) throws IOException, PatchingException {
        return rollback(tool, patchId, rollbackTo, ContentVerificationPolicy.STRICT);
    }

    protected PatchingResult rollback(String patchId, boolean rollbackTo, ContentVerificationPolicy policy) throws IOException, PatchingException {
        return rollback(newPatchTool(), patchId, rollbackTo, policy);
    }

    protected PatchingResult rollback(PatchTool tool, String patchId, boolean rollbackTo, ContentVerificationPolicy policy) throws IOException, PatchingException {
        final PatchingResult result = tool.rollback(patchId, policy, rollbackTo, true);
        result.commit();
        return result;
    }

    protected void installLayers(String... layers) throws Exception {
        installLayers(true, layers);
    }

    protected void installLayers(boolean reflectInConf, String... layers) throws Exception {
        final File baseDir = env.getModuleRoot();
        for (String layer : layers) {
            IoUtils.mkdir(baseDir, "system", "layers", layer);
        }
        if (reflectInConf) {
            final File layerConf = env.getInstalledImage().getLayersConf();
            Properties props = new Properties();
            StringBuilder str = new StringBuilder();
            for (int i = 0; i < layers.length; i++) {
                if (i > 0) {
                    str.append(',');
                }
                str.append(layers[i]);
            }
            props.put(Constants.LAYERS, str.toString());
            try (final FileOutputStream os = new FileOutputStream(layerConf)){
                props.store(os, "");
            }
        }
    }
}
