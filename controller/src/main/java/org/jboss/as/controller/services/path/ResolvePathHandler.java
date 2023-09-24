/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.services.path;

import java.util.Locale;
import java.util.ResourceBundle;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.common.Assert;

/**
 * An operation to resolve a relative-to path.
 * <p/>
 * The operation should be placed on any operation that defines a relative-to path attribute.
 * <p/>
 * Example usage in an extension:
 * <code>
 * <pre>
 *          public class CustomExtension implements Extension {
 *              ...
 *
 *              public void initialize(final ExtensionContext context) {
 *                  final SubsystemRegistration subsystem = context.registerSubsystem(SUBSYSTEM_NAME,
 *                      MANAGEMENT_API_MAJOR_VERSION, MANAGEMENT_API_MINOR_VERSION, MANAGEMENT_API_MICRO_VERSION);
 *                  final ManagementResourceRegistration registration = subsystem.registerSubsystemModel(CustomFileResource.INSTANCE);
 *
 *                  final ResolvePathHandler resolvePathHandler = ResolvePathHandler.Builder.of(context.getPathManager()).build();
 *                  registration.registerOperationHandler(ResolvePathHandler.OPERATION_DEFINITION, resolvePathHandler);
 *
 *                  subsystem.registerXMLElementWriter(CustomSubsystemParser.INSTANCE);
 *              }
 *
 *              ...
 *          }
 *      </pre>
 * </code>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ResolvePathHandler implements OperationStepHandler {

    private static final String OPERATION_NAME = "resolve-path";
    // Attributes
    public static final SimpleAttributeDefinition RELATIVE_TO_ONLY = SimpleAttributeDefinitionBuilder.create("relative-to-only", ModelType.BOOLEAN, true).build();

    private final AttributeDefinition parentAttribute;
    private final AttributeDefinition relativeToAttribute;
    private final AttributeDefinition pathAttribute;
    private final OperationDefinition operationDefinition;
    private final PathManager pathManager;
    private final boolean checkAbsolutePath;

    private ResolvePathHandler(final OperationDefinition operationDefinition, final AttributeDefinition parentAttribute,
                               final AttributeDefinition relativeToAttribute, final AttributeDefinition pathAttribute,
                               final PathManager pathManager, final boolean checkAbsolutePath) {
        this.parentAttribute = parentAttribute;
        this.relativeToAttribute = relativeToAttribute;
        this.pathAttribute = pathAttribute;
        this.operationDefinition = operationDefinition;
        this.pathManager = pathManager;
        this.checkAbsolutePath = checkAbsolutePath;
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        // Get the resource
        final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
        final ModelNode model = resource.getModel();

        // Validate the operation
        final ModelNode relativeToOnly = RELATIVE_TO_ONLY.validateOperation(operation);
        final boolean resolveRelativeToOnly = relativeToOnly.asBoolean(false);

        // Resolve the model values
        final ModelNode file = (parentAttribute != null ? parentAttribute.resolveModelAttribute(context, model) : model);
        final ModelNode relativeTo = relativeToAttribute.resolveModelAttribute(context, file);
        final ModelNode path = pathAttribute.resolveModelAttribute(context, file);

        // Resolve paths
        final String result;

        if (checkAbsolutePath
                && path.isDefined()
                && AbsolutePathService.isAbsoluteUnixOrWindowsPath(path.asString())) {
                result = pathManager.resolveRelativePathEntry(path.asString(), null);
        } else if (relativeTo.isDefined()) {
            // If resolving the full path and a path is defined
            if (!resolveRelativeToOnly && path.isDefined()) {
                result = pathManager.resolveRelativePathEntry(path.asString(), relativeTo.asString());
            } else {
                result = pathManager.getPathEntry(relativeTo.asString()).resolvePath();
            }
        } else if (path.isDefined()) {
            result = pathManager.resolveRelativePathEntry(path.asString(), null);
        } else {
            throw ControllerLogger.ROOT_LOGGER.noPathToResolve(relativeToAttribute.getName(), pathAttribute.getName(), model);
        }
        context.getResult().set(new ModelNode(result));
        context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
    }

    /**
     * Returns the operation definition for the operation.
     *
     * @return the operation definition
     */
    public OperationDefinition getOperationDefinition() {
        return operationDefinition;
    }

    /**
     * Build an operation to resolve the
     */
    public static class Builder {

        private final PathManager pathManager;
        private final String operationName;
        private AttributeDefinition parentAttribute;
        private AttributeDefinition relativeToAttribute;
        private AttributeDefinition pathAttribute;
        private ModelVersion deprecatedSince;
        private boolean checkAbsolutePath = false;

        private Builder(String operationName, final PathManager pathManager) {
            this.operationName = operationName;
            this.pathManager = pathManager;
        }

        /**
         * Creates a builder with the default operation name of {@code resolve-path}.
         *
         * @param pathManager the path manager used to resolve the path
         *
         * @return the operation handler builder
         */
        public static Builder of(final PathManager pathManager) {
            Assert.checkNotNullParam("pathManager", pathManager);
            return new Builder(OPERATION_NAME, pathManager);
        }

        /**
         * Creates a builder with the default operation name of defined in the {@code operationName} parameter.
         * <p/>
         * While this seems odd to add a deprecated method from the start, the transaction extension requires a
         * separate operation as there are two relative paths. Other extensions should not use this method and if the
         * transaction subsystem changes to use a proper resource for the {@code object-store}, this method should be
         * removed.
         *
         * @param operationName the name of the operation to register
         * @param pathManager   the path manager used to resolve the path
         *
         * @return the operation handler builder
         */
        @Deprecated
        public static Builder of(final String operationName, final PathManager pathManager) {
            Assert.checkNotNullParam("operationName", operationName);
            Assert.checkNotNullParam("pathManager", pathManager);
            return new Builder(operationName, pathManager);
        }

        /**
         * Builds the resolve path handler.
         *
         * @return the operation step handler
         */
        public ResolvePathHandler build() {
            if (relativeToAttribute == null) relativeToAttribute = PathResourceDefinition.RELATIVE_TO;
            if (pathAttribute == null) pathAttribute = PathResourceDefinition.PATH;
            final SimpleOperationDefinitionBuilder builder = new SimpleOperationDefinitionBuilder(operationName, new ResolvePathResourceDescriptionResolver(operationName))
                    .addParameter(ResolvePathHandler.RELATIVE_TO_ONLY)
                    .setReplyType(ModelType.STRING)
                    .setReadOnly()
                    .setRuntimeOnly();
            if (deprecatedSince != null) {
                builder.setDeprecated(deprecatedSince);
            }
            return new ResolvePathHandler(builder.build(), parentAttribute, relativeToAttribute, pathAttribute, pathManager, checkAbsolutePath);
        }

        /**
         * Sets the parent attribute that the {@code relative-to} and {@code path} attributes are children of. A value
         * of {@code null} indicates they are a direct decedent of the resource.
         *
         * @param parentAttribute the parent attribute
         *
         * @return the builder
         */
        public Builder setParentAttribute(final AttributeDefinition parentAttribute) {
            this.parentAttribute = parentAttribute;
            return this;
        }

        /**
         * Sets the {@code relative-to} attribute. The default value is {@link PathResourceDefinition#RELATIVE_TO}.
         *
         * @param relativeToAttribute the relative to attribute
         *
         * @return the builder
         */
        public Builder setRelativeToAttribute(final AttributeDefinition relativeToAttribute) {
            this.relativeToAttribute = relativeToAttribute;
            return this;
        }

        /**
         * Sets the {@code path} attribute. The default value is {@link PathResourceDefinition#PATH}.
         *
         * @param pathAttribute the path to attribute
         *
         * @return the builder
         */
        public Builder setPathAttribute(final AttributeDefinition pathAttribute) {
            this.pathAttribute = pathAttribute;
            return this;
        }

        public Builder setDeprecated(ModelVersion since) {
            this.deprecatedSince = since;
            return this;
        }

        /**
         * Sets whether the path is absolute and should ignore the relative-to value.
         *
         * @param checkAbsolutePath {code true} if an absolute path should ignore the relative-to value
         * @return the builder
         */
        public Builder setCheckAbsolutePath(final boolean checkAbsolutePath) {
            this.checkAbsolutePath = checkAbsolutePath;
            return this;
        }
    }

    private static class ResolvePathResourceDescriptionResolver extends StandardResourceDescriptionResolver {

        private final String operationName;

        public ResolvePathResourceDescriptionResolver(final String operationName) {
            super(ModelDescriptionConstants.PATH, ControllerResolver.RESOURCE_NAME, ResolvePathHandler.class.getClassLoader(), false, false);
            this.operationName = operationName;
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
                return bundle.getString(getKey(REPLY));
            }
            return super.getOperationReplyDescription(operationName, locale, bundle);
        }

        private String getKey() {
                return String.format("%s.%s", ModelDescriptionConstants.PATH, OPERATION_NAME);
            }

        private String getKey(final String key) {
                return String.format("%s.%s.%s", ModelDescriptionConstants.PATH, OPERATION_NAME, key);
            }
    }


}
