/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.core.management.deployment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

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
import org.wildfly.extension.core.management.UnstableApiAnnotationResourceDefinition;
import org.wildfly.extension.core.management.logging.CoreManagementLogger;
import org.wildfly.unstable.api.annotation.classpath.index.RuntimeIndex;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.ClassInfoScanner;

public class ScanUnstableApiAnnotationsProcessor implements DeploymentUnitProcessor {

    private final RuntimeIndex runtimeIndex;

    // If set we will output some extra information to the logs during testing, so we can verify the number of classes scanned
    // This is important for WildFly to make sure that the scanning of nested archives (especially wars and ears) is
    // working as expected and to guard against scanning classes multiple times (which happened during the development of this feature)
    private final String EXTRA_TEST_OUTPUT_PROPERTY = "org.wildfly.test.unstable-api-annotation.extra-output";

    private static final String BASE_MODULE_NAME = "org.wildfly._internal.unstable-api-annotation-index";
    private static final String INDEX_FILE = "index.txt";
    private final Stability stability;
    private final UnstableApiAnnotationResourceDefinition.UnstableApiAnnotationLevel level;

    private boolean extraTestOutput;

    public ScanUnstableApiAnnotationsProcessor(RunningMode runningMode, Stability stability, UnstableApiAnnotationResourceDefinition.UnstableApiAnnotationLevel level) {
        this.stability = stability;
        this.level = level;
        extraTestOutput = System.getProperties().containsKey(EXTRA_TEST_OUTPUT_PROPERTY);

        boolean enableScanning = true;
        if (runningMode == RunningMode.ADMIN_ONLY) {
            enableScanning = false;
        } else if (stability.enables(Stability.EXPERIMENTAL) || !stability.enables(UnstableApiAnnotationResourceDefinition.STABILITY)) {
            // We don't care about scanning at the experimental level.
            // Also, we need to be at the level where the feature is enabled or lower
            enableScanning = false;
        }

        RuntimeIndex runtimeIndex = null;
        if (enableScanning) {
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
    public void deploy(DeploymentPhaseContext phaseContext) {
        if (runtimeIndex == null) {
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
