/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */package org.jboss.as.controller.client.helpers.standalone.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jboss.as.controller.client.helpers.standalone.DeploymentAction;

/**
 * Implementation of {@link DeploymentAction}.
 *
 * @author Brian Stansberry
 */
public class DeploymentActionImpl implements DeploymentAction, Serializable {

    private static final long serialVersionUID = 613098200977026475L;

     private static final InputStream EMPTY_STREAM = new InputStream() {
        @Override
        public int read() throws IOException {
            return -1;
        }
    };

    public static DeploymentActionImpl getAddAction(String deploymentName, String fileName, InputStream in, boolean internalStream) {
        return new DeploymentActionImpl(Type.ADD, deploymentName, fileName, in, internalStream, null);
    }

    public static DeploymentActionImpl getAddAction(String deploymentName, String fileName, Path in) {
        return new DeploymentActionImpl(Type.ADD, deploymentName, fileName, in, null);
    }

    public static DeploymentActionImpl getAddContentAction(String deploymentName, Map<String, InputStream> contents) {
        return new DeploymentActionImpl(Type.ADD_CONTENT, deploymentName, contents, true, null);
    }

    public static DeploymentActionImpl getAddContentFileAction(String deploymentName, Map<String, Path> files) {
        return new DeploymentActionImpl(Type.ADD_CONTENT, deploymentName, files, null);
    }

    public static DeploymentActionImpl getDeployAction(String deploymentName) {
        return new DeploymentActionImpl(Type.DEPLOY, deploymentName, null, (InputStream)null, false, null);
    }

    public static DeploymentActionImpl getExplodeAction(String deploymentName, String path) {
        return new DeploymentActionImpl(Type.EXPLODE, deploymentName, path, (InputStream)null, false, null);
    }

    public static DeploymentActionImpl getRedeployAction(String deploymentName) {
        return new DeploymentActionImpl(Type.REDEPLOY, deploymentName, null, (InputStream)null, false, null);
    }

    public static DeploymentActionImpl getUndeployAction(String deploymentName) {
        return new DeploymentActionImpl(Type.UNDEPLOY, deploymentName, null, (InputStream)null, false, null);
    }

    public static DeploymentActionImpl getReplaceAction(String deploymentName, String replacedName) {
        return new DeploymentActionImpl(Type.REPLACE, deploymentName, null, (InputStream)null, false, replacedName);
    }

    public static DeploymentActionImpl getFullReplaceAction(String deploymentName, String fileName, InputStream in, boolean internalStream) {
        return new DeploymentActionImpl(Type.FULL_REPLACE, deploymentName, fileName, in, internalStream, null);
    }

        public static DeploymentActionImpl getFullReplaceAction(String deploymentName, String fileName, Path in, boolean internalStream) {
        return new DeploymentActionImpl(Type.FULL_REPLACE, deploymentName, fileName, in, null);
    }

    public static DeploymentActionImpl getRemoveAction(String deploymentName) {
        return new DeploymentActionImpl(Type.REMOVE, deploymentName, null, (InputStream)null, false, null);
    }

    public static DeploymentActionImpl getRemoveContentAction(String deploymentName, List<String> fileNames) {
        Map<String, InputStream> contents = new HashMap<>(fileNames.size());
        for(String file : fileNames) {
            contents.put(file, EMPTY_STREAM);
        }
        return new DeploymentActionImpl(Type.REMOVE_CONTENT, deploymentName, contents, false, null);
    }

    private final UUID uuid = UUID.randomUUID();
    private final Type type;
    private final String deploymentUnitName;
    private final String oldDeploymentUnitName;
    private final String newContentFileName;
    private final InputStream contentStream;
    private final Map<String, InputStream> contents;
    private final Map<String, Path> files;
    private final boolean internalStream;

    private DeploymentActionImpl(Type type, String deploymentUnitName, String newContentFileName, InputStream contents, boolean internalStream, String replacedDeploymentUnitName) {
        this.type = type;
        this.deploymentUnitName = deploymentUnitName;
        this.newContentFileName = newContentFileName;
        if(newContentFileName != null && contents != null) {
            this.contents = Collections.singletonMap(newContentFileName, contents);
            this.files = Collections.emptyMap();
        } else {
            this.contents = Collections.emptyMap();
            this.files = Collections.emptyMap();
        }
        if(contents != null) {
            this.contentStream = contents;
        } else {
            this.contentStream = null;
        }
        this.oldDeploymentUnitName = replacedDeploymentUnitName;
        this.internalStream = internalStream;
    }

    private DeploymentActionImpl(Type type, String deploymentUnitName, String newContentFileName, Path file,String replacedDeploymentUnitName) {
        this.type = type;
        this.deploymentUnitName = deploymentUnitName;
        this.newContentFileName = newContentFileName;
        boolean useableFile = file != null && Files.exists(file) && Files.isRegularFile(file);
        if (newContentFileName != null && useableFile) {
            this.files = Collections.singletonMap(newContentFileName, file);
            this.contents = Collections.emptyMap();
        } else {
            this.files = Collections.emptyMap();
            this.contents = Collections.emptyMap();
        }
        if (useableFile) {
            try {
                this.contentStream = Files.newInputStream(file);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            this.contentStream = null;
        }
        this.oldDeploymentUnitName = replacedDeploymentUnitName;
        this.internalStream = true;
    }

    private DeploymentActionImpl(Type type, String deploymentUnitName, Map<String, InputStream> contents, boolean internalStream, String replacedDeploymentUnitName) {
        this.type = type;
        this.deploymentUnitName = deploymentUnitName;
        this.newContentFileName = null;
        this.contentStream = null;
        this.contents = contents;
        this.files = Collections.emptyMap();
        this.oldDeploymentUnitName = replacedDeploymentUnitName;
        this.internalStream = internalStream;
    }

        private DeploymentActionImpl(Type type, String deploymentUnitName, Map<String, Path> files,String replacedDeploymentUnitName) {
        this.type = type;
        this.deploymentUnitName = deploymentUnitName;
        this.newContentFileName = null;
        this.contentStream = null;
        this.files = files;
        this.contents = Collections.emptyMap();
        this.oldDeploymentUnitName = replacedDeploymentUnitName;
        this.internalStream = true;
    }

    @Override
    public UUID getId() {
        return uuid;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public String getDeploymentUnitUniqueName() {
        return deploymentUnitName;
    }

    @Override
    public String getReplacedDeploymentUnitUniqueName() {
        return oldDeploymentUnitName;
    }

    public String getNewContentFileName() {
        return newContentFileName;
    }

    public InputStream getContentStream() {
        return contentStream;
    }

    public Map<String, InputStream> getContents() {
        return contents;
    }

    public Map<String, Path> getFiles() {
        return files;
    }

    public boolean isInternalStream() {
        return internalStream;
    }
}
