/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.tests;

import static org.jboss.as.patching.Constants.BASE;
import static org.jboss.as.patching.Constants.BUNDLES;
import static org.jboss.as.patching.Constants.LAYERS;
import static org.jboss.as.patching.Constants.MODULES;
import static org.jboss.as.patching.IoUtils.mkdir;
import static org.jboss.as.patching.runner.TestUtils.randomString;

import java.io.File;
import java.io.IOException;

import org.jboss.as.patching.IoUtils;
import org.junit.After;
import org.junit.Before;

/**
 * @author Emanuel Muckenhuber
 */
public class AbstractPatchingTest {

    protected static final String JBOSS_INSTALLATION = "jboss-installation";

    private static final String SYSTEM_TEMP_DIR = System.getProperty("java.io.tmpdir");

    protected File tempDir;

    @Before
    public void setUp() throws IOException {
        tempDir = mkdir(new File(SYSTEM_TEMP_DIR), randomString());
        final File jbossHome = mkdir(tempDir, JBOSS_INSTALLATION);
        mkdir(jbossHome, MODULES, "system", LAYERS, BASE);
        mkdir(jbossHome, BUNDLES, "system", LAYERS, BASE);
    }

    @After
    public void tearDown() {
        if (!IoUtils.recursiveDelete(tempDir)) {
            tempDir.deleteOnExit();
        }
    }
}
