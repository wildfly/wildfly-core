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
package org.jboss.as.test.manualmode.management.persistence;


import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FileUtils;
import org.jboss.as.repository.PathUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildFlyRunner;

@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class RemoteGitRepositoryTestCase extends AbstractGitRepositoryTestCase {

    private Path remoteRoot;
    private Repository remoteRepository;

    @Before
    public void prepareTest() throws Exception {
        remoteRoot = Files.createTempDirectory("RemoteGitRepositoryTestCase").resolve("remote");
        Path repoConfigDir = remoteRoot.resolve("configuration");
        Files.createDirectories(repoConfigDir);
        File baseDir = remoteRoot.toAbsolutePath().toFile();
        PathUtil.copyRecursively(getJbossServerBaseDir().resolve("configuration"), repoConfigDir, true);
        Path properties = repoConfigDir.resolve("logging.properties");
        if(Files.exists(properties)) {
            Files.delete(properties);
        }
        File gitDir = new File(baseDir, Constants.DOT_GIT);
        if (!gitDir.exists()) {
            try (Git git = Git.init().setDirectory(baseDir).setInitialBranch(Constants.MASTER).call()) {
                StoredConfig config = git.getRepository().getConfig();
                config.setBoolean(ConfigConstants.CONFIG_COMMIT_SECTION, null, ConfigConstants.CONFIG_KEY_GPGSIGN, false);
                config.save();
                git.add().addFilepattern("configuration").call();
                git.commit().setMessage("Repository initialized").call();
            }
        }
        remoteRepository = new FileRepositoryBuilder().setWorkTree(baseDir).setGitDir(gitDir).setup().build();
    }

    @After
    public void after() throws Exception {
        if (container.isStarted()) {
            try {
                removeDeployment();
            } catch (Exception sde) {
                // ignore error undeploying, might not exist
            }
            removeSystemProperty();
            container.stop();
        }
        closeRepository();
        closeEmptyRemoteRepository();
        closeRemoteRepository();
    }

    private void closeRemoteRepository() throws Exception{
        if (remoteRepository != null) {
            remoteRepository.close();
        }
        FileUtils.delete(remoteRoot.getParent().toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
    }

    /**
     * Start server with parameter --git-repo=file:///my_repo/test/.git
     */
    @Test
    public void startGitRepoRemoteTest() throws Exception {
        // start with remote repository containing configuration (--git-repo=file:///my_repo/test/.git)
        container.startGitBackedConfiguration("file://" + remoteRoot.resolve(Constants.DOT_GIT).toAbsolutePath().toString(), Constants.MASTER, null);
        Assert.assertTrue("Directory not found " + getDotGitDir(), Files.exists(getDotGitDir()));
        Assert.assertTrue("File not found " + getDotGitIgnore(), Files.exists(getDotGitIgnore()));
        List<String> commits = listCommits(remoteRepository);
        Assert.assertEquals(1, commits.size());
        addSystemProperty();
        publish(null);
        commits = listCommits(remoteRepository);
        Assert.assertEquals(3, commits.size());

        // create branch in remote repo and change Primary for next test
        try(Git git = new Git(remoteRepository)) {
            git.checkout().setName("my_branch").setCreateBranch(true).call();
        }
        removeSystemProperty();
        publish(null);
        container.stop();
        closeRepository();

        // start with remote repository and branch containing configuration
        // (--git-repo=file:///my_repo/test/.git --git-branch=my_branch)
        container.startGitBackedConfiguration("file://" + remoteRoot.resolve(Constants.DOT_GIT).toAbsolutePath().toString(), "my_branch", null);
        Assert.assertTrue("Directory not found " + getDotGitDir(), Files.exists(getDotGitDir()));
        Assert.assertTrue("File not found " + getDotGitIgnore(), Files.exists(getDotGitIgnore()));
        try {
            addSystemProperty();
            Assert.fail("Operation should have failed");
        } catch (UnsuccessfulOperationException uoe) {
            Assert.assertTrue(uoe.getMessage().contains("WFLYCTL0212"));
        }
    }

    /**
     * Start server with parameter --git-repo=file:///my_repo/test/.git
     */
    @Test
    public void historyAndManagementOperationsTest() throws Exception {
        container.startGitBackedConfiguration("file://" + remoteRoot.resolve(Constants.DOT_GIT).toAbsolutePath().toString(), Constants.MASTER, null);
        Assert.assertTrue("Directory not found " + getDotGitDir(), Files.exists(getDotGitDir()));
        Assert.assertTrue("File not found " + getDotGitIgnore(), Files.exists(getDotGitIgnore()));
        repository = createRepository();

        int expectedNumberOfCommits = 2;

        // start => initial commit
        List<String> commits = listCommits(repository);
        Assert.assertEquals(expectedNumberOfCommits, commits.size());
        Assert.assertEquals("Adding .gitignore", commits.get(0));
        Assert.assertEquals("Repository initialized", commits.get(1));
        List<String> paths = listFilesInCommit(repository);
        Assert.assertEquals(Arrays.toString(paths.toArray()), 1, paths.size());

        // change configuration => commit
        addSystemProperty();
        expectedNumberOfCommits++;
        commits = listCommits(repository);
        Assert.assertEquals(expectedNumberOfCommits, commits.size());
        Assert.assertEquals("Storing configuration", commits.get(0));
        paths = listFilesInCommit(repository);
        Assert.assertEquals(Arrays.toString(paths.toArray()), 1, paths.size());
        Assert.assertEquals("configuration/standalone.xml", paths.get(0));

        // deploy deployment => commit
        deployEmptyDeployment();
        expectedNumberOfCommits++;
        commits = listCommits(repository);
        Assert.assertEquals(expectedNumberOfCommits, commits.size());
        Assert.assertEquals("Storing configuration", commits.get(0));
        paths = listFilesInCommit(repository);
        Assert.assertEquals(Arrays.toString(paths.toArray()), 2, paths.size());
        Assert.assertEquals("configuration/standalone.xml", paths.get(0));
        String contentPath = paths.get(1);
        Assert.assertTrue(contentPath.startsWith("data/content/") && contentPath.endsWith("/content"));

        // undeploy deployment (/deployment=name:undeploy) => commited
        undeployDeployment();
        expectedNumberOfCommits++;
        commits = listCommits(repository);
        Assert.assertEquals(expectedNumberOfCommits, commits.size());
        Assert.assertEquals("Storing configuration", commits.get(0));
        paths = listFilesInCommit(repository);
        Assert.assertEquals(Arrays.toString(paths.toArray()), 1, paths.size());
        Assert.assertEquals("configuration/standalone.xml", paths.get(0));

        // exploded deployment => commited
        explodeDeployment();
        expectedNumberOfCommits++;
        commits = listCommits(repository);
        Assert.assertEquals(Arrays.toString(commits.toArray()), expectedNumberOfCommits, commits.size());
        Assert.assertEquals("Storing configuration", commits.get(0));
        paths = listFilesInCommit(repository);
        Assert.assertEquals(Arrays.toString(paths.toArray()), 3, paths.size());
        Assert.assertEquals("-" + contentPath, paths.get(0));
        Assert.assertEquals("configuration/standalone.xml", paths.get(1));
        String contentFile = paths.get(2);
        Assert.assertNotEquals(contentPath, contentFile);
        Assert.assertTrue(contentFile.startsWith("data/content/") && contentFile.endsWith("/content/file"));

        // exploded deployment - add content => commited
        addContentToDeployment();
        expectedNumberOfCommits++;
        commits = listCommits(repository);
        Assert.assertEquals(expectedNumberOfCommits, commits.size());
        Assert.assertEquals("Storing configuration", commits.get(0));
        paths = listFilesInCommit(repository);
        Assert.assertEquals(Arrays.toString(paths.toArray()), 4, paths.size());
        Assert.assertEquals("configuration/standalone.xml", paths.get(1));
        Assert.assertEquals("-" + contentFile, paths.get(0));
        contentFile = paths.get(2);
        String contentProperties = paths.get(3);
        Assert.assertNotEquals(contentPath, contentFile);
        Assert.assertNotEquals(contentPath, contentProperties);
        Assert.assertTrue(contentFile.startsWith("data/content/") && contentFile.endsWith("/content/file"));
        Assert.assertTrue(contentProperties.startsWith("data/content/") && contentProperties.endsWith("/content/test.properties"));

        // exploded deployment - remove content => commited
        removeContentFromDeployment();
        expectedNumberOfCommits++;
        commits = listCommits(repository);
        Assert.assertEquals(expectedNumberOfCommits, commits.size());
        Assert.assertEquals("Storing configuration", commits.get(0));
        paths = listFilesInCommit(repository);
        Assert.assertEquals(Arrays.toString(paths.toArray()), 4, paths.size());
        Assert.assertEquals("-" + contentFile, paths.get(0));
        Assert.assertEquals("-" + contentProperties, paths.get(1));
        Assert.assertEquals("configuration/standalone.xml", paths.get(2));
        contentFile = paths.get(3);
        Assert.assertTrue(contentFile.startsWith("data/content/") && contentFile.endsWith("/content/file"));

        // :clean-obsolete-content
        // remove deployment => commit
        removeDeployment();
        expectedNumberOfCommits++;
        commits = listCommits(repository);
        Assert.assertEquals(expectedNumberOfCommits, commits.size());
        Assert.assertEquals("Storing configuration", commits.get(0));
        paths = listFilesInCommit(repository);
        Assert.assertEquals(Arrays.toString(paths.toArray()), 2, paths.size());
        Assert.assertEquals( "-" + contentFile, paths.get(0));
        Assert.assertEquals("configuration/standalone.xml", paths.get(1));
        // :clean-obsolete-content
        // deployment-overlay

        // there are no tags
        List<String> tags = listTags(repository);
        Assert.assertEquals(0, tags.size());

        // :take-snapshot => tag = timestamp
        LocalDateTime snapshot = LocalDateTime.now();
        takeSnapshot(null, null);
        tags = listTags(repository);
        Assert.assertEquals(1, tags.size());
        verifyDefaultSnapshotString(snapshot, tags.get(0));
        // this snapshot is not expected to have commit, as there is no uncommited remove of content data
        commits = listCommits(repository);
        Assert.assertEquals(expectedNumberOfCommits, commits.size());
        Assert.assertEquals("Storing configuration", commits.get(0));

        // :take-snapshot(name=foo) => success, tag=foo
        takeSnapshot("foo", null);
        tags = listTags(repository);
        Assert.assertEquals(2, tags.size());
        // there should be two tags, from this and previous snapshot
        Assert.assertEquals("foo", tags.get(1));
        // this should be the same commit as with previous snapshot
        commits = listCommits(repository);
        Assert.assertEquals(expectedNumberOfCommits, commits.size());

        // :take-snapshot(name=foo) => fail, tag already exists
        try {
            takeSnapshot("foo", null);
            Assert.fail("Operation should have failed");
        } catch (UnsuccessfulOperationException uoe) {
            // good
            Assert.assertTrue(uoe.getMessage().contains("WFLYCTL0455"));
        }

        // :take-snapshot(description=bar) => tag = timestamp, commit msg=bar
        snapshot = LocalDateTime.now();
        takeSnapshot(null, "bar");
        expectedNumberOfCommits++;
        tags = listTags(repository);
        Assert.assertEquals(3, tags.size());
        // tags are ordered alphabetically, so we want second with default name
        verifyDefaultSnapshotString(snapshot, tags.get(1));
        commits = listCommits(repository);
        Assert.assertEquals(expectedNumberOfCommits, commits.size());
        Assert.assertEquals("bar", commits.get(0));

        // :take-snapshot(name=fooo, description=barbar) => success, tag=fooo, commit msg=bar
        takeSnapshot("fooo", "bar");
        expectedNumberOfCommits++;
        tags = listTags(repository);
        Assert.assertEquals(4, tags.size());
        // fooo is alphabetically last
        Assert.assertEquals("fooo", tags.get(3));
        commits = listCommits(repository);
        Assert.assertEquals(expectedNumberOfCommits, commits.size());
        Assert.assertEquals("bar", commits.get(0));

        // :take-snapshot(name=fooo, description=bar) => fail
        try {
            takeSnapshot("fooo", "bar");
            Assert.fail("Operation should have failed");
        } catch (UnsuccessfulOperationException uoe) {
            // good
            Assert.assertTrue(uoe.getMessage().contains("WFLYCTL0455"));
        }


        // :publish-configuration => push to origin
        commits = listCommits(remoteRepository);
        Assert.assertEquals(1, commits.size());
        Assert.assertEquals("Repository initialized", commits.get(0));
        publish(null);
        commits = listCommits(remoteRepository);
        Assert.assertEquals(expectedNumberOfCommits, commits.size());
        Assert.assertEquals("bar", commits.get(0));

        // :publish-configuration(location=empty) => push to empty)
        publish("empty");
        tags = listTags(emptyRemoteRepository);
        Assert.assertEquals(4, tags.size());
        Assert.assertEquals("fooo", tags.get(3));
        commits = listCommits(emptyRemoteRepository);
        Assert.assertEquals(expectedNumberOfCommits, commits.size());
        Assert.assertEquals("bar", commits.get(0));
    }

}
