/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.persistence;

import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Emmanuel Hugonnet (c) 2018 Red Hat, inc.
 */
public class FilePersistenceUtilsTest {

    @Test
    public void testSanitizeFileName() {
        String name = "name.é";
        Assert.assertEquals("name.é", FilePersistenceUtils.sanitizeFileName(name));
        name = "/name.é";
        Assert.assertEquals("name.é",FilePersistenceUtils.sanitizeFileName(name));
        name = "name.é\\";
        Assert.assertEquals("name.é",FilePersistenceUtils.sanitizeFileName(name));
        name = "\\name.txt";
        Assert.assertEquals("name.txt", FilePersistenceUtils.sanitizeFileName(name));
        name = "../name.txt";
        Assert.assertEquals("..name.txt",FilePersistenceUtils.sanitizeFileName(name));
        name = "~/name.txt";
        Assert.assertEquals("~name.txt",FilePersistenceUtils.sanitizeFileName(name));
        name = "name.txt\r\n";
        Assert.assertEquals("name.txt",FilePersistenceUtils.sanitizeFileName(name));
    }
}
