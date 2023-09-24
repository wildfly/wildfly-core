/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.management.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildFlyRunner;

@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class GitRepositoryTestCase extends AbstractGitRepositoryTestCase {

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
    }

    /**
     * Start server (no parameter)
     */
    @Test
    public void startDefaultTest() throws Exception {
        // start default (no parameters, no .git in jboss.server.base.dir)
        container.start();
        Assert.assertTrue(Files.notExists(getDotGitDir()));
        Assert.assertTrue(Files.notExists(getDotGitIgnore()));
        container.stop();

        // start with local repository (--git-repo=local) to initialize the local repo
        container.startGitBackedConfiguration("local", null, null);
        Assert.assertTrue("Directory not found " + getDotGitDir(), Files.exists(getDotGitDir()));
        Assert.assertTrue("File not found " + getDotGitIgnore(), Files.exists(getDotGitIgnore()));
        container.stop();

        // start with default (no parameters), but with initialized empty repository in jboss.server.base.dir
        container.start();
        repository = createRepository();
        addSystemProperty();
        int expectedNumberOfCommits = 2;
        List<String> commits = listCommits(repository);
        Assert.assertEquals(expectedNumberOfCommits, commits.size());
        Assert.assertEquals("Storing configuration", commits.get(0));
        Assert.assertEquals("Repository initialized", commits.get(1));
        container.stop();

        // start with default (no parameters), local git repository already contains configuration from last run
        container.start();
        try {
            addSystemProperty();
            Assert.fail("Operation should have failed");
        } catch (UnsuccessfulOperationException uoe) {
            Assert.assertTrue(uoe.getMessage().contains("WFLYCTL0212"));
        }
        removeSystemProperty();
        expectedNumberOfCommits++;
        commits = listCommits(repository);
        Assert.assertEquals(expectedNumberOfCommits, commits.size());
        Assert.assertEquals("Storing configuration", commits.get(0));
    }

    /**
     * Start server with parameter --git-repo=local
     */
    @Test
    public void startGitRepoLocalTest() throws Exception {
        // start with local repository and branch (--git-repo=local --git-branch=my_branch)
        container.startGitBackedConfiguration("local", "my_branch", null);
        Assert.assertTrue("Directory not found " + getDotGitDir(), Files.exists(getDotGitDir()));
        Assert.assertTrue("File not found " + getDotGitIgnore(), Files.exists(getDotGitIgnore()));
        repository = createRepository();
        addSystemProperty();
        int expectedNumberOfCommits = 2;
        List<String> commits = listCommits(repository, "my_branch");
        Assert.assertEquals(expectedNumberOfCommits, commits.size());
        Assert.assertEquals("Storing configuration", commits.get(0));
        Assert.assertEquals("Repository initialized", commits.get(1));
        container.stop();

        // create tag in local repo and change Primary for next test
        container.startGitBackedConfiguration("local", null, null);
        takeSnapshot("my_tag", null);
        Assert.assertEquals(1, listTags(repository).size());
        removeSystemProperty();
        container.stop();

        // start with local repository (where repository already exists) and branch where branch is actually tag
        // (--git-repo=local --git-branch=my_tag)
        container.startGitBackedConfiguration("local", "my_tag", null);
        try {
            addSystemProperty();
            Assert.fail("Operation should have failed");
        } catch (UnsuccessfulOperationException uoe) {
            Assert.assertTrue(uoe.getMessage().contains("WFLYCTL0212"));
        }
    }

    /**
     * Start server with parameter --git-repo=local and make changes in config and deployment
     */
    @Test
    public void historyAndManagementOperationsTest() throws Exception {
        container.startGitBackedConfiguration("local", null, null);
        Assert.assertTrue("Directory not found " + getDotGitDir(), Files.exists(getDotGitDir()));
        Assert.assertTrue("File not found " + getDotGitIgnore(), Files.exists(getDotGitIgnore()));

        repository = createRepository();
        int expectedNumberOfCommits = 1;

        // start => initial commit
        List<String> commits = listCommits(repository);
        Assert.assertEquals(expectedNumberOfCommits, commits.size());
        Assert.assertEquals("Repository initialized", commits.get(0));
        List<String> paths = listFilesInCommit(repository);

        // change configuration => commit
        addSystemProperty();
        expectedNumberOfCommits++;
        commits = listCommits(repository);
        Assert.assertEquals(expectedNumberOfCommits, commits.size());
        Assert.assertEquals("Storing configuration", commits.get(0));
        paths = listFilesInCommit(repository);
        Assert.assertEquals(1, paths.size());
        Assert.assertEquals("configuration/standalone.xml", paths.get(0));

        // deploy deployment => commit
        deployEmptyDeployment();
        listUntracked(repository).forEach(l-> System.out.println(l));
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

        // :publish-configuration(location=empty) => push to empty)
        publish("empty");
        tags = listTags(emptyRemoteRepository);
        Assert.assertEquals(4, tags.size());
        Assert.assertEquals("fooo", tags.get(3));
        commits = listCommits(emptyRemoteRepository);
        Assert.assertEquals(expectedNumberOfCommits, commits.size());
        Assert.assertEquals("bar", commits.get(0));
    }

     protected List<String> listUntracked(Repository repository) throws IOException, GitAPIException {
         List<String> result = new ArrayList<>();
         result.add("Untracked:");
        try (Git git = new Git(repository)) {
            Status status = git.status().call();
            result.addAll(status.getUntrackedFolders());
            result.addAll(status.getUntracked());
            return result;
        }
    }
}
