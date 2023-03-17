/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.core.instmgr;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILESYSTEM_PATH;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.core.instmgr.logging.InstMgrLogger;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

/**
 * Abstract class for Installation Manager Handlers used to update and revert.
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
            .setValidator(new ParameterValidator() {
                @Override
                public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
                    if (parameterName.equals(REPOSITORY.getName())) {
                        if (value.isDefined()) {
                            String repoUrl = value.get(InstMgrConstants.REPOSITORY_URL).asStringOrNull();
                            if (repoUrl == null) {
                                throw InstMgrLogger.ROOT_LOGGER.noRepositoryURLDefined();
                            }
                            try {
                                new URL(repoUrl);
                            } catch (MalformedURLException e) {
                                throw InstMgrLogger.ROOT_LOGGER.invalidRepositoryURL(repoUrl);
                            }
                            String repoId = value.get(InstMgrConstants.REPOSITORY_ID).asStringOrNull();
                            if (repoId == null) {
                                throw InstMgrLogger.ROOT_LOGGER.noRepositoryIDDefined();
                            }
                        }
                    }
                }
            })
            .setSuffix("repository")
            .build();

    protected static final SimpleAttributeDefinition LOCAL_CACHE = new SimpleAttributeDefinitionBuilder(InstMgrConstants.LOCAL_CACHE, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setAllowExpression(true)
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

    protected List<Repository> toRepositories(List<ModelNode> repositoriesMn) {
        final List<Repository> result = new ArrayList<>();

        if (repositoriesMn != null) {
            for (ModelNode repoModelNode : repositoriesMn) {
                String id = repoModelNode.get(REPOSITORY_ID.getName()).asString();
                String url = repoModelNode.get(REPOSITORY_URL.getName()).asString();
                result.add(new Repository(id, url));
            }
        }

        return result;
    }
}
