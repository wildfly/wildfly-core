/*
 * Copyright 2016 JBoss by Red Hat.
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
package org.jboss.as.repository;


import static org.jboss.as.repository.PathUtil.deleteRecursively;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test to check the lock when removing content while reading it.
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
@RunWith(BMUnitRunner.class)
public class DeletionCollisionTest {
    private ContentRepository repository;
    private final File rootDir = new File("target", "repository");
    private final File tmpRootDir = new File("target", "tmp");
    private final long EXECUTION_TIMEOUT = 30000L;

    public DeletionCollisionTest() {
    }

    @Before
    public void createRepository() throws IOException {
        if (rootDir.exists()) {
            deleteRecursively(rootDir.toPath());
        }
        rootDir.mkdirs();
        if (tmpRootDir.exists()) {
            deleteRecursively(tmpRootDir.toPath());
        }
        tmpRootDir.mkdirs();
        repository = ContentRepository.Factory.create(rootDir, tmpRootDir, 0L, 1000L);
        repository.readWrite();
    }

    @After
    public void destroyRepository() throws IOException {
        deleteRecursively(rootDir.toPath());
        deleteRecursively(tmpRootDir.toPath());
        repository = null;
    }

    @BMScript(value="lockDeployment.btm")
    @Test
    public void testFileLockByReadContent() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("overlay.xhtml")) {
            final byte[] deploymentHash = repository.addContentToExploded(
                    repository.addContent(null),
                    Collections.singletonList(new ExplodedContent("overlay.xhtml", stream)),
                    true);
            Assert.assertTrue(repository.hasContent(deploymentHash));
            ExecutorService executor = Executors.newFixedThreadPool(2);
            Future<InputStream> result = executor.submit(() -> repository.readContent(deploymentHash, "overlay.xhtml"));
            Thread.sleep(100L);
            Future deletion = executor.submit(() -> {
                repository.removeContent(new ContentReference("overlay.xhtml", deploymentHash));
            });
            deletion.get(EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS);
            result.get(EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS);
            try (TemporaryFileInputStream in = (TemporaryFileInputStream) result.get(EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS)) {
                Assert.assertTrue(Files.exists(in.getFile()));
            }
        }
    }

    @BMScript(value="lockDeployment.btm")
    @Test
    public void testFileLockByRemoveContent() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("overlay.xhtml")) {
            final byte[] deploymentHash = repository.addContentToExploded(
                    repository.addContent(null),
                    Collections.singletonList(new ExplodedContent("overlay.xhtml", stream)),
                    true);
            Assert.assertTrue(repository.hasContent(deploymentHash));
            ExecutorService executor = Executors.newFixedThreadPool(2);
            Future deletion = executor.submit(() -> {
                repository.removeContent(new ContentReference("overlay.xhtml", deploymentHash));
            });
            Thread.sleep(100L);
            Future<InputStream> result = executor.submit(() -> repository.readContent(deploymentHash, "overlay.xhtml"));
            deletion.get(EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS);
            try {
                result.get(EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS);
                Assert.fail("Shouldn't be able to lock the content");
            } catch(ExecutionException ex) {
                Assert.assertTrue(ex.getCause().getMessage().contains("WFLYDR0019"));
            }
        }
    }
}
