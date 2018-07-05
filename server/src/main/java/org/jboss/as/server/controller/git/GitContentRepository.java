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
package org.jboss.as.server.controller.git;

import static org.eclipse.jgit.lib.Constants.HEAD;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.ContentRepositoryImpl;
import org.jboss.as.repository.ExplodedContent;
import org.jboss.as.repository.ExplodedContentException;
import org.jboss.msc.service.ServiceTarget;

/**
 * Content repository implementation that integrates with git for history.
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class GitContentRepository extends ContentRepositoryImpl {

    private final GitRepository gitRepository;

    protected GitContentRepository(GitRepository gitRepository, File repoRoot, File tmpRoot, long obsolescenceTimeout, long lockTimeout) {
        super(repoRoot, tmpRoot, obsolescenceTimeout, lockTimeout);
        this.gitRepository = gitRepository;
    }

    @Override
    public byte[] removeContentFromExploded(byte[] deploymentHash, List<String> paths) throws ExplodedContentException {
        byte[] result = super.removeContentFromExploded(deploymentHash, paths);
        if (!Arrays.equals(deploymentHash, result)) {
            final Path realFile = getDeploymentContentFile(result, true);
            try (Git git = gitRepository.getGit()) {
                git.add().addFilepattern(gitRepository.getPattern(realFile)).call();
            } catch (GitAPIException ex) {
                throw new ExplodedContentException(ex.getMessage(), ex);
            }
        }
        return result;
    }

    @Override
    public byte[] addContentToExploded(byte[] deploymentHash, List<ExplodedContent> addFiles, boolean overwrite) throws ExplodedContentException {
        byte[] result = super.addContentToExploded(deploymentHash, addFiles, overwrite);
        if (!Arrays.equals(deploymentHash, result)) {
            final Path realFile = getDeploymentContentFile(result, true);
            try (Git git = gitRepository.getGit()) {
                git.add().addFilepattern(gitRepository.getPattern(realFile)).call();
            } catch (GitAPIException ex) {
                throw new ExplodedContentException(ex.getMessage(), ex);
            }
        }
        return result;
    }

    @Override
    public byte[] explodeSubContent(byte[] deploymentHash, String relativePath) throws ExplodedContentException {
        byte[] result = super.explodeSubContent(deploymentHash, relativePath);
        if (!Arrays.equals(deploymentHash, result)) {
            final Path realFile = getDeploymentContentFile(result, true);
            try (Git git = gitRepository.getGit()) {
                git.add().addFilepattern(gitRepository.getPattern(realFile)).call();
            } catch (GitAPIException ex) {
                throw new ExplodedContentException(ex.getMessage(), ex);
            }
        }
        return result;
    }

    @Override
    public byte[] explodeContent(byte[] deploymentHash) throws ExplodedContentException {
        byte[] result = super.explodeContent(deploymentHash);
        if (!Arrays.equals(deploymentHash, result)) {
            final Path realFile = getDeploymentContentFile(result, true);
            try (Git git = gitRepository.getGit()) {
                git.add().addFilepattern(gitRepository.getPattern(realFile)).call();
            } catch (GitAPIException ex) {
                throw new ExplodedContentException(ex.getMessage(), ex);
            }
        }
        return result;
    }

    @Override
    public void removeContent(ContentReference reference) {
        final Path realFile = getDeploymentContentFile(reference.getHash());
        super.removeContent(reference);
        if (!Files.exists(realFile)) {
            try (Git git = gitRepository.getGit()) {
                Set<String> deletedFiles = git.status().call().getMissing();
                RmCommand rmCommand = git.rm();
                for (String file : deletedFiles) {
                    rmCommand.addFilepattern(file);
                }
                rmCommand.addFilepattern(gitRepository.getPattern(realFile)).call();
            } catch (GitAPIException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    public byte[] addContent(InputStream stream) throws IOException {
        byte[] result = super.addContent(stream);
        final Path realFile = getDeploymentContentFile(result, true);
        try (Git git = gitRepository.getGit()) {
            git.add().addFilepattern(gitRepository.getPattern(realFile)).call();
        } catch (GitAPIException ex) {
            throw new IOException(ex);
        }
        return result;
    }

    @Override
    public void flush(boolean success) {
        if (success) {
            try (Git git = gitRepository.getGit()) {
                Status status = git.status().call();
                if (!status.isClean()) {
                    String message = git.getRepository().parseCommit(git.getRepository().resolve(HEAD)).getFullMessage();
                    if(! status.getUntracked().isEmpty() || ! status.getUntrackedFolders().isEmpty()) {
                        AddCommand addCommand = git.add();
                        for(String untracked : status.getUntrackedFolders()) {
                            addCommand = addCommand.addFilepattern(untracked);
                        }
                        for(String untracked : status.getUntracked()) {
                            addCommand = addCommand.addFilepattern(untracked);
                        }
                        addCommand.call();
                    }
                    git.commit().setMessage(message).setAmend(true).setAll(true).setNoVerify(true).call();
                }
            } catch (RevisionSyntaxException | IOException | GitAPIException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public static void addService(final ServiceTarget serviceTarget, final GitRepository gitRepository, final File repoRoot, final File tmpRoot) {
        ContentRepository.Factory.addService(serviceTarget, new GitContentRepository(gitRepository, repoRoot, tmpRoot, OBSOLETE_CONTENT_TIMEOUT, LOCK_TIMEOUT));
    }
}
