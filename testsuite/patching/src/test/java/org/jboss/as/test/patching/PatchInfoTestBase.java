/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.patching;

import static org.jboss.as.patching.IoUtils.mkdir;
import static org.jboss.as.test.patching.PatchingTestUtil.createPatchXMLFile;
import static org.jboss.as.test.patching.PatchingTestUtil.createZippedPatchFile;
import static org.jboss.as.test.patching.PatchingTestUtil.randomString;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchBuilder;
import org.jboss.as.test.patching.util.module.Module;
import org.jboss.as.version.ProductConfig;
import org.junit.Assert;
import org.junit.Before;

/**
 *
 * @author Alexey Loubyansky
 */
public class PatchInfoTestBase extends AbstractPatchingTestCase {

    protected ProductConfig productConfig;

    @Before
    public void initProductConfig() throws Exception {
        productConfig = new ProductConfig(PatchingTestUtil.PRODUCT, PatchingTestUtil.AS_VERSION, "main");
    }

    protected byte[] applyOneOff(String patchId, byte[] targetHash) throws IOException, Exception {
        return applyOneOff(patchId, randomString(), targetHash);
    }

    protected byte[] applyOneOff(String patchId, String patchElementId, byte[] targetHash) throws IOException, Exception {
        return applyPatch(patchId, patchElementId, false, targetHash);
    }

    protected byte[] applyCP(String patchId, byte[] targetHash) throws Exception {
        return applyCP(patchId, randomString(), targetHash);
    }

    protected byte[] applyCP(String patchId, String patchElementId, byte[] targetHash) throws Exception {
        return applyPatch(patchId, patchElementId, true, targetHash);
    }

    protected byte[] applyPatch(String patchID, String patchElementId, boolean cp, byte[] targetHash) throws Exception {
        final byte[] result = applyPatch(productConfig.getProductName(), productConfig.getProductVersion(),
                cp ? PatchingTestUtil.AS_VERSION + "_CP" + patchID : null,
                patchID, "base", patchElementId, targetHash);
        if (cp) {
            productConfig = new ProductConfig(PatchingTestUtil.PRODUCT, PatchingTestUtil.AS_VERSION + "_CP" + patchID, "main");
        }
        return result;
    }

    protected byte[] applyPatch(String product, String version, String newVersion, String patchID, String layer, String layerPatchId, byte[] targetHash) throws Exception {

        final File patchDir = mkdir(tempDir, patchID);

        final Module module = new Module.Builder("module-test").
                miscFile(new ResourceItem("resource-test", ("resource patch " + patchID).getBytes(StandardCharsets.UTF_8))).
                build();

        // create the patch with the updated module
        final ContentModification moduleModified = ContentModificationUtils.modifyModule(patchDir, layerPatchId, targetHash, module);

        PatchBuilder patchBuilder = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription(randomString());
        if (newVersion != null) {
            patchBuilder = patchBuilder.
                    upgradeIdentity(product, version, newVersion).
                    getParent().
                    upgradeElement(layerPatchId, layer, false).
                    addContentModification(moduleModified).
                    getParent();
        } else {
            patchBuilder = patchBuilder.
                    oneOffPatchIdentity(product, version).
                    getParent().
                    oneOffPatchElement(layerPatchId, layer, false).
                    addContentModification(moduleModified).
                    getParent();
        }
        final Patch patch = patchBuilder.build();

        // create the patch
        createPatchXMLFile(patchDir, patch);

        final File zippedPatch = createZippedPatchFile(patchDir, patch.getPatchId());

        // apply the patch and check if server is in restart-required mode
        controller.start();
        try {
            Assert.assertTrue("Patch should be accepted", CliUtilsForPatching.applyPatch(zippedPatch.getAbsolutePath()));
            Assert.assertTrue("server should be in restart-required mode", CliUtilsForPatching.doesServerRequireRestart());
        } finally {
            controller.stop();
        }
        return moduleModified.getItem().getContentHash();
    }
}