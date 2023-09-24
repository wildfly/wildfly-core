/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.persistence;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FileUtils;
import org.jboss.as.controller.persistence.ConfigurationPersister.SnapshotInfo;
import org.jboss.as.server.controller.git.GitRepository;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */

public class GitPersistenceResourceTestCase extends AbstractGitPersistenceResourceTestCase {

    @Before
    public void createDirectoriesAndFiles() throws Exception {
        root = Files.createTempDirectory("local").resolve("standalone");
        Files.createDirectories(root);
        File baseDir = root.toAbsolutePath().toFile();
        File gitDir = new File(baseDir, Constants.DOT_GIT);
        if (!gitDir.exists()) {
            try (Git git = Git.init().setDirectory(baseDir).setGitDir(gitDir).setInitialBranch(Constants.MASTER).call()) {
                StoredConfig config = git.getRepository().getConfig();
                config.setBoolean(ConfigConstants.CONFIG_COMMIT_SECTION, null, ConfigConstants.CONFIG_KEY_GPGSIGN, false);
                config.setBoolean(ConfigConstants.CONFIG_TAG_SECTION, null, ConfigConstants.CONFIG_KEY_GPGSIGN, false);
                config.save();
                git.commit().setMessage("Repository initialized").call();
            }
        }
        repository = new FileRepositoryBuilder().setWorkTree(baseDir).setGitDir(gitDir).setup().build();
    }

    @After
    public void deleteDirectoriesAndFiles() throws Exception {
        if (repository != null) {
            repository.close();
        }
        FileUtils.delete(root.getParent().toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
    }

    @Test
    public void testDefaultPersistentConfigurationFile() throws Exception {
        List<String> commits = listCommits(repository);
        Assert.assertEquals(1, commits.size());
        Assert.assertEquals("Repository initialized", commits.get(0));
        Assert.assertTrue(Files.exists(root));
        Path standard = createFile(root, "standard.xml", "std");
        ConfigurationFile configurationFile = new ConfigurationFile(root.toFile(), "standard.xml", null, ConfigurationFile.InteractionPolicy.STANDARD, true, null);
        Assert.assertEquals(standard.toAbsolutePath().toString(), configurationFile.getBootFile().getAbsolutePath());
        TestConfigurationFilePersister persister = new TestConfigurationFilePersister(configurationFile, new GitRepository(repository));
        persister.successfulBoot();
        checkFiles("standard", "std");
        commits = listCommits(repository);
        Assert.assertEquals(1, commits.size());
        Assert.assertEquals("Repository initialized", commits.get(0));
        store(persister, "One");
        commits = listCommits(repository);
        Assert.assertEquals(2, commits.size());
        Assert.assertEquals("Storing configuration", commits.get(0));
        Assert.assertEquals("Repository initialized", commits.get(1));
        checkFiles("standard", "One");
        SnapshotInfo infos = persister.listSnapshots();
        Assert.assertTrue(infos.names().isEmpty());
        store(persister, "Two");
        commits = listCommits(repository);
        Assert.assertEquals(3, commits.size());
        Assert.assertEquals("Storing configuration", commits.get(0));
        Assert.assertEquals("Storing configuration", commits.get(1));
        Assert.assertEquals("Repository initialized", commits.get(2));
        List<String> tags = listTags(repository);
        Assert.assertEquals(0, tags.size());
        persister.snapshot("test_snapshot", "1st snapshot");
        tags = listTags(repository);
        Assert.assertEquals(1, tags.size());
        Assert.assertEquals("test_snapshot : 1st snapshot", tags.get(0));
    }

}
