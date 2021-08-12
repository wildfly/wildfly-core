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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FileUtils;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Before;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class AbstractGitRepositoryTestCase {

    private static final Path JBOSS_SERVER_BASE_DIR = new File(System.getProperty("jboss.home", System.getenv("JBOSS_HOME"))).toPath().resolve("standalone");
    private static final String TEST_DEPLOYMENT_RUNTIME_NAME = "test.jar";
    private static final ModelNode TEST_SYSTEM_PROPERTY_ADDRESS = new ModelNode().add("system-property", "git-history-property");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS");
    protected Repository emptyRemoteRepository;
    protected Repository repository;
    private Path emptyRemoteRoot;

    static {
        TEST_SYSTEM_PROPERTY_ADDRESS.protect();
    }

    @Inject
    protected ServerController container;

    @Before
    public void prepareEmptyRemoteRepository() throws Exception {
        emptyRemoteRoot = Files.createTempDirectory("AbstractGitRepositoryTestCase").resolve("empty-remote");
        Files.createDirectories(emptyRemoteRoot);
        File gitDir = new File(emptyRemoteRoot.toFile(), Constants.DOT_GIT);
        if (!gitDir.exists()) {
            try (Git git = Git.init().setDirectory(emptyRemoteRoot.toFile()).setInitialBranch(Constants.MASTER).call()) {
                StoredConfig config = git.getRepository().getConfig();
                config.setBoolean(ConfigConstants.CONFIG_COMMIT_SECTION, null, ConfigConstants.CONFIG_KEY_GPGSIGN, false);
                config.save();
            }
        }
        Assert.assertTrue(gitDir.exists());
        emptyRemoteRepository = new FileRepositoryBuilder().setWorkTree(emptyRemoteRoot.toFile()).setGitDir(gitDir).setup().build();
    }

    protected void closeEmptyRemoteRepository() throws Exception {
        if (emptyRemoteRepository != null) {
            emptyRemoteRepository.close();
        }
        FileUtils.delete(emptyRemoteRoot.getParent().toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
    }

    protected Repository createRepository() throws IOException {
        Repository repo = new FileRepositoryBuilder().setWorkTree(getJbossServerBaseDir().toFile())
                .setGitDir(getDotGitDir().toFile())
                .setup().build();
        StoredConfig config = repo.getConfig();
        config.setString("remote", "empty", "url", "file://" + emptyRemoteRoot.resolve(Constants.DOT_GIT).toAbsolutePath().toString());
        config.save();
        return repo;
    }

    protected void closeRepository() throws Exception{
        if (repository != null) {
            repository.close();
        }
        if (Files.exists(getDotGitDir())) {
            FileUtils.delete(getDotGitDir().toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
        }
        if(Files.exists(getDotGitIgnore())) {
            FileUtils.delete(getDotGitIgnore().toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
        }
    }

    protected List<String> listCommits(Repository repository, String branchName) throws IOException, GitAPIException {
        try (Git git = new Git(repository)) {
            return listCommits(git, branchName);
        }
    }

    protected List<String> listCommits(Repository repository) throws IOException, GitAPIException {
        try (Git git = new Git(repository)) {
            return listCommits(git, Constants.MASTER);
        }
    }

    private List<String> listCommits(Git git, String branchName) throws IOException, GitAPIException {
        List<String> commits = new ArrayList<>();
        for (RevCommit commit : git.log().add(git.getRepository().resolve(branchName)).call()) {
            commits.add(commit.getFullMessage());
        }
        return commits;
    }

    protected List<String> listTags(Repository repository) throws IOException, GitAPIException {
        List<String> tags = new ArrayList<>();
        try (Git git = new Git(repository)) {
            for (Ref tag : git.tagList().call()) {
                RevWalk revWalk = new RevWalk(repository);
                try {
                    RevTag annotatedTag = revWalk.parseTag(tag.getObjectId());
                    tags.add(annotatedTag.getTagName());
                } catch (IncorrectObjectTypeException ex) {
                    tags.add(tag.getName().substring("refs/tags/".length()));
                }
            }
        }
        Collections.sort(tags);
        return tags;
    }

    protected List<String> listFilesInCommit(Repository repository) throws IOException, GitAPIException {
        List<String> result = new ArrayList<>();
        try (Git git = new Git(repository)) {
            RevCommit commit = git.log().add(git.getRepository().resolve(Constants.MASTER)).call().iterator().next();
            if (commit.getParentCount() > 0) {
                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    treeWalk.addTree(commit.getParent(0).getTree());
                    treeWalk.addTree(commit.getTree());
                    treeWalk.setRecursive(true);
                    List<DiffEntry> diff = DiffEntry.scan(treeWalk, false, null);
                    for (DiffEntry diffEntry : diff) {
                        if(diffEntry.getChangeType() == DiffEntry.ChangeType.DELETE) {
                            result.add("-" + diffEntry.getOldPath());
                        } else {
                            result.add(diffEntry.getNewPath());
                        }
                    }
                }
            }
        }
        Collections.sort(result);
        return result;
    }

    protected void addSystemProperty() throws UnsuccessfulOperationException {
        ModelNode op = Operations.createAddOperation(TEST_SYSTEM_PROPERTY_ADDRESS);
        op.get("value").set("git-history-property-value");

        ManagementClient client = container.getClient();
        client.executeForResult(op);
    }

    protected void removeSystemProperty() throws IOException {
        ModelNode op = Operations.createRemoveOperation(TEST_SYSTEM_PROPERTY_ADDRESS);
        container.getClient().getControllerClient().execute(op);
    }

    protected void deployEmptyDeployment() throws ServerDeploymentHelper.ServerDeploymentException {
        container.deploy(ShrinkWrap.create(JavaArchive.class).add(EmptyAsset.INSTANCE, "file"), TEST_DEPLOYMENT_RUNTIME_NAME);
    }

    protected void removeDeployment() throws ServerDeploymentHelper.ServerDeploymentException {
        container.undeploy(TEST_DEPLOYMENT_RUNTIME_NAME);
    }

    protected void undeployDeployment() throws UnsuccessfulOperationException {
        ModelNode op = Operations.createOperation("undeploy",
                new ModelNode().add("deployment", TEST_DEPLOYMENT_RUNTIME_NAME));
        ManagementClient client = container.getClient();
        client.executeForResult(op);
    }

    protected void explodeDeployment() throws UnsuccessfulOperationException {
        ModelNode op = Operations.createOperation("explode",
                new ModelNode().add("deployment", TEST_DEPLOYMENT_RUNTIME_NAME));

        ManagementClient client = container.getClient();
        client.executeForResult(op);
    }

    protected void addContentToDeployment() throws UnsuccessfulOperationException {
        ModelNode content = new ModelNode();
        content.get("bytes").set(RemoteGitRepositoryTestCase.class.getName().getBytes());
        content.get("target-path").set("test.properties");

        ModelNode op = Operations.createOperation("add-content",
                new ModelNode().add("deployment", TEST_DEPLOYMENT_RUNTIME_NAME));
        op.get("content").setEmptyList();
        op.get("content").add(content);

        ManagementClient client = container.getClient();
        client.executeForResult(op);
    }

    protected void removeContentFromDeployment() throws UnsuccessfulOperationException {
        ModelNode op = Operations.createOperation("remove-content",
                new ModelNode().add("deployment", TEST_DEPLOYMENT_RUNTIME_NAME));
        op.get("paths").setEmptyList();
        op.get("paths").add("test.properties");
        ManagementClient client = container.getClient();
        client.executeForResult(op);
    }

    protected void takeSnapshot(String name, String description) throws UnsuccessfulOperationException {
        ModelNode op = Operations.createOperation("take-snapshot");
        if (name != null) {
            op.get("name").set(name);
        }
        if (description != null) {
            op.get("comment").set(description);
        }

        container.getClient().executeForResult(op);
    }

    protected void publish(String location) throws UnsuccessfulOperationException {
        ModelNode op = Operations.createOperation("publish-configuration");
        if (location != null) {
            op.get("location").set(location);
        }
        container.getClient().executeForResult(op);
    }

    protected void verifyDefaultSnapshotString(LocalDateTime snapshot, String string) {
        LocalDateTime comparableSnapshot = snapshot.withNano(0);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        assert comparableSnapshot.isBefore(now) || comparableSnapshot.isEqual(now);
        LocalDateTime timestamp = LocalDateTime.parse(string.substring("Snapshot-".length()), FORMATTER).withNano(0);
        boolean valid = (comparableSnapshot.isBefore(timestamp) || comparableSnapshot.isEqual(timestamp)) && (timestamp.isBefore(now) || timestamp.isEqual(now));
        Assert.assertTrue(FORMATTER.format(timestamp) + " isn't before " + FORMATTER.format(now) + " or after " + FORMATTER.format(comparableSnapshot), valid);
    }

    protected Path getDotGitDir() {
        return JBOSS_SERVER_BASE_DIR.resolve(".git");
    }

    protected Path getDotGitIgnore() {
        return JBOSS_SERVER_BASE_DIR.resolve(".gitignore");
    }

    protected static Path getJbossServerBaseDir() {
        return JBOSS_SERVER_BASE_DIR;
    }

}
