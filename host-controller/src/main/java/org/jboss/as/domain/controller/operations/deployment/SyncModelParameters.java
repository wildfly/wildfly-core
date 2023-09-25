/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations.deployment;

import java.util.Map;

import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.domain.controller.DomainController;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.as.host.controller.mgmt.HostControllerRegistrationHandler;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.HostFileRepository;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class SyncModelParameters {

    private final DomainController domainController;
    private final IgnoredDomainResourceRegistry ignoredResourceRegistry;
    private final HostControllerEnvironment hostControllerEnvironment;
    private final ExtensionRegistry extensionRegistry;
    private final HostControllerRegistrationHandler.OperationExecutor operationExecutor;
    private final boolean fullModelTransfer;
    private final Map<String, ProxyController> serverProxies;
    private final HostFileRepository fileRepository;
    private final ContentRepository contentRepository;


    public SyncModelParameters(DomainController domainController,
                               IgnoredDomainResourceRegistry ignoredResourceRegistry,
                               HostControllerEnvironment hostControllerEnvironment,
                               ExtensionRegistry extensionRegistry,
                               HostControllerRegistrationHandler.OperationExecutor operationExecutor,
                               boolean fullModelTransfer,
                               Map<String, ProxyController> serverProxies, HostFileRepository fileRepository, ContentRepository contentRepository) {
        this.domainController = domainController;
        this.ignoredResourceRegistry = ignoredResourceRegistry;
        this.hostControllerEnvironment = hostControllerEnvironment;
        this.extensionRegistry = extensionRegistry;
        this.operationExecutor = operationExecutor;
        this.fullModelTransfer = fullModelTransfer;
        this.serverProxies = serverProxies;
        this.fileRepository = fileRepository;
        this.contentRepository = contentRepository;
    }

    public IgnoredDomainResourceRegistry getIgnoredResourceRegistry() {
        return ignoredResourceRegistry;
    }

    public HostControllerEnvironment getHostControllerEnvironment() {
        return hostControllerEnvironment;
    }

    public DomainController getDomainController() {
        return domainController;
    }

    public ExtensionRegistry getExtensionRegistry() {
        return extensionRegistry;
    }

    public HostControllerRegistrationHandler.OperationExecutor getOperationExecutor() {
        return operationExecutor;
    }

    public boolean isFullModelTransfer() {
        return fullModelTransfer;
    }

    public Map<String, ProxyController> getServerProxies() {
        return serverProxies;
    }

    public HostFileRepository getFileRepository() {
        return fileRepository;
    }

    public ContentRepository getContentRepository() {
        return contentRepository;
    }
}
