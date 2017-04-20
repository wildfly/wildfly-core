/*
 * Copyright 2017 JBoss by Red Hat.
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
package org.jboss.as.repository;

import org.jboss.as.repository.logging.DeploymentRepositoryLogger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Simple service wrapper around the content repository.
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class ContentRepositoryService implements Service<ContentRepository> {

    private ContentRepository repository;

    ContentRepositoryService(ContentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void start(StartContext context) throws StartException {
        repository.readWrite();
        DeploymentRepositoryLogger.ROOT_LOGGER.debugf("%s started", ContentRepository.class.getSimpleName());
    }

    @Override
    public void stop(StopContext context) {
        repository.readOnly();
        DeploymentRepositoryLogger.ROOT_LOGGER.debugf("%s stopped", ContentRepository.class.getSimpleName());
    }

    @Override
    public ContentRepository getValue() throws IllegalStateException, IllegalArgumentException {
        return repository;
    }

}
