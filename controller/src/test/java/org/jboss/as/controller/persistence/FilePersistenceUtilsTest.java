/*
 * Copyright 2018 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
