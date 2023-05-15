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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jboss.as.controller.persistence.ConfigurationPersister.SnapshotInfo;
import org.jboss.as.server.controller.git.GitRepository;
import org.jboss.as.server.controller.git.GitRepositoryConfiguration;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class RemoteGitPersistenceResourceTestCase extends AbstractGitPersistenceResourceTestCase {

    private Path remoteRoot;
    private Repository remoteRepository;

    @Before
    public void createDirectoriesAndFiles() throws Exception {
        root = Files.createTempDirectory("local").resolve("standalone");
        remoteRoot = Files.createTempDirectory("remote").resolve("standalone");
        Files.createDirectories(remoteRoot);
        File baseDir = remoteRoot.toAbsolutePath().toFile();
        createFile(remoteRoot, "standard.xml", "std");
        File gitDir = new File(baseDir, Constants.DOT_GIT);
        if (!gitDir.exists()) {
            try (Git git = Git.init().setDirectory(baseDir).setInitialBranch(Constants.MASTER).call()) {
                StoredConfig config = git.getRepository().getConfig();
                config.setBoolean(ConfigConstants.CONFIG_COMMIT_SECTION, null, ConfigConstants.CONFIG_KEY_GPGSIGN, false);
                config.setBoolean(ConfigConstants.CONFIG_TAG_SECTION, null, ConfigConstants.CONFIG_KEY_GPGSIGN, false);
                config.save();
                git.add().addFilepattern("standard.xml").call();
                git.commit().setMessage("Repository initialized").call();
            }
        }
        remoteRepository = new FileRepositoryBuilder().setWorkTree(baseDir).setGitDir(gitDir).setup().build();
        repository = new FileRepositoryBuilder().setWorkTree(root.toAbsolutePath().toFile()).setGitDir(new File(root.toAbsolutePath().toFile(), Constants.DOT_GIT)).setup().build();
    }

    @After
    public void deleteDirectoriesAndFiles() throws Exception {
        if (remoteRepository != null) {
            remoteRepository.close();
        }
        if (repository != null) {
            repository.close();
        }
        delete(remoteRoot.getParent().toFile());
        delete(root.getParent().toFile());
    }

    @Test
    public void testDefaultPersistentConfigurationFile() throws Exception {
        Path standard = createFile(root, "standard.xml", "std");
        ConfigurationFile configurationFile = new ConfigurationFile(root.toFile(), "standard.xml", null, ConfigurationFile.InteractionPolicy.STANDARD, true, null);
        Assert.assertEquals(standard.toAbsolutePath().toString(), configurationFile.getBootFile().getAbsolutePath());
        try (GitRepository gitRepository = new GitRepository(GitRepositoryConfiguration.Builder.getInstance()
                .setBasePath(root)
                .setRepository(remoteRoot.resolve(Constants.DOT_GIT).toAbsolutePath().toString())
                .build())) {
            List<String> commits = listCommits(repository);
            Assert.assertEquals(1, repository.getRemoteNames().size());
            Assert.assertTrue(repository.getRemoteNames().contains("origin"));
            StoredConfig config = repository.getConfig();
            Assert.assertEquals(remoteRoot.resolve(Constants.DOT_GIT).toAbsolutePath().toString(), config.getString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin", ConfigConstants.CONFIG_KEY_URL));
            Assert.assertEquals(2, commits.size());
            Assert.assertEquals("Adding .gitignore", commits.get(0));
            Assert.assertEquals("Repository initialized", commits.get(1));
            Assert.assertTrue(Files.exists(root));
            commits = listCommits(remoteRepository);
            Assert.assertEquals(1, commits.size());
            Assert.assertEquals("Repository initialized", commits.get(0));
            TestConfigurationFilePersister persister = new TestConfigurationFilePersister(configurationFile, gitRepository);
            persister.successfulBoot();
            checkFiles("standard", "std");
            commits = listCommits(repository);
            Assert.assertEquals(2, commits.size());
            Assert.assertEquals("Adding .gitignore", commits.get(0));
            Assert.assertEquals("Repository initialized", commits.get(1));
            store(persister, "One");
            commits = listCommits(repository);
            Assert.assertEquals(3, commits.size());
            Assert.assertEquals("Storing configuration", commits.get(0));
            Assert.assertEquals("Adding .gitignore", commits.get(1));
            Assert.assertEquals("Repository initialized", commits.get(2));
            checkFiles("standard", "One");
            SnapshotInfo infos = persister.listSnapshots();
            Assert.assertTrue(infos.names().isEmpty());
            store(persister, "Two");
            commits = listCommits(repository);
            Assert.assertEquals(4, commits.size());
            Assert.assertEquals("Storing configuration", commits.get(0));
            Assert.assertEquals("Storing configuration", commits.get(1));
            Assert.assertEquals("Adding .gitignore", commits.get(2));
            Assert.assertEquals("Repository initialized", commits.get(3));
            List<String> tags = listTags(repository);
            Assert.assertEquals(0, tags.size());
            persister.snapshot("test_snapshot", "1st snapshot");
            commits = listCommits(repository);
            Assert.assertEquals(5, commits.size());
            Assert.assertEquals("1st snapshot", commits.get(0));
            Assert.assertEquals("Storing configuration", commits.get(1));
            Assert.assertEquals("Storing configuration", commits.get(2));
            Assert.assertEquals("Adding .gitignore", commits.get(3));
            Assert.assertEquals("Repository initialized", commits.get(4));
            tags = listTags(repository);
            Assert.assertEquals(1, tags.size());
            Assert.assertEquals("test_snapshot : 1st snapshot", tags.get(0));
            commits = listCommits(remoteRepository);
            Assert.assertEquals(1, commits.size());
            Assert.assertEquals("Repository initialized", commits.get(0));
            persister.publish(null);
            commits = listCommits(remoteRepository);
            Assert.assertEquals(5, commits.size());
            Assert.assertEquals("1st snapshot", commits.get(0));
            Assert.assertEquals("Storing configuration", commits.get(1));
            Assert.assertEquals("Storing configuration", commits.get(2));
            Assert.assertEquals("Adding .gitignore", commits.get(3));
            Assert.assertEquals("Repository initialized", commits.get(4));
            tags = listTags(remoteRepository);
            Assert.assertEquals(1, tags.size());
            Assert.assertEquals("test_snapshot : 1st snapshot", tags.get(0));
        }
    }


}
