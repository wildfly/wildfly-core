/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.core.embedded;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class StandaloneSystemPropertyContextTestCase {
    @Rule
    public final TemporaryFolder temporaryUserHome = new TemporaryFolder();

    // clear the properties set by StandaloneSystemPropertyContext
    private Set<Object> startingProperties;

    private Path baseDir;
    private StandaloneSystemPropertyContext ctx;

    @Before
    public void setup() throws IOException {
        startingProperties = new HashSet<>(System.getProperties().keySet());
        baseDir = temporaryUserHome.newFolder("base").toPath();
        ctx = new StandaloneSystemPropertyContext(baseDir);
    }

    @After
    public void tearDown() {
        for (Object property : new HashSet<>(System.getProperties().keySet())) {
            if (!startingProperties.contains(property)) {
                System.clearProperty((String) property);
            }
        }
    }

    @Test
    public void testContentDirFollowsDataDir() throws Exception {
        Path dataFolder = temporaryUserHome.newFolder("data").toPath();

        System.setProperty("jboss.server.data.dir", dataFolder.toString());
        ctx.configureProperties();

        Assert.assertEquals(dataFolder.resolve("content").toString(), System.getProperty("jboss.server.content.dir"));
    }

    @Test
    public void testContentDirDefaultsToBase() throws Exception {
        ctx.configureProperties();

        Assert.assertEquals(Paths.get(baseDir.toString(), "standalone", "data", "content").toString(), System.getProperty("jboss.server.content.dir"));
    }
}
