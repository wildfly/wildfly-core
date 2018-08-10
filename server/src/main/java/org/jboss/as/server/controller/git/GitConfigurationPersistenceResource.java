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

import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.jboss.as.controller.persistence.AbstractConfigurationPersister;
import org.jboss.as.controller.persistence.AbstractFilePersistenceResource;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.dmr.ModelNode;

/**
 *{@link ConfigurationPersister.PersistenceResource} that persists to a configuration file upon commit, also
 * ensuring a git commit is made.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class GitConfigurationPersistenceResource extends AbstractFilePersistenceResource {

    protected final File file;
    private final GitRepository repository;

    public GitConfigurationPersistenceResource(final ModelNode model, final File fileName, final GitRepository repository,
            final AbstractConfigurationPersister persister) throws ConfigurationPersistenceException {
       super(model, persister);
        this.file = fileName;
        this.repository = repository;
    }

    @Override
    public void rollback() {
        super.rollback();
        try (Git git = repository.getGit()) {
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef(Constants.HEAD).call();
        } catch (GitAPIException ex) {
            MGMT_OP_LOGGER.failedToStoreConfiguration(ex, file.getName());
        }
    }

    protected void gitCommit(String msg) {
        try (Git git = repository.getGit()) {
            if(!git.status().call().isClean()) {
                git.commit().setMessage(msg).setAll(true).setNoVerify(true).call();
            }
        } catch (GitAPIException e) {
            MGMT_OP_LOGGER.failedToStoreConfiguration(e, file.getName());
        }
    }

    @Override
    protected void doCommit(InputStream in) {
        try {
            Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            gitCommit("Storing configuration");
        } catch (IOException ex) {
            MGMT_OP_LOGGER.failedToStoreConfiguration(ex, file.getName());
        }
    }

}
