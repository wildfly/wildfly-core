/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr;

import java.nio.file.Path;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.installationmanager.HistoryResult;
import org.wildfly.installationmanager.ManifestVersion;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;


/**
 * Operation handler to get the history of the installation manager changes, either artifacts or configuration metadata as
 * channel changes.
 */
public class InstMgrHistoryHandler extends InstMgrOperationStepHandler {
    public static final String OPERATION_NAME = "history";

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER)
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY).setReplyType(ModelType.LIST).setRuntimeOnly().setReplyValueType(ModelType.OBJECT).build();

    InstMgrHistoryHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                try {
                    Path serverHome = imService.getHomeDir();
                    MavenOptions mavenOptions = new MavenOptions(null, false);
                    InstallationManager installationManager = imf.create(serverHome, mavenOptions);
                    ModelNode resulList = new ModelNode();

                    List<HistoryResult> history = installationManager.history();
                    for (HistoryResult hr : history) {
                        ModelNode entry = new ModelNode();
                        entry.get(InstMgrConstants.HISTORY_RESULT_HASH).set(hr.getName());
                        if (hr.timestamp() != null) {
                            final java.util.Date timestamp = Date.from(hr.timestamp());
                            entry.get(InstMgrConstants.HISTORY_RESULT_TIMESTAMP).set(DateFormat.getInstance().format(timestamp));
                        }
                        entry.get(InstMgrConstants.HISTORY_RESULT_TYPE).set(hr.getType().toLowerCase(Locale.ENGLISH));
                        if (hr.getVersions() != null && !hr.getVersions().isEmpty()) {
                            final ModelNode versions = entry.get(InstMgrConstants.HISTORY_RESULT_CHANNEL_VERSIONS);
                            hr.getVersions().stream()
                                    .map(ManifestVersion::getDescription)
                                    .map(ModelNode::new)
                                    .forEach(versions::add);
                        }
                        if (hr.getDescription() != null) {
                            entry.get(InstMgrConstants.HISTORY_RESULT_DESCRIPTION).set(hr.getDescription());
                        }
                        resulList.add(entry);
                    }

                    context.getResult().set(resulList);
                } catch (OperationFailedException | RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, OperationContext.Stage.RUNTIME, true);
    }
}
