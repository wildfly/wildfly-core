/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.services.path;

import static org.jboss.as.controller.client.helpers.MeasurementUnit.BITS;
import static org.jboss.as.controller.client.helpers.MeasurementUnit.BYTES;
import static org.jboss.as.controller.client.helpers.MeasurementUnit.GIGABITS;
import static org.jboss.as.controller.client.helpers.MeasurementUnit.GIGABYTES;
import static org.jboss.as.controller.client.helpers.MeasurementUnit.KILOBITS;
import static org.jboss.as.controller.client.helpers.MeasurementUnit.KILOBYTES;
import static org.jboss.as.controller.client.helpers.MeasurementUnit.MEGABITS;
import static org.jboss.as.controller.client.helpers.MeasurementUnit.MEGABYTES;
import static org.jboss.as.controller.client.helpers.MeasurementUnit.PETABITS;
import static org.jboss.as.controller.client.helpers.MeasurementUnit.PETABYTES;
import static org.jboss.as.controller.client.helpers.MeasurementUnit.TERABITS;
import static org.jboss.as.controller.client.helpers.MeasurementUnit.TERABYTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNIT;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.as.controller.AbstractRuntimeOnlyHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Handler for file usage metric which contains the total size of a folder and the usable space (as in Java nio).
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 *
 * @see java.nio.file.FileStore#getUsableSpace()
 */
public class PathInfoHandler extends AbstractRuntimeOnlyHandler {

    private static volatile DateTimeFormatter DATE_FORMAT;
    private static volatile ZoneId ZONE_ID ;
    private static final String USED_SPACE = "used-space";
    private static final String AVAILABLE_SPACE = "available-space";
    private static final String RESOLVED_PATH = "resolved-path";
    private static final String LAST_MODIFIED = "last-modified";
    private static final String CREATION_TIME = "creation-time";
    private static final String OPERATION_NAME = "path-info";
    private static final String FILESYSTEM = "filesystem";

    private static final AttributeDefinition UNIT_ATTRIBUTE = SimpleAttributeDefinitionBuilder
                        .create(UNIT, ModelType.STRING, true)
                        .setAllowedValues(BYTES.getName(), KILOBYTES.getName(), MEGABYTES.getName(), GIGABYTES.getName(),
                                TERABYTES.getName(), PETABYTES.getName(), BITS.getName(), KILOBITS.getName(),
                                MEGABITS.getName(), GIGABITS.getName(), TERABITS.getName(), PETABITS.getName())
                        .setDefaultValue(new ModelNode(MeasurementUnit.BYTES.getName()))
                        .build();

    private static DateTimeFormatter getDateFormat() {
        if (DATE_FORMAT == null) {
            DATE_FORMAT = new DateTimeFormatterBuilder().appendInstant().appendZoneId().toFormatter(Locale.ENGLISH);
        }
        return DATE_FORMAT;
    }

    private static ZoneId getZoneId() {
        if (ZONE_ID == null) {
            ZONE_ID = ZoneId.systemDefault();
        }
        return ZONE_ID;
    }

    private final List<RelativePathSizeAttribute> relativePathAttributes;
    private final PathManager pathManager;
    private final AttributeDefinition parentAttribute;

    private PathInfoHandler(final PathManager pathManager, final AttributeDefinition parentAttribute, final List<RelativePathSizeAttribute> relativePathAttributes) {
        this.relativePathAttributes = relativePathAttributes;
        this.parentAttribute = parentAttribute;
        this.pathManager = pathManager;
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return true;
    }

    /**
     * Compute the file usage metric which contains the total size of a folder and the usable space (as in Java nio).
     * @throws org.jboss.as.controller.OperationFailedException
     * @see java.nio.file.FileStore#getUsableSpace()
     */
    @Override
    protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
        ModelNode unitModelNode = UNIT_ATTRIBUTE.resolveModelAttribute(context, operation);
        MeasurementUnit sizeUnit = MeasurementUnit.BYTES;
        if (unitModelNode.isDefined()) {
            try {
                sizeUnit = MeasurementUnit.valueOf(unitModelNode.asString());
            } catch (IllegalArgumentException ex) {
            }
        }
        // Get the resource
        final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
        final ModelNode model = resource.getModel();
        final ModelNode metric = new ModelNode();
        for(RelativePathSizeAttribute relativePathAttribute : relativePathAttributes) {
            String replyParameterName = relativePathAttribute.pathAttribute.getName();
            final ModelNode relativeTo;
            final ModelNode path;
            try {
                // Resolve the model values
                relativeTo = readAttributeValue(context, relativePathAttribute.relativeToAttribute);
                path = readAttributeValue(context, relativePathAttribute.pathAttribute);
            } catch (OperationFailedException ex) {
                return;
            }
            // Resolve paths
            final String result;
            if (relativeTo.isDefined()) {
                // If resolving the full path and a path is defined
                if (path.isDefined()) {
                    result = pathManager.resolveRelativePathEntry(path.asString(), relativeTo.asString());
                } else {
                    result = pathManager.getPathEntry(relativeTo.asString()).resolvePath();
                }
            } else if (path.isDefined()) {
                if (pathManager != null) {
                    result = pathManager.resolveRelativePathEntry(path.asString(), null);
                } else {
                    result = path.asString();
                }
            } else {
                throw ControllerLogger.ROOT_LOGGER.noPathToResolve(relativePathAttribute.relativeToAttribute.getName(),
                        replyParameterName, model);
            }
            Double offset = MeasurementUnit.calculateOffset(MeasurementUnit.BYTES, sizeUnit);
            try {
                Path folder = new File(result).toPath();
                PathSizeWalker walker = new PathSizeWalker();
                Files.walkFileTree(folder, walker);
                ModelNode replyParameterNode;
                if (this.parentAttribute != null) {
                    replyParameterNode = metric.get(parentAttribute.getName()).get(replyParameterName);
                } else {
                    replyParameterNode = metric.get(replyParameterName);
                }
                replyParameterNode.get(USED_SPACE).set(new ModelNode(offset * walker.getSize().doubleValue()));
                if (Files.exists(folder)) {
                    BasicFileAttributes attributes = Files.getFileAttributeView(folder, BasicFileAttributeView.class).readAttributes();
                    replyParameterNode.get(RESOLVED_PATH).set(folder.toAbsolutePath().toString());
                    DateTimeFormatter formatter = getDateFormat();
                    ZoneId zoneId = getZoneId();
                    replyParameterNode.get(CREATION_TIME).set(formatter.format(attributes.creationTime().toInstant().atZone(zoneId)));
                    replyParameterNode.get(LAST_MODIFIED).set(formatter.format(attributes.lastModifiedTime().toInstant().atZone(zoneId)));
                    replyParameterNode.get(AVAILABLE_SPACE).set(new ModelNode(offset * Files.getFileStore(folder).getUsableSpace()));
                }
            } catch (IOException ex) {
                throw new OperationFailedException(ex);
            }
        }
        context.getResult().set(metric);
    }

    private ModelNode readAttributeValue(OperationContext context, AttributeDefinition attribute) throws OperationFailedException {
        final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
        ModelNode model = resource.getModel();
        if(this.parentAttribute != null && !this.parentAttribute.equals(attribute)) {
            model = readAttributeValue(context, this.parentAttribute);
        }
        final String attributeName = attribute.getName();
        if(model.hasDefined(attributeName)) {
            return attribute.resolveModelAttribute(context, model);
        }
        AttributeAccess access = context.getResourceRegistration().getAttributeAccess(PathAddress.EMPTY_ADDRESS, attributeName);
        if(access == null) {
            return new ModelNode();
        }
        OperationStepHandler handler = access.getReadHandler();
        ModelNode path;
        if(handler != null) {
            ClassLoader oldTccl = WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(handler.getClass());
            try {
                handler.execute(context, Util.getReadAttributeOperation(context.getCurrentAddress(), attributeName));
            } finally {
                WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(oldTccl);
            }
            path = context.getResult().clone();
            context.getResult().setEmptyObject();
        } else {
            path = new ModelNode();
        }
        return path;
    }

    private class PathSizeWalker implements FileVisitor<Path> {

        private final AtomicLong size;

        private PathSizeWalker() {
            size = new AtomicLong(0);
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (attrs.isRegularFile()) {
                size.addAndGet(attrs.size());
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        /**
         * Return the size of the Path walked in bytes.
         * @return the size of the Path walked in bytes.
         */
        public AtomicLong getSize() {
            return size;
        }

    }

    public static void registerOperation(final ManagementResourceRegistration resourceRegistration, final PathInfoHandler handler) {
        registerOperation(resourceRegistration, MeasurementUnit.BYTES, handler);
    }

    private static void registerOperation(final ManagementResourceRegistration resourceRegistration,
            final MeasurementUnit unit, final PathInfoHandler handler) {
        List<AttributeDefinition> replyParameters = new ArrayList<>();
        for (RelativePathSizeAttribute att : handler.relativePathAttributes) {
            ObjectTypeAttributeDefinition build = ObjectTypeAttributeDefinition.Builder.of(att.pathAttribute.getName(),
                SimpleAttributeDefinitionBuilder.create(USED_SPACE, ModelType.DOUBLE, false)
                    .setUndefinedMetricValue(new ModelNode(0d))
                    .setMeasurementUnit(unit)
                    .setStorageRuntime()
                    .build(),
                SimpleAttributeDefinitionBuilder.create(CREATION_TIME, ModelType.STRING, false)
                    .setStorageRuntime()
                    .build(),
                SimpleAttributeDefinitionBuilder.create(LAST_MODIFIED, ModelType.STRING, false)
                    .setStorageRuntime()
                    .build(),
                SimpleAttributeDefinitionBuilder.create(RESOLVED_PATH, ModelType.STRING, false)
                    .setStorageRuntime()
                    .build(),
                SimpleAttributeDefinitionBuilder.create(AVAILABLE_SPACE, ModelType.DOUBLE, false)
                    .setMeasurementUnit(unit)
                    .setStorageRuntime()
                    .build())
                .build();
            replyParameters.add(build);
        }
        OperationDefinition operation = new SimpleOperationDefinitionBuilder(OPERATION_NAME, new DiskUsagePathResourceDescriptionResolver(OPERATION_NAME, replyParameters))
                .addParameter(UNIT_ATTRIBUTE)
                .setReadOnly()
                .setRuntimeOnly()
                .setReplyType(ModelType.OBJECT)
                .setReplyParameters(replyParameters.toArray(new AttributeDefinition[replyParameters.size()]))
                .build();
        resourceRegistration.registerOperationHandler(operation, handler);
    }

    public static class Builder {

        private final List<RelativePathSizeAttribute> attributes = new ArrayList<>();
        private AttributeDefinition parentAttribute = null;
        private final PathManager pathManager;

        private Builder(PathManager pathManager) {
            this.pathManager = pathManager;
        }

        public static Builder of(final PathManager pathManager) {
            return new Builder(pathManager);
        }

        public Builder setParentAttribute(final AttributeDefinition parentAttribute) {
            this.parentAttribute = parentAttribute;
            return this;
        }

        public Builder addAttribute(final AttributeDefinition pathAttribute, final AttributeDefinition relativeToAttribute) {
            attributes.add(new RelativePathSizeAttribute(pathAttribute, relativeToAttribute));
            return this;
        }

        public PathInfoHandler build() {
            if (attributes.isEmpty()) {
                attributes.add(new RelativePathSizeAttribute(null, null));
            }
            return new PathInfoHandler(pathManager, parentAttribute, attributes);
        }
    }

    private static class RelativePathSizeAttribute {

        private final AttributeDefinition relativeToAttribute;
        private final AttributeDefinition pathAttribute;

        RelativePathSizeAttribute(final AttributeDefinition pathAttribute, final AttributeDefinition relativeToAttribute) {
            if (relativeToAttribute == null) {
                this.relativeToAttribute = PathResourceDefinition.RELATIVE_TO;
            } else {
                this.relativeToAttribute = relativeToAttribute;
            }
            if (pathAttribute == null) {
                this.pathAttribute = PathResourceDefinition.PATH;
            } else {
                this.pathAttribute = pathAttribute;
            }
        }
    }

    private static class DiskUsagePathResourceDescriptionResolver extends StandardResourceDescriptionResolver {

        private final String operationName;
        private final Set<String> replyParameterKeys;

        public DiskUsagePathResourceDescriptionResolver(final String operationName, List<AttributeDefinition> replyParameters) {
            super(FILESYSTEM, ControllerResolver.RESOURCE_NAME, ResolvePathHandler.class.getClassLoader(), false, false);
            this.operationName = operationName;
            Set<String> set = new HashSet<>();
            for (AttributeDefinition replyParameter : replyParameters) {
                String name = replyParameter.getName();
                set.add(name);
            }
            replyParameterKeys = set;
        }

        @Override
        public String getOperationDescription(String operationName, Locale locale, ResourceBundle bundle) {
            if (this.operationName.equals(operationName)) {
                return bundle.getString(getKey());
            }
            return super.getOperationParameterDescription(operationName, operationName, locale, bundle);
        }

        @Override
        public String getOperationParameterDescription(final String operationName, final String paramName, final Locale locale, final ResourceBundle bundle) {
            if (this.operationName.equals(operationName)) {
                return bundle.getString(getKey(paramName));
            }
            return super.getOperationParameterDescription(operationName, paramName, locale, bundle);
        }

        @Override
        public String getOperationReplyDescription(String operationName, Locale locale, ResourceBundle bundle) {
            if (this.operationName.equals(operationName)) {
                return bundle.getString(getKey(PATH));
            }
            return super.getOperationReplyDescription(operationName, locale, bundle);
        }

        @Override
        public String getResourceAttributeDescription(String attributeName, Locale locale, ResourceBundle bundle) {
            String attribute = attributeName;
            if(attributeName.endsWith(USED_SPACE)) {
                String key = attributeName.substring(0, attributeName.length() - USED_SPACE.length() -1);
                if(replyParameterKeys.contains(key)) {
                    attribute = PATH + '.' +  USED_SPACE;
                }
            } else if (attributeName.endsWith(AVAILABLE_SPACE)) {
                String key = attributeName.substring(0, attributeName.length() - AVAILABLE_SPACE.length() -1);
                if(replyParameterKeys.contains(key)) {
                    attribute = PATH + '.' +  AVAILABLE_SPACE;
                }
            } else if (attributeName.endsWith(CREATION_TIME)) {
                String key = attributeName.substring(0, attributeName.length() - CREATION_TIME.length() -1);
                if(replyParameterKeys.contains(key)) {
                    attribute = PATH + '.' +  CREATION_TIME;
                }
            } else if (attributeName.endsWith(RESOLVED_PATH)) {
                String key = attributeName.substring(0, attributeName.length() - RESOLVED_PATH.length() -1);
                if(replyParameterKeys.contains(key)) {
                    attribute = PATH + '.' +  RESOLVED_PATH;
                }
            } else if (attributeName.endsWith(LAST_MODIFIED)) {
                String key = attributeName.substring(0, attributeName.length() - LAST_MODIFIED.length() -1);
                if(replyParameterKeys.contains(key)) {
                    attribute = PATH + '.' +  LAST_MODIFIED;
                }
            }
            return super.getResourceAttributeDescription(attribute, locale, bundle);
        }


        private String getKey() {
            return String.format("%s.%s", FILESYSTEM, OPERATION_NAME);
        }

        private String getKey(final String key) {
            if(replyParameterKeys.contains(key)) {
                return String.format("%s.%s.%s", FILESYSTEM, OPERATION_NAME, PATH);
            }
            return String.format("%s.%s.%s", FILESYSTEM, OPERATION_NAME, key);
        }
    }
}
