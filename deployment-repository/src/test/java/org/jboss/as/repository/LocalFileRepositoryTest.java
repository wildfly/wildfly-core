/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.repository;

import static org.jboss.as.repository.PathUtil.deleteRecursively;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LocalFileRepositoryTest {

    private final Path root = new File("target", "temp").toPath().resolve("localfilerepository");


    @Before
    public void createRepository() throws IOException {
        if (Files.exists(root)) {
            deleteRecursively(root);
        }
        Files.createDirectories(root);
    }

    @After
    public void destroyRepository() throws IOException {
        deleteRecursively(root);
    }

    /**
     * Test of getFile method, of class LocalFileRepository.
     */
    @Test
    public void testGetFile() {
        File repoFile = root.toFile();
        LocalFileRepository repo = new LocalFileRepository(repoFile, repoFile, repoFile);
        testGetFileSecurely(repo::getFile);
    }

    /**
     * Test of getFile method, of class LocalFileRepository.
     */
    @Test
    public void testGetConfigurationFile() {
        File repoFile = root.toFile();
        LocalFileRepository repo = new LocalFileRepository(repoFile, repoFile, repoFile);
        testGetFileSecurely(repo::getConfigurationFile);
    }

    private void testGetFileSecurely(Function<String, File> getter) {
        Path result = getter.apply("../localfilerepository/test").toPath();
        Assert.assertEquals(root.resolve("test"), result);
        try {
            getter.apply("../test");
            Assert.fail("We shouldn't be able to go out");
        } catch (IllegalArgumentException ioex) {
            Assert.assertTrue(ioex.getMessage().contains("WFLYDR0025"));
        }
        try {
            getter.apply("test/../../test");
            Assert.fail("We shouldn't be able to go out");
        } catch (IllegalArgumentException ioex) {
            Assert.assertTrue(ioex.getMessage().contains("WFLYDR0025"));
        }
        result = getter.apply( "/index.html").toPath();
        Assert.assertEquals(root.resolve("index.html"), result);
        result = getter.apply("///index.html").toPath();
        Assert.assertEquals(root.resolve("index.html"), result);
    }
}
