/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
