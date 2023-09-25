/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.controller.git;

import static org.eclipse.jgit.lib.Constants.R_TAGS;
import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.namespace.QName;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.persistence.ConfigurationFile;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ConfigurationPersister.PersistenceResource;
import org.jboss.as.controller.persistence.ConfigurationPersister.SnapshotInfo;
import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.as.controller.persistence.XmlConfigurationPersister;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;

/**
 * A configuration persister which uses an XML file for backing storage and Git for history support.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class GitConfigurationPersister extends XmlConfigurationPersister {
    private final AtomicBoolean successfulBoot = new AtomicBoolean();
    private GitRepository gitRepository;
    private final Path root;
    private final File mainFile;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS");
    private static final String SNAPSHOT_PREFIX = "Snapshot-";

    public GitConfigurationPersister(GitRepository gitRepository, ConfigurationFile file, QName rootElement, XMLElementReader<List<ModelNode>> rootParser,
            XMLElementWriter<ModelMarshallingContext> rootDeparser, boolean suppressLoad) {
        super(file.getBootFile(), rootElement, rootParser, rootDeparser, suppressLoad);
        root = file.getConfigurationDir().getParentFile().toPath();
        mainFile = file.getMainFile();
        this.gitRepository = gitRepository;
        File baseDir = root.toFile();
        try {
            File gitDir = new File(baseDir, Constants.DOT_GIT);
            if(!gitDir.exists()) {
                gitDir.mkdir();
            }
            if (gitRepository.isBare()) {
                Git.init().setDirectory(baseDir).setGitDir(gitDir).call();
                ServerLogger.ROOT_LOGGER.gitRespositoryInitialized(baseDir.getAbsolutePath());
            }
        } catch (IllegalStateException | GitAPIException e) {
            ControllerLogger.ROOT_LOGGER.error(e);
        }
    }

    public GitConfigurationPersister(GitRepository gitRepository, ConfigurationFile file, QName rootElement, XMLElementReader<List<ModelNode>> rootParser,
            XMLElementWriter<ModelMarshallingContext> rootDeparser) {
        this(gitRepository, file, rootElement, rootParser, rootDeparser, false);
    }

    @Override
    public void successfulBoot() throws ConfigurationPersistenceException {
        successfulBoot.compareAndSet(false, true);
    }

    @Override
    public PersistenceResource store(final ModelNode model, Set<PathAddress> affectedAddresses) throws ConfigurationPersistenceException {
        if(!successfulBoot.get()) {
            return new PersistenceResource() {
                @Override
                public void commit() {
                }

                @Override
                public void rollback() {
                }
            };
        }
        return new GitConfigurationPersistenceResource(model, mainFile, gitRepository, this);
    }

    @Override
    public String snapshot(String name, String comment) throws ConfigurationPersistenceException {
        boolean noComment = (comment ==null || comment.isEmpty());
        String message = noComment ? SNAPSHOT_PREFIX + FORMATTER.format(LocalDateTime.now()) : comment;
        String tagName = (name ==null || name.isEmpty()) ? SNAPSHOT_PREFIX + FORMATTER.format(LocalDateTime.now()) : name;
        try (Git git = gitRepository.getGit()) {
            Status status = git.status().call();
            List<Ref> tags = git.tagList().call();
            String refTagName = R_TAGS + tagName;
            for(Ref tag : tags) {
                if(refTagName.equals(tag.getName())) {
                   throw MGMT_OP_LOGGER.snapshotAlreadyExistError(tagName);
                }
            }
            //if comment is not null
            if(status.hasUncommittedChanges() || !noComment) {
                git.commit().setMessage(message).setAll(true).setNoVerify(true).call();
            }
            git.tag().setName(tagName).setMessage(message).call();
        } catch (GitAPIException ex) {
            throw MGMT_OP_LOGGER.failedToPersistConfiguration(ex, message, ex.getMessage());
        }
        return message;
    }

    @Override
    public String publish(String name) throws ConfigurationPersistenceException {
        StringBuilder message = new StringBuilder();
        String remoteName = gitRepository.getRemoteName(name);
        if (remoteName != null && gitRepository.isValidRemoteName(remoteName)) {
            try (Git git = gitRepository.getGit()) {
                Iterable<PushResult> result = git.push().setRemote(remoteName)
                        .setRefSpecs(new RefSpec(gitRepository.getBranch() + ':' + gitRepository.getBranch()))
                        .setPushTags().call();
                for (PushResult pushResult : result) {
                    for (RemoteRefUpdate refUpdate : pushResult.getRemoteUpdates()) {
                        message.append(refUpdate.getMessage()).append(" ").append(refUpdate.getNewObjectId().name()).append('\n');
                    }
                }
            } catch (GitAPIException ex) {
                throw MGMT_OP_LOGGER.failedToPublishConfiguration(ex, name, ex.getMessage());
            }
        } else {
            throw MGMT_OP_LOGGER.failedToPublishConfigurationInvalidRemote(name);
        }
        return message.toString();
    }

    @Override
    public void deleteSnapshot(String name) {
         try (Git git = gitRepository.getGit()) {
             git.tagDelete().setTags(name).call();
        } catch (GitAPIException ex) {
            MGMT_OP_LOGGER.failedToDeleteConfigurationSnapshot(ex,name);
        }
    }

    @Override
    public SnapshotInfo listSnapshots() {
        try (Git git = gitRepository.getGit()) {
            final List<String> snapshots = new ArrayList<>();
            for(Ref ref : git.tagList().call()) {
                RevWalk revWalk = new RevWalk(git.getRepository());
                revWalk.sort(RevSort.COMMIT_TIME_DESC, true);
                try {
                    RevTag annotatedTag = revWalk.parseTag(ref.getObjectId());
                    snapshots.add(annotatedTag.getTagName() + " : " + annotatedTag.getFullMessage());
                } catch (IncorrectObjectTypeException ex) {
                   snapshots.add(ref.getName());
                }
                snapshots.add(ref.getName());
            }
            return new SnapshotInfo() {
                @Override
                public String getSnapshotDirectory() {
                    return "";
                }

                @Override
                public List<String> names() {
                    return snapshots;
                }
            };
        } catch (GitAPIException ex) {
            MGMT_OP_LOGGER.failedToListConfigurationSnapshot(ex, mainFile.getName());
        } catch (IOException ex) {
            MGMT_OP_LOGGER.failedToListConfigurationSnapshot(ex, mainFile.getName());
        }
        return NULL_SNAPSHOT_INFO;
    }

}
