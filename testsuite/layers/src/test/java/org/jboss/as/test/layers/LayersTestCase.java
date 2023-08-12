/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.layers;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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
    // This is the expected set of not provisioned modules when all layers are provisioned.
    private static final String[] NOT_USED = {
        // deprecated and unused
        "ibm.jdk",
        "javax.api",
        "javax.sql.api",
        "javax.xml.stream.api",
        "org.jboss.as.threads",
        "sun.jdk",
        "sun.scripting",
        // No patching modules in layers
        "org.jboss.as.patching",
        "org.jboss.as.patching.cli",
        // Not currently used internally
        "org.wildfly.event.logger",
        // wildfly-elytron-http-stateful-basic
        "org.wildfly.security.http.sfbasic",
        // wildfly-elytron-tool
        "org.apache.commons.cli",
        "org.apache.commons.lang3",
        "org.wildfly.security.elytron-tool",
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
    };

    /**
     * A HashMap to configure a banned module.
     * They key is the banned module name, the value is an optional List with the installation names that are allowed to
     * provision the banned module. This installations will be ignored.
     */
    private static final HashMap<String, List<String>> BANNED_MODULES_CONF = new HashMap<String, List<String>>(){{
        put("org.jboss.as.security", null);
    }};

    public static String root;

    @BeforeClass
    public static void setUp() {
        root = System.getProperty("layers.install.root");
    }

    @AfterClass
    public static void cleanUp() {
        Boolean delete = Boolean.getBoolean("layers.delete.installations");
        if(delete) {
            File[] installations = new File(root).listFiles(File::isDirectory);
            for(File f : installations) {
                LayersTest.recursiveDelete(f.toPath());
            }
        }
    }

    @Test
    public void test() throws Exception {
        LayersTest.test(root, new HashSet<>(Arrays.asList(NOT_REFERENCED)),
                new HashSet<>(Arrays.asList(NOT_USED)));
    }

    @Test
    public void checkBannedModules() throws Exception {
        HashMap<String, String> results = LayersTest.checkBannedModules(root, BANNED_MODULES_CONF);

        Assert.assertTrue("The following banned modules were provisioned " + results.toString(), results.isEmpty());
    }
}
