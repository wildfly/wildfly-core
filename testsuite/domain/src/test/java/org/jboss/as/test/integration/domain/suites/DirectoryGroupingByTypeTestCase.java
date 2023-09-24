/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.suites;

import static org.junit.Assert.*;

import java.io.File;

import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test that <servers directory-grouping="by-type"> results in the proper directory organization
 *
 * @author @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class DirectoryGroupingByTypeTestCase {

    private static DomainTestSupport testSupport;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(DirectoryGroupingByTypeTestCase.class.getSimpleName());
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        DomainTestSuite.stopSupport();
    }

    @Test
    public void testDirectoryLocations() throws Exception {
        File baseDir = new File(testSupport.getDomainSecondaryConfiguration().getDomainDirectory());
        validateDirectory(baseDir);
        File data = new File(baseDir, "data");
        validateDirectory(data);
        validateServerDirectory(data);
        File log = new File(baseDir, "log");
        validateDirectory(log);
        validateServerDirectory(log);
        File tmp = new File(baseDir, "tmp");
        validateDirectory(tmp);
        validateServerDirectory(tmp);
    }

    private void validateServerDirectory(File typeDir) {
        File servers = new File(typeDir, "servers");
        validateDirectory(servers);
        File server = new File(servers, "main-three");
        validateDirectory(server);
    }

    private void validateDirectory(File file) {
        assertTrue(file + " exists", file.exists());
        assertTrue(file + " is a directory", file.isDirectory());
    }
}
