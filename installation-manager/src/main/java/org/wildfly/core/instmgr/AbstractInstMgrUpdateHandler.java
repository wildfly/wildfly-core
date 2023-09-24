/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILESYSTEM_PATH;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.ObjectTypeValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.core.instmgr.logging.InstMgrLogger;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;
import org.wildfly.installationmanager.spi.OsShell;

/**
 * Abstract class for Installation Manager Handlers used to list updates, prepare updates and prepare a revert.
 */
abstract class AbstractInstMgrUpdateHandler extends InstMgrOperationStepHandler {
    protected static final AttributeDefinition OFFLINE = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.OFFLINE, ModelType.BOOLEAN)
            .setStorageRuntime()
            .setDefaultValue(ModelNode.FALSE)
            .setRequired(false)
            .build();

    protected static final AttributeDefinition REPOSITORY_ID = new SimpleAttributeDefinitionBuilder(InstMgrConstants.REPOSITORY_ID, ModelType.STRING)
            .setStorageRuntime()
            .build();

    protected static final AttributeDefinition REPOSITORY_URL = new SimpleAttributeDefinitionBuilder(InstMgrConstants.REPOSITORY_URL, ModelType.STRING)
            .setStorageRuntime()
            .build();

    protected static final ObjectTypeAttributeDefinition REPOSITORY = new ObjectTypeAttributeDefinition.Builder(InstMgrConstants.REPOSITORY, REPOSITORY_ID, REPOSITORY_URL)
            .setStorageRuntime()
            .setRequired(false)
            .setValidator(new RepositoryValidator())
            .setSuffix("repository")
            .build();

    protected static final SimpleAttributeDefinition LOCAL_CACHE = new SimpleAttributeDefinitionBuilder(InstMgrConstants.LOCAL_CACHE, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .addArbitraryDescriptor(FILESYSTEM_PATH, ModelNode.TRUE)
            .setMinSize(1)
            .setRequired(false)
            .setAlternatives(InstMgrConstants.NO_RESOLVE_LOCAL_CACHE)
            .build();

    protected static final AttributeDefinition NO_RESOLVE_LOCAL_CACHE = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.NO_RESOLVE_LOCAL_CACHE, ModelType.BOOLEAN)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setDefaultValue(ModelNode.FALSE)
            .setRequired(false)
            .setStorageRuntime()
            .setAlternatives(InstMgrConstants.LOCAL_CACHE)
            .build();

    AbstractInstMgrUpdateHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    protected List<Repository> toRepositories(OperationContext context, List<ModelNode> repositoriesMn) throws OperationFailedException {
        final List<Repository> result = new ArrayList<>();

        if (repositoriesMn != null) {
            for (ModelNode repoModelNode : repositoriesMn) {
                String id = REPOSITORY_ID.resolveModelAttribute(context, repoModelNode).asString();
                String url = REPOSITORY_URL.resolveModelAttribute(context, repoModelNode).asString();
                try {
                    result.add(new Repository(id, new URI(url).toURL().toExternalForm()));
                } catch (MalformedURLException | URISyntaxException e) {
                    throw new OperationFailedException(e);
                }
            }
        }

        return result;
    }

    private static class RepositoryValidator extends ObjectTypeValidator {
        public RepositoryValidator() {
            super(false, REPOSITORY_ID, REPOSITORY_URL);
        }
        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            super.validateParameter(parameterName, value);

            String repoUrl = value.get(InstMgrConstants.REPOSITORY_URL).asStringOrNull();
            try {
                new URI(repoUrl).toURL();
            } catch (MalformedURLException | URISyntaxException e) {
                throw InstMgrLogger.ROOT_LOGGER.invalidRepositoryURL(repoUrl);
            }
        }
    }

    protected static OsShell getOsShell() {
        String env = System.getenv("JBOSS_LAUNCH_SCRIPT");
        InstMgrLogger.ROOT_LOGGER.debug("Detected server launched from " + env + " script");
        if (env != null) {
            switch (env) {
                case "powershell": return OsShell.WindowsPowerShell;
                case "batch": return OsShell.WindowsBash;
                default: return OsShell.Linux;
            }
        }
        return OsShell.Linux;
    }

    protected List<Repository> retrieveAllCustomPatchRepositories(InstallationManager im) throws Exception {
        Collection<Channel> channels = im.listChannels();
        List<Repository> customChannelRepositories = new ArrayList<>();
        for (Channel c : channels) {
            if (c.getName().startsWith(InstMgrConstants.DEFAULT_CUSTOM_CHANNEL_NAME_PREFIX) && c.getManifestCoordinate().isPresent()) {
                String customPatchManifest = c.getManifestCoordinate().get().replace(":", "_");
                // and additional verification just to be sure we are using a custom channel created by us
                String customPatchManifestChannel = c.getName().replace(InstMgrConstants.DEFAULT_CUSTOM_CHANNEL_NAME_PREFIX, "");
                if (customPatchManifestChannel.equals(customPatchManifest)) {
                    Path customPatchDir = imService.getCustomPatchDir(customPatchManifest);
                    if (Files.exists(customPatchDir)) {
                        customChannelRepositories.addAll(c.getRepositories());
                    }
                }
            }
        }
        return customChannelRepositories;
    }
}
