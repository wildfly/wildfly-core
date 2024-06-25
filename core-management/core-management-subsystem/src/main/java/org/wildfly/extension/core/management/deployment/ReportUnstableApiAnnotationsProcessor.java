/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.core.management.deployment;

import static org.wildfly.extension.core.management.logging.CoreManagementLogger.UNSUPPORTED_ANNOTATION_LOGGER;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;
import org.wildfly.extension.core.management.UnstableApiAnnotationResourceDefinition.UnstableApiAnnotationLevel;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.AnnotatedAnnotationUsage;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.AnnotatedClassUsage;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.AnnotatedFieldReference;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.AnnotatedMethodReference;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.AnnotationUsage;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.ClassInfoScanner;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.ExtendsAnnotatedClass;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.ImplementsAnnotatedInterface;

public class ReportUnstableApiAnnotationsProcessor implements DeploymentUnitProcessor {

    private final UnstableApiAnnotationLevel level;

    public ReportUnstableApiAnnotationsProcessor(UnstableApiAnnotationLevel level) {
        this.level = level;
    }

    /**
     * Process this deployment for annotations.  This will use an annotation indexer to create an index of all annotations
     * found in this deployment and attach it to the deployment unit context.
     *
     * @param phaseContext the deployment unit context
     * @throws DeploymentUnitProcessingException
     *
     */
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit du = phaseContext.getDeploymentUnit();
        DeploymentUnit top = du.getParent() == null ? du : du.getParent();
        ClassInfoScanner scanner = top.getAttachment(UnstableApiAnnotationAttachments.UNSTABLE_API_ANNOTATION_SCANNER);
        if (scanner == null) {
            return;
        }

        // ScanExperimentalAnnotationsProcessor has looked for class, interface, method and field usage where those
        // parts have been annotated with an annotation flagged as experimental.
        // The finale part is to check the annotations indexed by Jandex
        CompositeIndex index = du.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        scanner.checkAnnotationIndex(annotationName -> index.getAnnotations(DotName.createSimple(annotationName)));

        Set<AnnotationUsage> usages = scanner.getUsages();

        if (!usages.isEmpty()) {
            AnnotationUsages annotationUsages = AnnotationUsages.parseAndGroup(scanner.getUsages());
            AnnotationUsageReporter reporter = getAnnotationUsageReporter(phaseContext, top);
            if (reporter.isEnabled()) {
                reportAnnotationUsages(top, annotationUsages, reporter);
            }
        }
    }

    private void reportAnnotationUsages(DeploymentUnit top, AnnotationUsages annotationUsages, AnnotationUsageReporter reporter) throws DeploymentUnitProcessingException {
        reporter.header(UNSUPPORTED_ANNOTATION_LOGGER.deploymentContainsUnstableApiAnnotations(top.getName()));
        for (ExtendsAnnotatedClass ext : annotationUsages.extendsAnnotatedClasses) {
            reporter.reportAnnotationUsage(
                    UNSUPPORTED_ANNOTATION_LOGGER.classExtendsClassWithUnstableApiAnnotations(
                            ext.getSourceClass(),
                            ext.getSuperClass(),
                            ext.getAnnotations()));
        }
        for (ImplementsAnnotatedInterface imp : annotationUsages.implementsAnnotatedInterfaces) {
            reporter.reportAnnotationUsage(
                    UNSUPPORTED_ANNOTATION_LOGGER.classImplementsInterfaceWithUnstableApiAnnotations(
                            imp.getSourceClass(),
                            imp.getInterface(),
                            imp.getAnnotations()));
        }
        for (AnnotatedFieldReference ref : annotationUsages.annotatedFieldReferences) {
            reporter.reportAnnotationUsage(
                    UNSUPPORTED_ANNOTATION_LOGGER.classReferencesFieldWithUnstableApiAnnotations(
                            ref.getSourceClass(),
                            ref.getFieldClass(),
                            ref.getFieldName(),
                            ref.getAnnotations()));
        }
        for (AnnotatedMethodReference ref : annotationUsages.annotatedMethodReferences) {
            reporter.reportAnnotationUsage(
                    UNSUPPORTED_ANNOTATION_LOGGER.classReferencesMethodWithUnstableApiAnnotations(
                            ref.getSourceClass(),
                            ref.getMethodClass(),
                            ref.getMethodName(),
                            ref.getDescriptor(),
                            ref.getAnnotations()));
        }
        for (AnnotatedClassUsage ref : annotationUsages.annotatedClassUsages) {
            reporter.reportAnnotationUsage(
                    UNSUPPORTED_ANNOTATION_LOGGER.classReferencesClassWithUnstableApiAnnotations(
                            ref.getSourceClass(),
                            ref.getReferencedClass(),
                            ref.getAnnotations()));
        }
        for (AnnotatedAnnotationUsage ref : annotationUsages.annotatedAnnotationUsages) {
            reporter.reportAnnotationUsage(
                    UNSUPPORTED_ANNOTATION_LOGGER.classUsesAnnotatedAnnotations(
                            ref.getClazz(), ref.getAnnotations()));
        }

        reporter.complete();
    }

    private AnnotationUsageReporter getAnnotationUsageReporter(DeploymentPhaseContext ctx, DeploymentUnit top) throws DeploymentUnitProcessingException {
        if (level == UnstableApiAnnotationLevel.ERROR) {
            return new ErrorAnnotationUsageReporter();
        }
        return new WarningAnnotationUsageReporter();
    }


    private static class AnnotationUsages {


        private final List<ExtendsAnnotatedClass> extendsAnnotatedClasses;
        private final List<ImplementsAnnotatedInterface> implementsAnnotatedInterfaces;
        private final List<AnnotatedFieldReference> annotatedFieldReferences;
        private final List<AnnotatedMethodReference> annotatedMethodReferences;
        private final List<AnnotatedClassUsage> annotatedClassUsages;
        private final List<AnnotatedAnnotationUsage> annotatedAnnotationUsages;
        public AnnotationUsages(List<ExtendsAnnotatedClass> extendsAnnotatedClasses,
                                List<ImplementsAnnotatedInterface> implementsAnnotatedInterfaces,
                                List<AnnotatedFieldReference> annotatedFieldReferences,
                                List<AnnotatedMethodReference> annotatedMethodReferences,
                                List<AnnotatedClassUsage> annotatedClassUsages,
                                List<AnnotatedAnnotationUsage> annotatedAnnotationUsages) {

            this.extendsAnnotatedClasses = extendsAnnotatedClasses;
            this.implementsAnnotatedInterfaces = implementsAnnotatedInterfaces;
            this.annotatedFieldReferences = annotatedFieldReferences;
            this.annotatedMethodReferences = annotatedMethodReferences;
            this.annotatedClassUsages = annotatedClassUsages;
            this.annotatedAnnotationUsages = annotatedAnnotationUsages;
        }

        static AnnotationUsages parseAndGroup(Set<AnnotationUsage> usages) {
            List<ExtendsAnnotatedClass> extendsAnnotatedClasses = new ArrayList<>();
            List<ImplementsAnnotatedInterface> implementsAnnotatedInterfaces = new ArrayList<>();
            List<AnnotatedFieldReference> annotatedFieldReferences = new ArrayList<>();
            List<AnnotatedMethodReference> annotatedMethodReferences = new ArrayList<>();
            List<AnnotatedClassUsage> annotatedClassUsages = new ArrayList<>();
            List<AnnotatedAnnotationUsage> annotatedAnnotationUsages = new ArrayList<>();
            for (AnnotationUsage usage : usages) {
                switch (usage.getType()) {
                    case EXTENDS_CLASS: {
                        ExtendsAnnotatedClass ext = usage.asExtendsAnnotatedClass();
                        extendsAnnotatedClasses.add(ext);
                    }
                    break;
                    case IMPLEMENTS_INTERFACE: {
                        ImplementsAnnotatedInterface imp = usage.asImplementsAnnotatedInterface();
                        implementsAnnotatedInterfaces.add(imp);
                    }
                    break;
                    case FIELD_REFERENCE: {
                        AnnotatedFieldReference ref = usage.asAnnotatedFieldReference();
                        annotatedFieldReferences.add(ref);
                    }
                    break;
                    case METHOD_REFERENCE: {
                        AnnotatedMethodReference ref = usage.asAnnotatedMethodReference();
                        annotatedMethodReferences.add(ref);
                    }
                    break;
                    case CLASS_USAGE: {
                        AnnotatedClassUsage ref = usage.asAnnotatedClassUsage();
                        annotatedClassUsages.add(ref);
                    }
                    break;
                    case ANNOTATED_ANNOTATION_USAGE: {
                        AnnotatedAnnotationUsage ref = usage.asAnnotatedAnnotationUsage();
                        annotatedAnnotationUsages.add(ref);
                    }
                    break;
                }
            }
            extendsAnnotatedClasses.sort(new Comparator<>() {
                @Override
                public int compare(ExtendsAnnotatedClass o1, ExtendsAnnotatedClass o2) {
                    int i = o1.getSourceClass().compareTo(o2.getSourceClass());
                    if (i == 0) {
                        i = o1.getSuperClass().compareTo(o2.getSuperClass());
                    }

                    return i;
                }
            });
            implementsAnnotatedInterfaces.sort(new Comparator<>() {
                @Override
                public int compare(ImplementsAnnotatedInterface o1, ImplementsAnnotatedInterface o2) {
                    int i = o1.getSourceClass().compareTo(o2.getSourceClass());
                    if (i == 0) {
                        i = o1.getInterface().compareTo(o2.getInterface());
                    }

                    return i;
                }
            });
            annotatedFieldReferences.sort(new Comparator<>() {
                @Override
                public int compare(AnnotatedFieldReference o1, AnnotatedFieldReference o2) {
                    int i = o1.getSourceClass().compareTo(o2.getSourceClass());
                    if (i == 0) {
                        i = o1.getFieldClass().compareTo(o2.getFieldClass());
                        if (i == 0) {
                            i = o1.getFieldName().compareTo(o2.getFieldName());
                        }
                    }
                    return i;
                }
            });
            annotatedMethodReferences.sort(new Comparator<>() {
                @Override
                public int compare(AnnotatedMethodReference o1, AnnotatedMethodReference o2) {
                    int i = o1.getSourceClass().compareTo(o2.getSourceClass());
                    if (i == 0) {
                        i = o1.getMethodClass().compareTo(o2.getMethodClass());
                        if (i == 0) {
                            i = o1.getMethodName().compareTo(o2.getMethodName());
                            if (i == 0) {
                                i = o1.getDescriptor().compareTo(o2.getDescriptor());
                            }
                        }
                    }
                    return i;
                }
            });
            annotatedClassUsages.sort(new Comparator<>() {
                @Override
                public int compare(AnnotatedClassUsage o1, AnnotatedClassUsage o2) {
                    int i =  o1.getSourceClass().compareTo(o2.getSourceClass());
                    if (i == 0) {
                        i = o1.getReferencedClass().compareTo(o2.getReferencedClass());
                    }
                    return i;
                }
            });
            annotatedAnnotationUsages.sort(new Comparator<>(){
                @Override
                public int compare(AnnotatedAnnotationUsage o1, AnnotatedAnnotationUsage o2) {
                    return o1.getClazz().compareTo(o2.getClazz());
                }
            });

            return new AnnotationUsages(extendsAnnotatedClasses,
                    implementsAnnotatedInterfaces,
                    annotatedFieldReferences,
                    annotatedMethodReferences,
                    annotatedClassUsages,
                    annotatedAnnotationUsages);
        }
    }



    private interface AnnotationUsageReporter {
        void header(String message);

        void reportAnnotationUsage(String message);

        void complete() throws DeploymentUnitProcessingException;

        boolean isEnabled();
    }

    private class WarningAnnotationUsageReporter implements AnnotationUsageReporter {
        @Override
        public void header(String message) {
            UNSUPPORTED_ANNOTATION_LOGGER.warn(message);
        }

        @Override
        public void reportAnnotationUsage(String message) {
            UNSUPPORTED_ANNOTATION_LOGGER.warn(message);
        }

        @Override
        public void complete() throws DeploymentUnitProcessingException {

        }

        @Override
        public boolean isEnabled() {
            return UNSUPPORTED_ANNOTATION_LOGGER.isEnabled(Logger.Level.WARN);
        }
    }

    private class ErrorAnnotationUsageReporter implements AnnotationUsageReporter {
        private final StringBuilder sb = new StringBuilder();
        @Override
        public void header(String message) {
            sb.append(message);
        }

        @Override
        public void reportAnnotationUsage(String message) {
            sb.append("\n");
            sb.append("-");
            sb.append(message);
        }

        @Override
        public void complete() throws DeploymentUnitProcessingException {
            throw new DeploymentUnitProcessingException(sb.toString());
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }
}