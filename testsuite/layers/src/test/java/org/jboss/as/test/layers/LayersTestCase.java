/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.layers;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author jdenise@redhat.com
 */
public class LayersTestCase {
    // Packages that are provisioned by the test-standalone-reference installation
    // but not used in the test-all-layers installation.
    // This plus the {@link #NOT_USED_OR_REFERENCED} array are the expected set of
    // not provisioned modules when all layers are provisioned.
    private static final String[] NOT_USED = {
        // No patching modules in layers
        "org.jboss.as.patching",
        "org.jboss.as.patching.cli",
    };
    // Packages that are not referenced from the module graph but needed.
    // This is the expected set of un-referenced modules found when scanning
    // the test-standalone-reference configuration.
    private static final String[] NOT_REFERENCED = {
        //  injected by server in UndertowHttpManagementService
        "org.jboss.as.domain-http-error-context",
        // injected by elytron
        "org.wildfly.security.elytron",
        // injected by logging
        "org.apache.logging.log4j.api",
        // injected by logging
        "org.jboss.logging.jul-to-slf4j-stub",
        // injected by logging
        "org.jboss.logmanager.log4j2",
        // tooling
        "org.jboss.as.domain-add-user",
        // deployment-scanner not configured in default config
        "org.jboss.as.deployment-scanner",
        // Brought by galleon FP config
        "org.jboss.as.product",
        // Brought by galleon FP config
        "org.jboss.as.standalone",
        // Brought by galleon ServerRootResourceDefinition
        "wildflyee.api",
        // bootable jar runtime
        "org.wildfly.bootable-jar",
        // wildfly-elytron-tool
        "org.apache.commons.cli",
        "org.apache.commons.lang3",
        "org.wildfly.security.elytron-tool",
    };

    private static final String[] NOT_USED_OR_REFERENCED = {
            // deprecated and unused
            "ibm.jdk",
            "javax.api",
            "javax.sql.api",
            "javax.xml.stream.api",
            "sun.jdk",
            "sun.scripting",
            // Not currently used WildFly Core; only full WF
            "org.jboss.as.threads",
            "org.wildfly.event.logger",
            // Special support status -- wildfly-elytron-http-stateful-basic
            "org.wildfly.security.http.sfbasic"
    };

    /**
     * A HashMap to configure a banned module.
     * The key is the banned module name, the value is an optional List with the installation names that are allowed to
     * provision the banned module.
     */
    private static final HashMap<String, List<String>> BANNED_MODULES_CONF = new HashMap<>(){{
        put("org.jboss.as.security", null);
    }};

    private static String root;
    private static LayersTest.ScanContext scanContext;

    @BeforeClass
    public static void setUp() {
        root = System.getProperty("layers.install.root");
        scanContext = new LayersTest.ScanContext(root);
    }

    @AfterClass
    public static void cleanUp() {
        boolean delete = Boolean.getBoolean("layers.delete.installations");
        if(delete) {
            File[] installations = new File(root).listFiles(File::isDirectory);
            if (installations != null) {
                for (File f : installations) {
                    LayersTest.recursiveDelete(f.toPath());
                }
            }
        }
    }

    @Test
    public void testLayersModuleUse() throws Exception {
        LayersTest.testLayersModuleUse(LayersTest.concatArrays(NOT_USED_OR_REFERENCED, NOT_USED), scanContext);
    }

    @Test
    public void testUnreferencedModules() throws Exception {
        LayersTest.testUnreferencedModules(LayersTest.concatArrays(NOT_USED_OR_REFERENCED, NOT_REFERENCED), scanContext);
    }

    @Test
    public void testLayersBoot() throws Exception {
        LayersTest.testLayersBoot(root);
    }

    @Test
    public void checkBannedModules() throws Exception {
        HashMap<String, String> results = LayersTest.checkBannedModules(root, BANNED_MODULES_CONF);

        Assert.assertTrue("The following banned modules were provisioned " + results, results.isEmpty());
    }
}
