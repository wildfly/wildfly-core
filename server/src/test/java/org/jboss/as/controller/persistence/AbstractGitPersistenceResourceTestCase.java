/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.persistence;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.server.controller.git.GitConfigurationPersistenceResource;
import org.jboss.as.server.controller.git.GitConfigurationPersister;
import org.jboss.as.server.controller.git.GitRepository;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class AbstractGitPersistenceResourceTestCase {

    protected Path root;
    protected Repository repository;

    protected void checkFiles(String mainFileName, String content) throws Exception {
        assertFileContents(root.resolve(mainFileName + ".xml"), content);
    }

    protected Path createFile(Path dir, String name, String contents) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve(name);
        if (contents != null) {
            return Files.write(dir.resolve(name), contents.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    protected void delete(File file) {
        if (file.isDirectory()) {
            for (String name : file.list()) {
                delete(new File(file, name));
            }
        }
        if (!file.delete() && file.exists()) {
            Assert.fail("Could not delete " + file);
        }
    }

    protected void assertFileContents(Path file, String expectedContents) throws Exception {
        Assert.assertTrue(file + " does not exist", Files.exists(file));
        StringBuilder sb = new StringBuilder();
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String s = in.readLine();
            while (s != null) {
                sb.append(s);
                s = in.readLine();
            }
        }
        Assert.assertEquals(expectedContents, sb.toString());
    }

    protected void store(TestConfigurationFilePersister persister, String s) throws Exception {
        persister.store(new ModelNode(s), Collections.<PathAddress>emptySet()).commit();
    }

    protected List<String> listCommits(Repository repository) throws IOException, GitAPIException {
        try (Git git = new Git(repository)) {
            return listCommits(git, Constants.MASTER);
        }
    }

    private List<String> listCommits(Git git, String branchName) throws IOException, GitAPIException {
        List<String> commits = new ArrayList<>();
        for(RevCommit commit : git.log().add(git.getRepository().resolve(branchName)).call()) {
            commits.add(commit.getFullMessage());
        }
        return commits;
    }

    protected List<String> listTags(Repository repository) throws IOException, GitAPIException {
        try (Git git = new Git(repository)) {
            return listTags(git);
        }
    }

    private List<String> listTags(Git git) throws IOException, GitAPIException {
        List<String> tags = new ArrayList<>();
        for (Ref tag : git.tagList().call()) {
            RevWalk revWalk = new RevWalk(git.getRepository());
            revWalk.sort(RevSort.COMMIT_TIME_DESC, true);
            try {
                RevTag annotatedTag = revWalk.parseTag(tag.getObjectId());
                tags.add(annotatedTag.getTagName() + " : " + annotatedTag.getFullMessage());
            } catch (IncorrectObjectTypeException ex) {
                tags.add(tag.getName().substring("refs/tags/".length()));
            }
        }
        return tags;
    }

    protected class TestConfigurationFilePersister extends GitConfigurationPersister {

    private final File configurationFile;
    private final GitRepository repository;

    public TestConfigurationFilePersister(ConfigurationFile file, GitRepository repository) {
        super(repository, file, null, null, null);
        this.configurationFile = file.getBootFile();
        this.repository = repository;
    }

    ConfigurationPersister.PersistenceResource create(ModelNode model) throws ConfigurationPersistenceException {
        return new GitConfigurationPersistenceResource(model, configurationFile, repository, this);
    }

    @Override
    public List<ModelNode> load() throws ConfigurationPersistenceException {
        return Collections.emptyList();
    }

    @Override
    public void marshallAsXml(ModelNode model, OutputStream output) throws ConfigurationPersistenceException {
        try {
            output.write(model.asString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ConfigurationPersistenceException(e);
        }
    }
}

}
