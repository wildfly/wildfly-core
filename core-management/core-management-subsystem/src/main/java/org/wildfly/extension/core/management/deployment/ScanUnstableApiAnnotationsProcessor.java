/*
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2024 Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.wildfly.extension.core.management.deployment;

import org.jboss.as.controller.RunningMode;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.DeploymentUtils;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.version.Stability;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.vfs.VirtualFile;
import org.jboss.vfs.VisitorAttributes;
import org.jboss.vfs.util.SuffixMatchFilter;
import org.wildfly.extension.core.management.logging.CoreManagementLogger;
import org.wildfly.unstable.api.annotation.classpath.index.RuntimeIndex;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.ClassInfoScanner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ScanUnstableApiAnnotationsProcessor implements DeploymentUnitProcessor {

    private final RuntimeIndex runtimeIndex;

    // If set we will output some extra information to the logs during testing
    private final String EXTRA_TEST_OUTPUT_PROPERTY = "org.wildfly.test.unstable-api-annotation.extra-output";

    private static final String BASE_MODULE_NAME = "org.wildfly._internal.unstable-api-annotation-index";
    private static final String INDEX_FILE = "index.txt";
    private final Stability stability;

    private boolean extraTestOutput;

    public ScanUnstableApiAnnotationsProcessor(RunningMode runningMode, Stability stability) {
        this.stability = stability;
        RuntimeIndex runtimeIndex = null;
        extraTestOutput = System.getProperties().containsKey(EXTRA_TEST_OUTPUT_PROPERTY);
        if (runningMode != RunningMode.ADMIN_ONLY) {
            ModuleLoader moduleLoader = ((ModuleClassLoader) this.getClass().getClassLoader()).getModule().getModuleLoader();
            Module module = null;
            try {
                module = moduleLoader.loadModule(BASE_MODULE_NAME);
            } catch (ModuleLoadException e) {
                // TODO make this module part of core so it is always there
            }
            if (module != null) {
                URL url = module.getExportedResource(INDEX_FILE);
                List<URL> urls = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                    String fileName = reader.readLine();
                    while (fileName != null) {
                        fileName = fileName.trim();
                        if (!fileName.isEmpty() && !fileName.startsWith("#")) {
                            urls.add(module.getExportedResource(fileName));
                        }
                        fileName = reader.readLine();
                    }

                    if (!urls.isEmpty()) {
                        runtimeIndex = RuntimeIndex.load(urls);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        this.runtimeIndex = runtimeIndex;
    }

    /**
     * Process this deployment for annotations.  This will use an annotation indexer to create an index of all annotations
     * found in this deployment and attach it to the deployment unit context.
     *
     * @param phaseContext the deployment unit context
     * @throws DeploymentUnitProcessingException
     */
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        if (runtimeIndex == null) {
            return;
        }
        if (stability.enables(Stability.EXPERIMENTAL)) {
            // If running with Stability.EXPERIMENTAL we don't care if the user is using experimental annotations
            return;
        }

        final DeploymentUnit du = phaseContext.getDeploymentUnit();
        DeploymentUnit top = DeploymentUtils.getTopDeploymentUnit(du);

        ClassInfoScanner scanner = top.getAttachment(UnstableApiAnnotationAttachments.UNSTABLE_API_ANNOTATION_SCANNER);
        if (scanner == null) {
            scanner = new ClassInfoScanner(runtimeIndex);
            top.putAttachment(UnstableApiAnnotationAttachments.UNSTABLE_API_ANNOTATION_SCANNER, scanner);
        }
        ProcessedClassCounter counter = new ProcessedClassCounter();

        ServerLogger.DEPLOYMENT_LOGGER.debug("Scanning deployment for unstable api annotations");
        List<ResourceRoot> resourceRoots = DeploymentUtils.allResourceRoots(du);
        for (ResourceRoot root : resourceRoots) {

            // The EarStructureProcessor and WarStructureProcessor in full will set this accordingly
            Boolean shouldIndexResource = root.getAttachment(Attachments.INDEX_RESOURCE_ROOT);
            if (shouldIndexResource != null && !shouldIndexResource) {
                continue;
            }

            if (root.getAttachment(UnstableApiAnnotationAttachments.UNSTABLE_API_ANNOTATIONS_SCANNED) != null) {
                continue;
            }
            final VisitorAttributes visitorAttributes = new VisitorAttributes();
            visitorAttributes.setLeavesOnly(true);
            visitorAttributes.setRecurseFilter(f-> true);

            try {
                final List<VirtualFile> classChildren = root.getRoot().getChildren(new SuffixMatchFilter(".class", visitorAttributes));
                for (VirtualFile file : classChildren) {
                    if (file.isFile() && file.getName().endsWith(".class")) {
                        try (InputStream in = file.openStream()) {
                            scanner.scanClass(in);
                            counter.incrementClassesScannedCount();
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            root.putAttachment(UnstableApiAnnotationAttachments.UNSTABLE_API_ANNOTATIONS_SCANNED, true);
        }

        long time = (System.currentTimeMillis() - counter.startTime);
        CoreManagementLogger.UNSUPPORTED_ANNOTATION_LOGGER.debugf("Unstable annotation api scan took %d ms to scan %d classes", time, counter.getClassesScannedCount());
        if (extraTestOutput) {
            CoreManagementLogger.UNSUPPORTED_ANNOTATION_LOGGER.testOutputNumberOfClassesScanned(counter.getClassesScannedCount());
        }
    }

    public static class ProcessedClassCounter {
        private final long startTime = System.currentTimeMillis();

        private volatile int classesScannedCount = 0;

        void incrementClassesScannedCount() {
            classesScannedCount++;
        }

        public int getClassesScannedCount() {
            return classesScannedCount;
        }
    }
}
