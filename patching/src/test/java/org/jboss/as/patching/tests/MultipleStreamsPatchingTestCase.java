/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.tests;

import static org.jboss.as.patching.HashUtils.hashFile;
import static org.jboss.as.patching.runner.TestUtils.dump;
import static org.jboss.as.patching.runner.TestUtils.randomString;
import static org.jboss.as.patching.runner.TestUtils.touch;

import java.io.File;
import java.util.Arrays;

import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class MultipleStreamsPatchingTestCase extends AbstractPatchingTest {

    static final String[] FILE_ONE = {"bin", "standalone.sh"};
    static final String[] FILE_TWO = {"bin", "standalone.conf"};
    static final String[] FILE_EXISTING = {"bin", "test"};


    @Test
    public void testOne() throws Exception {

        final PatchingTestBuilder builder = createDefaultBuilder("layer1", "base");
        final byte[] standaloneHash = new byte[20];
        final byte[] moduleHash = new byte[20];

        // Create a file
        final File existing = builder.getFile(FILE_EXISTING);
        touch(existing);
        dump(existing, randomString());
        final byte[] existingHash = hashFile(existing);
        final byte[] initialHash = Arrays.copyOf(existingHash, existingHash.length);

        final PatchingTestStepBuilder wfcp1 = builder.createStepBuilder();
        wfcp1.setPatchId("WFCP1")
                .upgradeIdentity("WildFly", PRODUCT_VERSION, PRODUCT_VERSION)
                .upgradeElement("base-WFCP1", "base", false)
                .addModuleWithRandomContent("org.jboss.test", moduleHash)
                .getParent()
                .addFileWithRandomContent(standaloneHash, FILE_ONE)
                .updateFileWithRandomContent(initialHash, existingHash, FILE_EXISTING);
        apply(wfcp1);

        final PatchingTestStepBuilder lpcp1 = builder.createStepBuilder();
        lpcp1.setPatchId("LPCP1")
                .upgradeIdentity("LayeredProduct", "0.0.1", "0.0.2")
                .upgradeElement("layer1-LPCP1", "layer1", false)
                .addModuleWithRandomContent("org.jboss.test3", moduleHash);
        apply(lpcp1);

        final PatchingTestStepBuilder wfcp2 = builder.createStepBuilder();
        wfcp2.setPatchId("WFCP2")
                .upgradeIdentity("WildFly", PRODUCT_VERSION, PRODUCT_VERSION)
                .upgradeElement("base-WFCP2", "base", false)
                .addModuleWithRandomContent("org.jboss.test2", moduleHash)
                .getParent()
                .updateFileWithRandomContent(existingHash, existingHash, FILE_EXISTING);
        apply(wfcp2);

        rollback(lpcp1);
        apply(lpcp1);

        rollback(wfcp2);
        rollback(wfcp1);
        rollback(lpcp1);
    }
}
