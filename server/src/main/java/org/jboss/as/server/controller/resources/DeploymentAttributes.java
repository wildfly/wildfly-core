/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.controller.resources;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTACHED_STREAMS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_DEPLOYED_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_UNDEPLOYED_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILESYSTEM_PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELATIVE_TO;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UUID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WEB_URL;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.NotificationDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ParameterCorrector;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.access.management.ApplicationTypeAccessConstraintDefinition;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.MinMaxValidator;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.operations.validation.ParametersValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.OperationEntry.Flag;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.as.server.deployment.AbstractDeploymentUnitService;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;


/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class DeploymentAttributes {

    public static final ResourceDescriptionResolver DEPLOYMENT_RESOLVER = ServerDescriptions.getResourceDescriptionResolver(DEPLOYMENT, false);

    //Top level attributes
    public static final SimpleAttributeDefinition NAME = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.NAME, ModelType.STRING, false)
        .setValidator(new StringLengthValidator(1, false))
        .build();

    public static final AttributeDefinition TO_REPLACE = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.TO_REPLACE, NAME).build();

    //For use in resources
    public static final SimpleAttributeDefinition RUNTIME_NAME = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.RUNTIME_NAME, ModelType.STRING, false)
            .setValidator(new StringLengthValidator(1))
            .build();
    //For use in add ops
    public static final SimpleAttributeDefinition RUNTIME_NAME_NILLABLE = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.RUNTIME_NAME, ModelType.STRING, true)
            .setValidator(new StringLengthValidator(1, true))
            .build();

    public static final SimpleAttributeDefinition ENABLED = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.ENABLED, ModelType.BOOLEAN, true)
        .setDefaultValue(ModelNode.FALSE)
        .setAllowExpression(false) // allowing expressions here complicates domain mode and deployment scanners
        .setAttributeMarshaller(new AttributeMarshaller() {

            @Override
            public boolean isMarshallable(AttributeDefinition attribute, ModelNode resourceModel) {
                // Unfortunately, the xsd says default is true while the mgmt API default is false.
                // So, only marshal if the value != 'true'
                return !resourceModel.has(attribute.getName())
                        || resourceModel.get(attribute.getName()).getType() != ModelType.BOOLEAN
                        || !resourceModel.get(attribute.getName()).asBoolean();
            }

            @Override
            public void marshallAsAttribute(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
                ModelNode value = resourceModel.hasDefined(attribute.getName()) ? resourceModel.get(attribute.getName()) : ModelNode.FALSE;
                if (value.getType() != ModelType.BOOLEAN || !value.asBoolean()) {
                    writer.writeAttribute(attribute.getXmlName(), value.asString());
                }
            }
        })
        .build();
    public static final AttributeDefinition PERSISTENT = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.PERSISTENT, ModelType.BOOLEAN, false)
        .build();
    public static final AttributeDefinition OWNER = new SimpleMapAttributeDefinition.Builder(ModelDescriptionConstants.OWNER, ModelType.STRING, true)
            .build();

    public static final AttributeDefinition STATUS = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.STATUS, ModelType.STRING, true)
        .setValidator(new EnumValidator<AbstractDeploymentUnitService.DeploymentStatus>(AbstractDeploymentUnitService.DeploymentStatus.class))
        .build();

    public static final SimpleAttributeDefinition ENABLED_TIME = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.ENABLED_TIME, ModelType.LONG, true)
            .setStorageRuntime()
            .setMeasurementUnit(MeasurementUnit.MILLISECONDS)
            .build();

    public static final SimpleAttributeDefinition ENABLED_TIMESTAMP = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.ENABLED_TIMESTAMP, ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    public static final SimpleAttributeDefinition DISABLED_TIME = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.DISABLED_TIME, ModelType.LONG, true)
            .setStorageRuntime()
            .setMeasurementUnit(MeasurementUnit.MILLISECONDS)
            .build();

    public static final SimpleAttributeDefinition DISABLED_TIMESTAMP = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.DISABLED_TIMESTAMP, ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    public static final SimpleAttributeDefinition MANAGED = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.MANAGED, ModelType.BOOLEAN, false)
            .setStorageRuntime()
            .addFlag(AttributeAccess.Flag.RUNTIME_SERVICE_NOT_REQUIRED)
            .build();

    //Managed content value attributes
    public static final AttributeDefinition EMPTY =
            createContentValueTypeAttribute(ModelDescriptionConstants.EMPTY, ModelType.BOOLEAN, new ModelTypeValidator(ModelType.BOOLEAN, true), false,
                    ModelDescriptionConstants.HASH, ModelDescriptionConstants.INPUT_STREAM_INDEX,
                    ModelDescriptionConstants.BYTES, ModelDescriptionConstants.URL,
                    ModelDescriptionConstants.PATH, ModelDescriptionConstants.RELATIVE_TO)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    public static final SimpleAttributeDefinition CONTENT_INPUT_STREAM_INDEX =
            createContentValueTypeAttribute(ModelDescriptionConstants.INPUT_STREAM_INDEX, ModelType.INT, new StringLengthValidator(1, true), false,
                    ModelDescriptionConstants.HASH, ModelDescriptionConstants.BYTES, ModelDescriptionConstants.URL,
                    ModelDescriptionConstants.PATH, ModelDescriptionConstants.RELATIVE_TO, ModelDescriptionConstants.EMPTY)
            .addArbitraryDescriptor(FILESYSTEM_PATH, ModelNode.TRUE)
            .addArbitraryDescriptor(ATTACHED_STREAMS, ModelNode.TRUE)
            .build();

    public static final SimpleAttributeDefinition CONTENT_HASH =
            createContentValueTypeAttribute(ModelDescriptionConstants.HASH, ModelType.BYTES, new HashValidator(true), false,
                    ModelDescriptionConstants.INPUT_STREAM_INDEX, ModelDescriptionConstants.BYTES, ModelDescriptionConstants.URL,
                    ModelDescriptionConstants.PATH, ModelDescriptionConstants.RELATIVE_TO, ModelDescriptionConstants.EMPTY)
                    .build();
    public static final SimpleAttributeDefinition CONTENT_BYTES =
            createContentValueTypeAttribute(ModelDescriptionConstants.BYTES, ModelType.BYTES, new ModelTypeValidator(ModelType.BYTES, true), false,
                    ModelDescriptionConstants.INPUT_STREAM_INDEX, ModelDescriptionConstants.HASH, ModelDescriptionConstants.URL,
                    ModelDescriptionConstants.PATH, ModelDescriptionConstants.RELATIVE_TO, ModelDescriptionConstants.EMPTY)
                    .build();
    public static final SimpleAttributeDefinition CONTENT_URL =
            createContentValueTypeAttribute(ModelDescriptionConstants.URL, ModelType.STRING, new StringLengthValidator(1, true), false,
                    ModelDescriptionConstants.INPUT_STREAM_INDEX, ModelDescriptionConstants.HASH, ModelDescriptionConstants.BYTES,
                    ModelDescriptionConstants.PATH, ModelDescriptionConstants.RELATIVE_TO, ModelDescriptionConstants.EMPTY)
                    .build();

    //Unmanaged content value attributes
    public static final SimpleAttributeDefinition CONTENT_PATH =
            createContentValueTypeAttribute(ModelDescriptionConstants.PATH, ModelType.STRING, new StringLengthValidator(1, true), false,
                    ModelDescriptionConstants.INPUT_STREAM_INDEX, ModelDescriptionConstants.HASH, ModelDescriptionConstants.BYTES,
                    ModelDescriptionConstants.URL, ModelDescriptionConstants.EMPTY)
                    .setRequires(ModelDescriptionConstants.ARCHIVE)
                    .build();
    public static final SimpleAttributeDefinition CONTENT_RELATIVE_TO =
            createContentValueTypeAttribute(ModelDescriptionConstants.RELATIVE_TO, ModelType.STRING, new StringLengthValidator(1, true), false,
                    ModelDescriptionConstants.INPUT_STREAM_INDEX, ModelDescriptionConstants.HASH, ModelDescriptionConstants.BYTES,
                    ModelDescriptionConstants.URL, ModelDescriptionConstants.EMPTY)
                    .setRequires(ModelDescriptionConstants.PATH)
                    .setRequired(false)
                    .build();

    public static final SimpleAttributeDefinition CONTENT_ARCHIVE =
            createContentValueTypeAttribute(ModelDescriptionConstants.ARCHIVE, ModelType.BOOLEAN, new ModelTypeValidator(ModelType.BOOLEAN), false,
                    ModelDescriptionConstants.INPUT_STREAM_INDEX, ModelDescriptionConstants.BYTES, ModelDescriptionConstants.URL)
                    .setRequires(ModelDescriptionConstants.PATH, ModelDescriptionConstants.HASH, ModelDescriptionConstants.EMPTY)
                    .setRequired(false)
                    .build();

    //Exploded content attributes
    public static final SimpleAttributeDefinition DEPLOYMENT_CONTENT_PATH = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.PATH, ModelType.STRING, true)
            .addArbitraryDescriptor(RELATIVE_TO, ModelNode.TRUE)
            .build();
    public static final SimpleAttributeDefinition TARGET_PATH = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.TARGET_PATH, ModelType.STRING, false)
            .addArbitraryDescriptor(RELATIVE_TO, ModelNode.TRUE)
            .build();
    public static final StringListAttributeDefinition REMOVED_PATHS = new StringListAttributeDefinition.Builder(ModelDescriptionConstants.PATHS)
            .addArbitraryDescriptor(RELATIVE_TO, ModelNode.TRUE)
            .setAllowExpression(true)
            .setRequired(true)
            .build();
    public static final StringListAttributeDefinition UPDATED_PATHS = new StringListAttributeDefinition.Builder(ModelDescriptionConstants.PATH)
            .addArbitraryDescriptor(RELATIVE_TO, ModelNode.TRUE)
            .setAllowExpression(true)
            .setRequired(true)
            .build();
    public static final SimpleAttributeDefinition OVERWRITE = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.OVERWRITE, ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.TRUE)
            .build();
    public static final SimpleAttributeDefinition DEPTH = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.DEPTH, ModelType.INT, true)
            .setDefaultValue(new ModelNode(-1))
            .build();
    public static final SimpleAttributeDefinition ARCHIVE = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.ARCHIVE, ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    /** The complex content  operation parameters */
    public static final ObjectListAttributeDefinition CONTENT_PARAM_ALL =
                ObjectListAttributeDefinition.Builder.of(ModelDescriptionConstants.CONTENT,
                    ObjectTypeAttributeDefinition.Builder.of(ModelDescriptionConstants.CONTENT,
                            CONTENT_INPUT_STREAM_INDEX,
                            CONTENT_HASH,
                            CONTENT_BYTES,
                            CONTENT_URL,
                            CONTENT_PATH,
                            CONTENT_RELATIVE_TO,
                            CONTENT_ARCHIVE,
                            EMPTY)
                            .setRequired(true)
                            .setValidator(new ContentTypeValidator())
                            .build())
                    .setMinSize(1)
                    .setMaxSize(1)
                    .setRequired(true)
                    .setCorrector(ContentListCorrector.INSTANCE)
                    .build();
    public static final ObjectListAttributeDefinition CONTENT_PARAM_ALL_NILLABLE =
            ObjectListAttributeDefinition.Builder.of(ModelDescriptionConstants.CONTENT,
                ObjectTypeAttributeDefinition.Builder.of(ModelDescriptionConstants.CONTENT,
                        SimpleAttributeDefinitionBuilder.create(CONTENT_INPUT_STREAM_INDEX).removeAlternatives(ModelDescriptionConstants.EMPTY).build(),
                        SimpleAttributeDefinitionBuilder.create(CONTENT_HASH).removeAlternatives(ModelDescriptionConstants.EMPTY).build(),
                        SimpleAttributeDefinitionBuilder.create(CONTENT_BYTES).removeAlternatives(ModelDescriptionConstants.EMPTY).build(),
                        SimpleAttributeDefinitionBuilder.create(CONTENT_URL).removeAlternatives(ModelDescriptionConstants.EMPTY).build(),
                        SimpleAttributeDefinitionBuilder.create(CONTENT_PATH).removeAlternatives(ModelDescriptionConstants.EMPTY).build(),
                        SimpleAttributeDefinitionBuilder.create(CONTENT_RELATIVE_TO).removeAlternatives(ModelDescriptionConstants.EMPTY).build(),
                        CONTENT_ARCHIVE)
                        .setValidator(new ContentTypeValidator())
                        .build())
                .setMinSize(1)
                .setMaxSize(1)
                .setRequired(false)
                .setCorrector(ContentListCorrector.INSTANCE)
                .build();
    public static final ObjectListAttributeDefinition CONTENT_PARAM_ALL_EXPLODED =
                ObjectListAttributeDefinition.Builder.of(ModelDescriptionConstants.CONTENT,
                    ObjectTypeAttributeDefinition.Builder.of(ModelDescriptionConstants.CONTENT,
                        SimpleAttributeDefinitionBuilder.create(CONTENT_INPUT_STREAM_INDEX).removeAlternatives(ModelDescriptionConstants.PATH, ModelDescriptionConstants.RELATIVE_TO, ModelDescriptionConstants.EMPTY).build(),
                        SimpleAttributeDefinitionBuilder.create(CONTENT_HASH).removeAlternatives(ModelDescriptionConstants.PATH, ModelDescriptionConstants.RELATIVE_TO, ModelDescriptionConstants.EMPTY).build(),
                        SimpleAttributeDefinitionBuilder.create(CONTENT_BYTES).removeAlternatives(ModelDescriptionConstants.PATH, ModelDescriptionConstants.RELATIVE_TO, ModelDescriptionConstants.EMPTY).build(),
                        SimpleAttributeDefinitionBuilder.create(CONTENT_URL).removeAlternatives(ModelDescriptionConstants.PATH, ModelDescriptionConstants.RELATIVE_TO, ModelDescriptionConstants.EMPTY).build(),
                        TARGET_PATH)
                        .setRequired(true)
                        .build())
                    .setMinSize(1)
                    .setRequired(true)
                    .setValidator(new ManagedContentTypeValidator(ModelDescriptionConstants.TARGET_PATH, ModelDescriptionConstants.OVERWRITE))
                    .setCorrector(ContentListCorrector.INSTANCE)
                    .build();

    // Resource content attributes
    public static final SimpleAttributeDefinition CONTENT_RESOURCE_HASH =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.HASH, ModelType.BYTES)
                    .setValidator(new HashValidator(true))
                    .setAlternatives(ModelDescriptionConstants.PATH, ModelDescriptionConstants.RELATIVE_TO)
                    .build();
    public static final AttributeDefinition CONTENT_RESOURCE_PATH =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.PATH, ModelType.STRING)
                    .setAlternatives(ModelDescriptionConstants.HASH)
                    .build();
    public static final AttributeDefinition CONTENT_RESOURCE_RELATIVE_TO =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.RELATIVE_TO, ModelType.STRING)
                    .setRequired(false)
                    .setRequires(ModelDescriptionConstants.PATH)
                    .setAlternatives(ModelDescriptionConstants.HASH)
                    .build();
    public static final SimpleAttributeDefinition CONTENT_RESOURCE_ARCHIVE =
            createContentValueTypeAttribute(ModelDescriptionConstants.ARCHIVE, ModelType.BOOLEAN, new ModelTypeValidator(ModelType.BOOLEAN), false)
                    .setRequires(ModelDescriptionConstants.PATH, ModelDescriptionConstants.HASH)
                    .setRequired(false)
                    .build();
    public static final ObjectListAttributeDefinition CONTENT_RESOURCE_ALL =
            ObjectListAttributeDefinition.Builder.of(ModelDescriptionConstants.CONTENT,
                    ObjectTypeAttributeDefinition.Builder.of(ModelDescriptionConstants.CONTENT,
                            CONTENT_RESOURCE_HASH,
                            CONTENT_RESOURCE_PATH,
                            CONTENT_RESOURCE_RELATIVE_TO,
                            CONTENT_RESOURCE_ARCHIVE)
                            .setRequired(true)
                            .setValidator(new ContentTypeValidator())
                            .build())
                    .setMinSize(1)
                    .setMaxSize(1)
                    .setRequired(true)
                    .build();


    /** Attributes for server deployment resource */
    public static final AttributeDefinition[] SERVER_RESOURCE_ATTRIBUTES = new AttributeDefinition[] {NAME, RUNTIME_NAME, CONTENT_RESOURCE_ALL, ENABLED, PERSISTENT, OWNER, STATUS, ENABLED_TIME, ENABLED_TIMESTAMP, DISABLED_TIME, DISABLED_TIMESTAMP, MANAGED};

    /** Attributes for server deployment add */
    public static final AttributeDefinition[] SERVER_ADD_ATTRIBUTES = new AttributeDefinition[] { RUNTIME_NAME_NILLABLE, CONTENT_PARAM_ALL, ENABLED};// 'hide' the persistent and owner attributes from users

    /** Attributes for server group deployment add */
    public static final AttributeDefinition[] SERVER_GROUP_RESOURCE_ATTRIBUTES = new AttributeDefinition[] {NAME, RUNTIME_NAME, ENABLED, MANAGED};

    /** Attributes for server group deployment add */
    public static final AttributeDefinition[] SERVER_GROUP_ADD_ATTRIBUTES = new AttributeDefinition[] {RUNTIME_NAME_NILLABLE, ENABLED};

    /** Attributes for domain deployment resource */
    public static final AttributeDefinition[] DOMAIN_RESOURCE_ATTRIBUTES = new AttributeDefinition[] {NAME, RUNTIME_NAME, MANAGED, CONTENT_RESOURCE_ALL};

    /** Attributes for domain deployment add */
    public static final AttributeDefinition[] DOMAIN_ADD_ATTRIBUTES = new AttributeDefinition[] {RUNTIME_NAME_NILLABLE, CONTENT_PARAM_ALL};

    /** Attributes indicating managed deployments in the content attribute */
    public static final Map<String, AttributeDefinition> MANAGED_CONTENT_ATTRIBUTES = createAttributeMap(CONTENT_INPUT_STREAM_INDEX, CONTENT_HASH, CONTENT_BYTES, CONTENT_URL, EMPTY);

    /** Attributes indicating unmanaged deployments in the content attribute */
    public static final Map<String, AttributeDefinition> UNMANAGED_CONTENT_ATTRIBUTES = createAttributeMap(CONTENT_PATH, CONTENT_RELATIVE_TO, CONTENT_ARCHIVE);

    /** Return type for the browse-content operations */
    private static final ObjectTypeAttributeDefinition BROWSE_CONTENT_REPLY = ObjectTypeAttributeDefinition.Builder.of(ModelDescriptionConstants.CONTENT,
                SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.DIRECTORY, ModelType.BOOLEAN, false).build(),
                SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.PATH, ModelType.STRING, false).build(),
                SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.FILE_SIZE, ModelType.LONG, true).setMeasurementUnit(MeasurementUnit.BYTES).build())
            .setRequired(true)
            .build();

    /** All attributes of the content attribute */
    @SuppressWarnings("unchecked")
    public static final Map<String, AttributeDefinition> ALL_CONTENT_ATTRIBUTES = createAttributeMap(MANAGED_CONTENT_ATTRIBUTES, UNMANAGED_CONTENT_ATTRIBUTES);

    public static SimpleAttributeDefinition VERBOSE = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.VERBOSE, ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    public static final OperationDefinition LIST_MODULES = SimpleOperationDefinitionBuilder.of(ModelDescriptionConstants.LIST_MODULES, DEPLOYMENT_RESOLVER)
            .addParameter(VERBOSE)
            .withFlags(Flag.READ_ONLY)
            .build();

    public static final OperationDefinition DEPLOY_DEFINITION = SimpleOperationDefinitionBuilder.of(ModelDescriptionConstants.DEPLOY, DEPLOYMENT_RESOLVER).build();
    public static final OperationDefinition UNDEPLOY_DEFINITION = SimpleOperationDefinitionBuilder.of(ModelDescriptionConstants.UNDEPLOY, DEPLOYMENT_RESOLVER).build();
    public static final OperationDefinition REDEPLOY_DEFINITION = SimpleOperationDefinitionBuilder.of(ModelDescriptionConstants.REDEPLOY, DEPLOYMENT_RESOLVER).build();
    public static final OperationDefinition EXPLODE_DEFINITION = SimpleOperationDefinitionBuilder.of(ModelDescriptionConstants.EXPLODE, DEPLOYMENT_RESOLVER)
            .addParameter(DEPLOYMENT_CONTENT_PATH)
            .withFlag(Flag.DOMAIN_PUSH_TO_SERVERS)
            .build();

    /** Server add deployment definition */
    public static final OperationDefinition SERVER_DEPLOYMENT_ADD_DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.ADD, DEPLOYMENT_RESOLVER)
            .setParameters(SERVER_ADD_ATTRIBUTES)
            .build();
    public static final OperationDefinition DEPLOYMENT_ADD_CONTENT_DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.ADD_CONTENT, DEPLOYMENT_RESOLVER)
            .setParameters(CONTENT_PARAM_ALL_EXPLODED, OVERWRITE)
            .withFlag(Flag.DOMAIN_PUSH_TO_SERVERS)
            .build();
    public static final OperationDefinition DEPLOYMENT_REMOVE_CONTENT_DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.REMOVE_CONTENT, DEPLOYMENT_RESOLVER)
            .setParameters(REMOVED_PATHS)
            .withFlag(Flag.DOMAIN_PUSH_TO_SERVERS)
            .build();
    public static final OperationDefinition DEPLOYMENT_READ_CONTENT_DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.READ_CONTENT, DEPLOYMENT_RESOLVER)
            .setParameters(DEPLOYMENT_CONTENT_PATH)
            .setReplyParameters(new SimpleAttributeDefinitionBuilder(UUID, ModelType.STRING, false).build())
            .withFlags(Flag.READ_ONLY)
            .build();
    public static final OperationDefinition DEPLOYMENT_BROWSE_CONTENT_DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.BROWSE_CONTENT, DEPLOYMENT_RESOLVER)
            .setParameters(DEPLOYMENT_CONTENT_PATH, ARCHIVE, DEPTH)
            .setReplyParameters(BROWSE_CONTENT_REPLY)
            .withFlags(Flag.READ_ONLY)
            .build();

    /** Server group add deployment definition */
    public static final OperationDefinition SERVER_GROUP_DEPLOYMENT_ADD_DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.ADD, DEPLOYMENT_RESOLVER)
            .setParameters(SERVER_GROUP_ADD_ATTRIBUTES)
            .build();

    public static final OperationDefinition DOMAIN_DEPLOYMENT_ADD_DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.ADD, DEPLOYMENT_RESOLVER)
            .setParameters(DOMAIN_ADD_ATTRIBUTES)
            .build();

    /** Return type for the upload-deployment-xxx operaions */
    private static final SimpleAttributeDefinition UPLOAD_HASH_REPLY = SimpleAttributeDefinitionBuilder.create(CONTENT_HASH)
            .setRequired(true)
            .setAlternatives()
            .build();


    //Upload deployment bytes definitions
    public static final AttributeDefinition BYTES_NOT_NULL = SimpleAttributeDefinitionBuilder.create(DeploymentAttributes.CONTENT_BYTES)
            .setRequired(true)
            .build();
    public static final OperationDefinition UPLOAD_BYTES_DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.UPLOAD_DEPLOYMENT_BYTES, DEPLOYMENT_RESOLVER)
            .setParameters(BYTES_NOT_NULL)
            .setReplyParameters(UPLOAD_HASH_REPLY)
            .setRuntimeOnly()
            .addAccessConstraint(ApplicationTypeAccessConstraintDefinition.DEPLOYMENT)
            .build();
    public static final OperationDefinition DOMAIN_UPLOAD_BYTES_DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.UPLOAD_DEPLOYMENT_BYTES, DEPLOYMENT_RESOLVER)
            .setParameters(BYTES_NOT_NULL)
            .setReplyParameters(UPLOAD_HASH_REPLY)
            .withFlag(Flag.MASTER_HOST_CONTROLLER_ONLY)
            .setRuntimeOnly()
            .addAccessConstraint(ApplicationTypeAccessConstraintDefinition.DEPLOYMENT)
            .build();

    //Upload deployment url definitions
    public static final AttributeDefinition URL_NOT_NULL = SimpleAttributeDefinitionBuilder.create(DeploymentAttributes.CONTENT_URL)
            .setRequired(true)
            .addArbitraryDescriptor(WEB_URL, ModelNode.TRUE)
            .build();
    public static final OperationDefinition UPLOAD_URL_DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.UPLOAD_DEPLOYMENT_URL, DEPLOYMENT_RESOLVER)
            .setParameters(URL_NOT_NULL)
            .setReplyParameters(UPLOAD_HASH_REPLY)
            .setRuntimeOnly()
            .addAccessConstraint(ApplicationTypeAccessConstraintDefinition.DEPLOYMENT)
            .build();
    public static final OperationDefinition DOMAIN_UPLOAD_URL_DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.UPLOAD_DEPLOYMENT_URL, DEPLOYMENT_RESOLVER)
            .setParameters(URL_NOT_NULL)
            .setReplyParameters(UPLOAD_HASH_REPLY)
            .withFlag(Flag.MASTER_HOST_CONTROLLER_ONLY)
            .setRuntimeOnly()
            .addAccessConstraint(ApplicationTypeAccessConstraintDefinition.DEPLOYMENT)
            .build();

    //Upload deployment stream definition
    public static final AttributeDefinition INPUT_STREAM_INDEX_NOT_NULL = SimpleAttributeDefinitionBuilder.create(DeploymentAttributes.CONTENT_INPUT_STREAM_INDEX)
            .setRequired(true)
            .build();
    //public static Map<String, AttributeDefinition> UPLOAD_INPUT_STREAM_INDEX_ATTRIBUTES = Collections.singletonMap(INPUT_STREAM_INDEX_NOT_NULL.getName(), INPUT_STREAM_INDEX_NOT_NULL);
    public static final OperationDefinition UPLOAD_STREAM_ATTACHMENT_DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.UPLOAD_DEPLOYMENT_STREAM, DEPLOYMENT_RESOLVER)
            .setParameters(INPUT_STREAM_INDEX_NOT_NULL)
            .setReplyParameters(UPLOAD_HASH_REPLY)
            .setRuntimeOnly()
            .addAccessConstraint(ApplicationTypeAccessConstraintDefinition.DEPLOYMENT)
            .build();
    public static final OperationDefinition DOMAIN_UPLOAD_STREAM_ATTACHMENT_DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.UPLOAD_DEPLOYMENT_STREAM, DEPLOYMENT_RESOLVER)
            .setParameters(INPUT_STREAM_INDEX_NOT_NULL)
            .setReplyParameters(UPLOAD_HASH_REPLY)
            .withFlag(Flag.MASTER_HOST_CONTROLLER_ONLY)
            .setRuntimeOnly()
            .addAccessConstraint(ApplicationTypeAccessConstraintDefinition.DEPLOYMENT)
            .build();

    //Replace deployment definition
    public static final Map<String, AttributeDefinition> REPLACE_DEPLOYMENT_ATTRIBUTES = createAttributeMap(NAME, TO_REPLACE, CONTENT_PARAM_ALL_NILLABLE, RUNTIME_NAME_NILLABLE);
    public static final OperationDefinition REPLACE_DEPLOYMENT_DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.REPLACE_DEPLOYMENT, DEPLOYMENT_RESOLVER)
            .setParameters(REPLACE_DEPLOYMENT_ATTRIBUTES.values().toArray(new AttributeDefinition[REPLACE_DEPLOYMENT_ATTRIBUTES.size()]))
            .build();

    public static final Map<String, AttributeDefinition> SERVER_GROUP_REPLACE_DEPLOYMENT_ATTRIBUTES = createAttributeMap(NAME, TO_REPLACE, RUNTIME_NAME_NILLABLE);
    public static final OperationDefinition SERVER_GROUP_REPLACE_DEPLOYMENT_DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.REPLACE_DEPLOYMENT, DEPLOYMENT_RESOLVER)
            .setParameters(SERVER_GROUP_REPLACE_DEPLOYMENT_ATTRIBUTES.values().toArray(new AttributeDefinition[SERVER_GROUP_REPLACE_DEPLOYMENT_ATTRIBUTES.size()]))
            .build();

    //Full replace deployment definition
    public static final Map<String, AttributeDefinition> FULL_REPLACE_DEPLOYMENT_ATTRIBUTES = createAttributeMap(NAME, RUNTIME_NAME_NILLABLE, CONTENT_PARAM_ALL, ENABLED);
    public static final OperationDefinition FULL_REPLACE_DEPLOYMENT_DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.FULL_REPLACE_DEPLOYMENT, DEPLOYMENT_RESOLVER)
            .setParameters(FULL_REPLACE_DEPLOYMENT_ATTRIBUTES.values().toArray(new AttributeDefinition[FULL_REPLACE_DEPLOYMENT_ATTRIBUTES.size()]))
            .addAccessConstraint(ApplicationTypeAccessConstraintDefinition.DEPLOYMENT)
            .build();

    public static final NotificationDefinition NOTIFICATION_DEPLOYMENT_DEPLOYED = NotificationDefinition.Builder.create(DEPLOYMENT_DEPLOYED_NOTIFICATION, DEPLOYMENT_RESOLVER).build();
    public static final NotificationDefinition NOTIFICATION_DEPLOYMENT_UNDEPLOYED = NotificationDefinition.Builder.create(DEPLOYMENT_UNDEPLOYED_NOTIFICATION, DEPLOYMENT_RESOLVER).build();

    private static final List<String> UNMANAGED_CONTENT_ATTS = Arrays.asList(DeploymentAttributes.CONTENT_PATH.getName(), DeploymentAttributes.CONTENT_RELATIVE_TO.getName());

    public static boolean isUnmanagedContent(ModelNode content) {
        for (String s : UNMANAGED_CONTENT_ATTS) {
            if ((content.hasDefined(s))) {
                return true;
            }
        }
        return false;
    }

    private static SimpleAttributeDefinitionBuilder createContentValueTypeAttribute(String name, ModelType type,
                                                                                    ParameterValidator validator,
                                                                                    boolean allowExpression,
                                                                                    String... alternatives) {
        SimpleAttributeDefinitionBuilder builder = SimpleAttributeDefinitionBuilder.create(name, type, false);
        if (validator != null) {
            builder.setValidator(validator);
        }
        builder.setAllowExpression(allowExpression);
        if (alternatives != null && alternatives.length > 0) {
            builder.setAlternatives(alternatives);
        }
        return builder;
    }

    private static class HashValidator extends ModelTypeValidator implements MinMaxValidator {
        public HashValidator(boolean nillable) {
            super(ModelType.BYTES, nillable);
        }

        @Override
        public Long getMin() {
            return 20L;
        }

        @Override
        public Long getMax() {
            return 20L;
        }
    }

    private static Map<String, AttributeDefinition> createAttributeMap(AttributeDefinition...defs) {
        Map<String, AttributeDefinition> map = new HashMap<String, AttributeDefinition>();
        for (AttributeDefinition def : defs) {
            map.put(def.getName(), def);
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, AttributeDefinition> createAttributeMap(Map<String, AttributeDefinition>...maps) {
        Map<String, AttributeDefinition> map = new HashMap<String, AttributeDefinition>();
        for (Map<String, AttributeDefinition> other : maps) {
            map.putAll(other);
        }
        return Collections.unmodifiableMap(map);
    }

    private static class ContentTypeValidator extends ParametersValidator {

        @Override
        public void validateParameter(String parameterName, ModelNode contentItemNode) throws OperationFailedException {
            for (String key : contentItemNode.keys()){
                boolean managedAttr = true;
                if (CONTENT_ARCHIVE.getName().equals(key)) {
                    continue;
                }
                AttributeDefinition def = MANAGED_CONTENT_ATTRIBUTES.get(key);
                if (def == null) {
                    def = UNMANAGED_CONTENT_ATTRIBUTES.get(key);
                    managedAttr = false;
                }
                if (def != null) {
                    def.validateOperation(contentItemNode);
                    if (contentItemNode.hasDefined(key)) {
                        String[] alts = def.getAlternatives();
                        if (alts != null && alts.length > 0) {
                            for (String alt : alts) {
                                if (contentItemNode.hasDefined(alt)) {
                                    boolean altIsManaged = MANAGED_CONTENT_ATTRIBUTES.containsKey(alt);
                                    if (managedAttr == altIsManaged) {
                                        if (managedAttr) {
                                            throw ServerLogger.ROOT_LOGGER.cannotHaveMoreThanOneManagedContentItem(MANAGED_CONTENT_ATTRIBUTES.keySet());
                                        } else {
                                            // won't happen as the unmanaged attributes don't have unmanaged alternatives
                                            throw new IllegalStateException();
                                        }
                                    } else {
                                        throw ServerLogger.ROOT_LOGGER.cannotMixUnmanagedAndManagedContentItems(Collections.singleton(key), new HashSet<>(Arrays.asList(def.getAlternatives())));
                                    }
                                }
                            }
                        }
                        String[] reqs = def.getRequires();
                        if (reqs != null && reqs.length > 0) {
                            boolean hasReq = false;
                            for (String req : reqs) {
                                if (contentItemNode.hasDefined(req)) {
                                    hasReq = true;
                                    break;
                                }
                            }
                            if (!hasReq) {
                               throw ServerLogger.ROOT_LOGGER.nullParameter(reqs[0]);
                            }
                        }
                    }
                } else {
                    throw ServerLogger.ROOT_LOGGER.unknownContentItemKey(key);
                }
            }
        }
    }

    private static class ManagedContentTypeValidator extends ParametersValidator {
        private final Set<String> ignoredParameters;

        public ManagedContentTypeValidator(String ... ignoredParameters) {
            this.ignoredParameters = new HashSet<>(Arrays.asList(ignoredParameters));
        }

        @Override
        public void validateParameter(String parameterName, ModelNode contentItemNode) throws OperationFailedException {
            for (String key : contentItemNode.keys()) {
                if (ignoredParameters.contains(key)) {
                    continue;
                }
                AttributeDefinition def = MANAGED_CONTENT_ATTRIBUTES.get(key);
                if (def == null) {
                    throw ServerLogger.ROOT_LOGGER.unknownContentItemKey(key);
                }
                def.validateOperation(contentItemNode);
                if (contentItemNode.hasDefined(key)) {
                    String[] alts = def.getAlternatives();
                    if (alts != null && alts.length > 0) {
                        for (String alt : alts) {
                            if (contentItemNode.hasDefined(alt)) {
                                throw ServerLogger.ROOT_LOGGER.cannotHaveMoreThanOneManagedContentItem(MANAGED_CONTENT_ATTRIBUTES.keySet());
                            }
                        }
                    }
                    String[] reqs = def.getRequires();
                    if (reqs != null && reqs.length > 0) {
                        boolean hasReq = false;
                        for (String req : reqs) {
                            if (contentItemNode.hasDefined(req)) {
                                hasReq = true;
                                break;
                            }
                        }
                        if (!hasReq) {
                            throw ServerLogger.ROOT_LOGGER.nullParameter(reqs[0]);
                        }
                    }
                }
            }
        }
    }

    private static class ContentListCorrector implements ParameterCorrector {

        private static final ContentListCorrector INSTANCE = new ContentListCorrector();

        @Override
        public ModelNode correct(ModelNode newValue, ModelNode currentValue) {
            ModelNode result = newValue;
            if (newValue.getType() == ModelType.OBJECT) {
                // WFLY-3184 user probably forgot the list wrapper
                result = new ModelNode();
                result.add(newValue);
            }
            return result;
        }
    }

}
