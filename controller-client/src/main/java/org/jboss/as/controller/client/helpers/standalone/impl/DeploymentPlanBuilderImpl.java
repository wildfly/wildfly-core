/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.standalone.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.client.logging.ControllerClientLogger;
import org.jboss.as.controller.client.helpers.standalone.AddDeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.standalone.DeploymentAction;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlan;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.standalone.InitialDeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.standalone.ReplaceDeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.standalone.UndeployDeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.standalone.DeploymentAction.Type;
import org.jboss.as.controller.client.impl.InputStreamEntry.FileStreamEntry;
import org.jboss.as.protocol.StreamUtils;

/**
 * {@link DeploymentPlanBuilder} implementation meant to handle in-VM calls.
 *
 * @author Brian Stansberry
 */
class DeploymentPlanBuilderImpl
    implements AddDeploymentPlanBuilder, InitialDeploymentPlanBuilder, UndeployDeploymentPlanBuilder  {

    private final boolean shutdown;
    private final long gracefulShutdownPeriod;
    private final boolean globalRollback;
    private volatile boolean cleanupInFinalize = true;

    private final List<DeploymentActionImpl> deploymentActions = new ArrayList<>();

    DeploymentPlanBuilderImpl() {
        this.shutdown = false;
        this.globalRollback = true;
        this.gracefulShutdownPeriod = -1;
    }

    DeploymentPlanBuilderImpl(DeploymentPlanBuilderImpl existing) {
        this.deploymentActions.addAll(existing.deploymentActions);
        this.shutdown = existing.shutdown;
        this.globalRollback = existing.globalRollback;
        this.gracefulShutdownPeriod = existing.gracefulShutdownPeriod;
        existing.cleanupInFinalize = false;
    }

    DeploymentPlanBuilderImpl(DeploymentPlanBuilderImpl existing, boolean globalRollback) {
        this.deploymentActions.addAll(existing.deploymentActions);
        this.shutdown = false;
        this.globalRollback = globalRollback;
        this.gracefulShutdownPeriod = -1;
        existing.cleanupInFinalize = false;
    }

    DeploymentPlanBuilderImpl(DeploymentPlanBuilderImpl existing, long gracefulShutdownPeriod) {
        this.deploymentActions.addAll(existing.deploymentActions);
        this.shutdown = true;
        this.globalRollback = false;
        this.gracefulShutdownPeriod = gracefulShutdownPeriod;
        existing.cleanupInFinalize = false;
    }

    DeploymentPlanBuilderImpl(DeploymentPlanBuilderImpl existing, DeploymentActionImpl modification) {
        this(existing);
        this.deploymentActions.add(modification);
    }

    @Override
    public DeploymentAction getLastAction() {
        return deploymentActions.isEmpty() ? null : deploymentActions.get(deploymentActions.size() - 1);
    }

    @Override
    public List<DeploymentAction> getDeploymentActions() {
        return new ArrayList<>(deploymentActions);
    }

    @Override
    public long getGracefulShutdownTimeout() {
        return gracefulShutdownPeriod;
    }

    @Override
    public boolean isGlobalRollback() {
        return globalRollback;
    }

    @Override
    public boolean isGracefulShutdown() {
        return shutdown && gracefulShutdownPeriod > -1;
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public DeploymentPlan build() {
        DeploymentPlan dp = new DeploymentPlanImpl(Collections.unmodifiableList(deploymentActions), globalRollback, shutdown, gracefulShutdownPeriod);
        cleanupInFinalize = false;
        return dp;
    }

    @Override
    public AddDeploymentPlanBuilder add(File file) throws IOException {
        String name = file.getName();
        return add(name, file);
    }

    @Override
    public AddDeploymentPlanBuilder add(URL url) throws IOException {
        String name = getName(url);
        return add(name, name, url);
    }

    @Override
    public AddDeploymentPlanBuilder add(String name, File file) throws IOException {
        return add(name, name, new FileStreamEntry(file), true);
    }

    @Override
    public AddDeploymentPlanBuilder add(String name, URL url) throws IOException {
        return add(name, name, url);
    }

    private AddDeploymentPlanBuilder add(String name, String commonName, URL url) throws IOException {
        try {
            URLConnection conn = url.openConnection();
            conn.connect();
            InputStream stream = conn.getInputStream();
            return add(name, commonName, stream, true);
        } catch (IOException e) {
            cleanup();
            throw e;
        }
    }

    private DeploymentPlanBuilder replace(String name, String commonName, URL url) throws IOException {
        try {
            URLConnection conn = url.openConnection();
            conn.connect();
            InputStream stream = conn.getInputStream();
            return replace(name, commonName, stream, true);
        } catch (IOException e) {
            cleanup();
            throw e;
        }
    }

    @Override
    public AddDeploymentPlanBuilder add(String name, InputStream stream) {
        return add(name, name, stream);
    }

    @Override
    public AddDeploymentPlanBuilder add(String name, String commonName, InputStream stream) {
        return add(name, commonName, stream, false);
    }

    private AddDeploymentPlanBuilder add(String name, String commonName, InputStream stream, boolean internalStream) {
        DeploymentActionImpl mod = DeploymentActionImpl.getAddAction(name, commonName, stream, internalStream);
        return new DeploymentPlanBuilderImpl(this, mod);
    }

    /* (non-Javadoc)
     * @see org.jboss.as.deployment.client.api.server.AddDeploymentPlanBuilder#andDeploy()
     */
    @Override
    public DeploymentPlanBuilder andDeploy() {
        String addedKey = getAddedContentKey();
        DeploymentActionImpl deployMod = DeploymentActionImpl.getDeployAction(addedKey);
        return new DeploymentPlanBuilderImpl(this, deployMod);
    }

    /* (non-Javadoc)
     * @see org.jboss.as.deployment.client.api.server.AddDeploymentPlanBuilder#andReplace(java.lang.String)
     */
    @Override
    public ReplaceDeploymentPlanBuilder andReplace(String toReplace) {
        String newContentKey = getAddedContentKey();
        return replace(newContentKey, toReplace);
    }

    @Override
    public DeploymentPlanBuilder deploy(String key) {
        DeploymentActionImpl mod = DeploymentActionImpl.getDeployAction(key);
        return new DeploymentPlanBuilderImpl(this, mod);
    }

    @Override
    public UndeployDeploymentPlanBuilder undeploy(String key) {
        DeploymentActionImpl mod = DeploymentActionImpl.getUndeployAction(key);
        return new DeploymentPlanBuilderImpl(this, mod);
    }

    @Override
    public DeploymentPlanBuilder redeploy(String deploymentName) {
        DeploymentActionImpl mod = DeploymentActionImpl.getRedeployAction(deploymentName);
        return new DeploymentPlanBuilderImpl(this, mod);
    }

    @Override
    public ReplaceDeploymentPlanBuilder replace(String replacement, String toReplace) {
        DeploymentActionImpl mod = DeploymentActionImpl.getReplaceAction(replacement, toReplace);
        return new ReplaceDeploymentPlanBuilderImpl(this, mod);
    }

    @Override
    public DeploymentPlanBuilder replace(File file) throws IOException {
        String name = file.getName();
        return replace(name, file);
    }

    @Override
    public DeploymentPlanBuilder replace(URL url) throws IOException {
        String name = getName(url);
        return replace(name, name, url);
    }

    @Override
    public DeploymentPlanBuilder replace(String name, File file) throws IOException {
        return replace(name, name, new FileStreamEntry(file), true);
    }

    @Override
    public DeploymentPlanBuilder replace(String name, URL url) throws IOException {
        return replace(name, name, url);
    }

    @Override
    public DeploymentPlanBuilder replace(String name, InputStream stream) {
        return replace(name, name, stream);
    }

    @Override
    public DeploymentPlanBuilder replace(String name, String commonName, InputStream stream) {
        return replace(name, commonName, stream, false);
    }

    private DeploymentPlanBuilder replace(String name, String commonName, InputStream stream, boolean internalStream) {

        DeploymentActionImpl mod = DeploymentActionImpl.getFullReplaceAction(name, commonName, stream, internalStream);
        return new DeploymentPlanBuilderImpl(this, mod);
    }

    @Override
    public DeploymentPlanBuilder andRemoveUndeployed() {
        DeploymentAction last = getLastAction();
        if (last.getType() != Type.UNDEPLOY) {
            // Someone cast to the impl class instead of using the interface
            cleanup();
            throw ControllerClientLogger.ROOT_LOGGER.invalidPrecedingAction(Type.UNDEPLOY);
        }
        DeploymentActionImpl removeMod = DeploymentActionImpl.getRemoveAction(last.getDeploymentUnitUniqueName());
        return new DeploymentPlanBuilderImpl(this, removeMod);
    }

    @Override
    public DeploymentPlanBuilder remove(String key) {
        DeploymentActionImpl removeMod = DeploymentActionImpl.getRemoveAction(key);
        return new DeploymentPlanBuilderImpl(this, removeMod);
    }

    @Override
    public DeploymentPlanBuilder withoutRollback() {
        if (!deploymentActions.isEmpty()) {
            // Someone has cast to this impl class
            cleanup();
            throw ControllerClientLogger.ROOT_LOGGER.operationsNotAllowed(InitialDeploymentPlanBuilder.class.getSimpleName());
        }
        return new DeploymentPlanBuilderImpl(this, false);
    }

    @Override
    public DeploymentPlanBuilder withGracefulShutdown(long timeout, TimeUnit timeUnit) {
        // TODO determine how to remove content. Perhaps with a signal to the
        // deployment repository service such that as part of shutdown after
        // undeploys are done it then removes the content?

        if (!deploymentActions.isEmpty()) {
            // Someone has to cast this impl class
            cleanup();
            throw ControllerClientLogger.ROOT_LOGGER.operationsNotAllowed(InitialDeploymentPlanBuilder.class.getSimpleName());
        }
        if (globalRollback) {
            cleanup();
            throw ControllerClientLogger.ROOT_LOGGER.globalRollbackNotCompatible();
        }
        long period = timeUnit.toMillis(timeout);
        if (shutdown && period != gracefulShutdownPeriod) {
            cleanup();
            throw ControllerClientLogger.ROOT_LOGGER.gracefulShutdownAlreadyConfigured(gracefulShutdownPeriod);
        }
        return new DeploymentPlanBuilderImpl(this, period);
    }

    @Override
    public DeploymentPlanBuilder withShutdown() {
        // TODO determine how to remove content. Perhaps with a signal to the
        // deployment repository service such that as part of shutdown after
        // undeploys are done it then removes the content?

        if (!deploymentActions.isEmpty()) {
            // Someone has to cast this impl class
            cleanup();
            throw ControllerClientLogger.ROOT_LOGGER.operationsNotAllowed(InitialDeploymentPlanBuilder.class.getSimpleName());
        }
        if (globalRollback) {
            cleanup();
            throw ControllerClientLogger.ROOT_LOGGER.globalRollbackNotCompatible();
        }
        if (shutdown && gracefulShutdownPeriod != -1) {
            cleanup();
            throw ControllerClientLogger.ROOT_LOGGER.gracefulShutdownAlreadyConfigured(gracefulShutdownPeriod);
        }
        return new DeploymentPlanBuilderImpl(this, -1);
    }


    private String getAddedContentKey() {
        DeploymentAction last = getLastAction();
        if (last.getType() != Type.ADD) {
            // Someone cast to the impl class instead of using the interface
            cleanup();
            throw ControllerClientLogger.ROOT_LOGGER.invalidPrecedingAction(Type.ADD);
        }
        return last.getDeploymentUnitUniqueName();
    }

    private String getName(URL url) {
        if ("file".equals(url.getProtocol())) {
            try {
                File f = new File(url.toURI());
                return f.getName();
            } catch (URISyntaxException e) {
                cleanup();
                throw ControllerClientLogger.ROOT_LOGGER.invalidUri(e, url);
            }
        }

        String path = url.getPath();
        int idx = path.lastIndexOf('/');
        while (idx == path.length() - 1) {
            path = path.substring(0, idx);
            idx = path.lastIndexOf('/');
        }
        if (idx == -1) {
            cleanup();
            throw ControllerClientLogger.ROOT_LOGGER.cannotDeriveDeploymentName(url);
        }

        return path.substring(idx + 1);
    }

    protected void cleanup() {
        for (DeploymentActionImpl action : deploymentActions) {
            if (action.isInternalStream() && action.getContentStream() != null) {
                StreamUtils.safeClose(action.getContentStream());
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (cleanupInFinalize) {
            cleanup();
        }
    }

    @Override
    public DeploymentPlanBuilder explodeDeployment(String deploymentName) throws IOException {
        DeploymentActionImpl mod = DeploymentActionImpl.getExplodeAction(deploymentName, null);
        return new DeploymentPlanBuilderImpl(this, mod);
    }

    @Override
    public DeploymentPlanBuilder explodeDeploymentContent(String deploymentName, String path) throws IOException {
        DeploymentActionImpl mod = DeploymentActionImpl.getExplodeAction(deploymentName, path);
        return new DeploymentPlanBuilderImpl(this, mod);
    }

    @Override
    public DeploymentPlanBuilder addContentToDeployment(String deploymentName, Map<String, InputStream> contents) throws IOException {
        DeploymentActionImpl mod = DeploymentActionImpl.getAddContentAction(deploymentName, contents);
        return new DeploymentPlanBuilderImpl(this, mod);
    }

    @Override
    public DeploymentPlanBuilder addContentFileToDeployment(String deploymentName, Map<String, Path> files) throws IOException {
        DeploymentActionImpl mod = DeploymentActionImpl.getAddContentFileAction(deploymentName, files);
        return new DeploymentPlanBuilderImpl(this, mod);
    }

    @Override
    public DeploymentPlanBuilder removeContentFromDeployment(String deploymentName, List<String> paths) throws IOException {
         DeploymentActionImpl mod = DeploymentActionImpl.getRemoveContentAction(deploymentName, paths);
        return new DeploymentPlanBuilderImpl(this, mod);
    }

}
