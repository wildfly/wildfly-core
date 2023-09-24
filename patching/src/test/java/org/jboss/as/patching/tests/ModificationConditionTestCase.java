/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.tests;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.jboss.as.patching.PatchingException;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class ModificationConditionTestCase extends AbstractPatchingTest {

    static final String DOCS = "docs";
    static final String INSTALLED = "installed";
    static final String MANUAL = "manual.txt";
    static final String NOT_INSTALLED = "not_installed";
    static final String OTHER = "other";

    static final String[] INSTALLED_PATH = new String[]{DOCS, INSTALLED};
    static final String[] NOT_INSTALLED_PATH = new String[]{DOCS, NOT_INSTALLED};

    static final String[] INSTALLED_MANUAL = new String[]{DOCS, INSTALLED, MANUAL};
    static final String[] NOT_INSTALLED_MANUAL = new String[]{DOCS, NOT_INSTALLED, MANUAL};
    static final String[] OTHER_MANUAL = new String[]{OTHER, MANUAL};

    @Test
    public void testMisc() throws IOException, PatchingException {

        final PatchingTestBuilder builder = createDefaultBuilder();
        final byte[] installedHash = new byte[20];
        final byte[] notInstalledHash = new byte[20];
        final byte[] otherHash = new byte[20];
        final byte[] moduleHash = new byte[20];

        // Create a file
        final File installedDir = builder.getFile(DOCS, INSTALLED);
        assertTrue(installedDir.mkdirs());

        final PatchingTestStepBuilder cp1 = builder.createStepBuilder();
        cp1.setPatchId("CP1")
                .upgradeIdentity(PRODUCT_VERSION, PRODUCT_VERSION)
                .upgradeElement("base-CP1", "base", false)
                .addModuleWithRandomContent("org.jboss.test", moduleHash)
                .getParent()
                .addFileWithRandomContent(installedHash, INSTALLED_MANUAL, INSTALLED_PATH)
                .addFileWithRandomContent(notInstalledHash, NOT_INSTALLED_MANUAL, NOT_INSTALLED_PATH)
                .addFileWithRandomContent(otherHash, OTHER_MANUAL, INSTALLED_PATH);

        // Apply CP1
        apply(cp1);

        Assert.assertTrue(builder.hasFile(DOCS, INSTALLED, MANUAL));
        Assert.assertTrue(builder.hasFile(OTHER, MANUAL));
        Assert.assertFalse(builder.hasFile(DOCS, NOT_INSTALLED, MANUAL));

        final PatchingTestStepBuilder oneOff1 = builder.createStepBuilder();
        oneOff1.setPatchId("oneOff1")
                .oneOffPatchIdentity(PRODUCT_VERSION)
                .oneOffPatchElement("base-oneOff1", "base", false)
                .updateModuleWithRandomContent("org.jboss.test", moduleHash, null)
                .getParent()
                .updateFileWithRandomContent(installedHash, null, INSTALLED_MANUAL, INSTALLED_PATH)
                .updateFileWithRandomContent(notInstalledHash, null, NOT_INSTALLED_MANUAL, NOT_INSTALLED_PATH)
                .updateFileWithRandomContent(otherHash, null, OTHER_MANUAL, INSTALLED_PATH);

        // Apply oneOff1
        apply(oneOff1);

        Assert.assertTrue(builder.hasFile(DOCS, INSTALLED, MANUAL));
        Assert.assertTrue(builder.hasFile(OTHER, MANUAL));
        Assert.assertFalse(builder.hasFile(DOCS, NOT_INSTALLED, MANUAL));

        final PatchingTestStepBuilder cp2 = builder.createStepBuilder();
        cp2.setPatchId("CP2")
                .upgradeIdentity(PRODUCT_VERSION, PRODUCT_VERSION)
                .upgradeElement("base-CP2", "base", false)
                .updateModuleWithRandomContent("org.jboss.test", moduleHash, null)
                .getParent()
                .removeFile(installedHash, INSTALLED_MANUAL, INSTALLED_PATH)
                .removeFile(notInstalledHash, NOT_INSTALLED_MANUAL, NOT_INSTALLED_PATH)
                .removeFile(otherHash, OTHER_MANUAL, INSTALLED_PATH);

        // Apply CP2
        apply(cp2);

        Assert.assertFalse(builder.hasFile(DOCS, INSTALLED, MANUAL));
        Assert.assertFalse(builder.hasFile(DOCS, NOT_INSTALLED, MANUAL));
        Assert.assertFalse(builder.hasFile(OTHER, MANUAL));

        rollback(cp2);

        Assert.assertTrue(builder.hasFile(DOCS, INSTALLED, MANUAL));
        Assert.assertTrue(builder.hasFile(OTHER, MANUAL));
        Assert.assertFalse(builder.hasFile(DOCS, NOT_INSTALLED, MANUAL));

        rollback(oneOff1);

        Assert.assertTrue(builder.hasFile(DOCS, INSTALLED, MANUAL));
        Assert.assertTrue(builder.hasFile(OTHER, MANUAL));
        Assert.assertFalse(builder.hasFile(DOCS, NOT_INSTALLED, MANUAL));

        rollback(cp1);

        Assert.assertFalse(builder.hasFile(DOCS, INSTALLED, MANUAL));
        Assert.assertFalse(builder.hasFile(OTHER, MANUAL));
        Assert.assertFalse(builder.hasFile(DOCS, NOT_INSTALLED, MANUAL));
    }
}
