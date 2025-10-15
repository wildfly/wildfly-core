/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.management.persistence;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.http.SimpleHttpServer;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
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

/**
 * @author <a href="mailto:aabdelsa@redhat.com">Ashley Abdel-Sayed</a>
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class RemoteHttpGitRepositoryTestCase extends AbstractGitRepositoryTestCase {

    private Path remoteRoot;

    private Repository remoteRepository;
    private SimpleHttpServer httpServer;
    private String AUTH_FILE = Paths.get("src","test", "resources", "git-persistence", "wildfly-config.xml").toUri().toString();

    @Before
    public void prepareTest() throws Exception {
        remoteRoot = new File("target", "remote").toPath();
        Path repoConfigDir = remoteRoot.resolve("configuration");
        Files.createDirectories(repoConfigDir);
        File baseDir = remoteRoot.toAbsolutePath().toFile();
        PathUtil.copyRecursively(getJbossServerBaseDir().resolve("configuration"), repoConfigDir, true);
        Path properties = repoConfigDir.resolve("logging.properties");
        if (Files.exists(properties)) {
            Files.delete(properties);
        }
        Path standaloneHistory = repoConfigDir.resolve("standalone_xml_history");
        if (Files.exists(standaloneHistory)) {
            PathUtil.deleteRecursively(standaloneHistory);
        }
        File gitDir = new File(baseDir, Constants.DOT_GIT);
        if (!gitDir.exists()) {
            try (Git git = Git.init().setDirectory(baseDir).setInitialBranch(Constants.MASTER).call()) {
                git.add().addFilepattern("configuration").call();
                git.commit().setSign(false).setMessage("Repository initialized").call();
            }
        }
        remoteRepository = new FileRepositoryBuilder().setWorkTree(baseDir).setGitDir(gitDir).setup().build();
        httpServer = new SimpleHttpServer(remoteRepository);
        httpServer.start();

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
        httpServer.stop();
    }

    private void closeRemoteRepository() throws Exception {
        if (remoteRepository != null) {
            remoteRepository.close();
            remoteRepository = null;
        }
        FileUtils.delete(remoteRoot.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY | FileUtils.SKIP_MISSING);
    }

    @Test
    public void startGitRepoRemoteHttpAuthTest() throws Exception {
        // start with remote repository containing configuration
        container.startGitBackedConfiguration("http://httptest@127.0.0.1:" + httpServer.getUri().getPort() + "/sbasic/.git",
                Constants.MASTER, AUTH_FILE);
        Assert.assertTrue("Directory not found " + getDotGitDir(), Files.exists(getDotGitDir()));
        Assert.assertTrue("File not found " + getDotGitIgnore(), Files.exists(getDotGitIgnore()));
        List<String> commits = listCommits(remoteRepository);
        Assert.assertEquals(1, commits.size());
        addSystemProperty();
        publish(null);
        commits = listCommits(remoteRepository);
        Assert.assertEquals(3, commits.size());
        // create branch in remote repo and change Primary for next test
        try (Git git = new Git(remoteRepository)) {
            git.checkout().setName("my_branch").setCreateBranch(true).call();
        }
        removeSystemProperty();
        publish(null);
        container.stop();
        closeRepository();

        container.startGitBackedConfiguration("http://httptest@127.0.0.1:" + httpServer.getUri().getPort() + "/sbasic/.git",
                "my_branch", AUTH_FILE);
        Assert.assertTrue("Directory not found " + getDotGitDir(), Files.exists(getDotGitDir()));
        Assert.assertTrue("File not found " + getDotGitIgnore(), Files.exists(getDotGitIgnore()));
        try {
            //my_branch was created before the system property was removed and so attempting to add the system property
            //should fail as it already exists
            addSystemProperty();
            Assert.fail("Operation should have failed");
        } catch (UnsuccessfulOperationException uoe) {
            Assert.assertTrue(uoe.getMessage().contains("WFLYCTL0212"));
        }
    }

}
