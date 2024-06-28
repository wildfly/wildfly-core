/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.persistence.yaml;

import static org.jboss.as.controller.client.impl.AdditionalBootCliScriptInvoker.CLI_SCRIPT_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BYTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EMPTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.URL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;
import static org.jboss.as.controller.logging.ControllerLogger.ROOT_LOGGER;
import static org.jboss.dmr.ModelType.LIST;
import static org.jboss.dmr.ModelType.OBJECT;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.as.controller.MapAttributeDefinition;
import org.jboss.as.controller.ObjectMapAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.ParsedBootOp;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.persistence.ConfigurationExtension;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.Construct;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 *
 * @author Emmanuel Hugonnet (c) 2021 Red Hat, Inc.
 */
public class YamlConfigurationExtension implements ConfigurationExtension {

    private static final String CONFIGURATION_ROOT_KEY = "wildfly-configuration";
    private static final String YAML_CODEPOINT_LIMIT = "org.wildfly.configuration.extension.yaml.codepoint.limit";

    private static final String YAML_CONFIG = "--yaml";
    private static final String SHORT_YAML_CONFIG = "-y";

    private boolean needReload;
    private Path[] files;
    private final List<Map<String, Object>> configs = new ArrayList<>();
    private final Map<String, Object> deployments = new LinkedHashMap<>();
    private static final String[] EXCLUDED_ELEMENTS = {"deployment", "extension", "deployment-overlay", "path"};
    public static final Set<String> MANAGED_CONTENT_ATTRIBUTES = Set.of(INPUT_STREAM_INDEX, HASH, BYTES, URL, EMPTY);

    @SuppressWarnings("unchecked")
    public YamlConfigurationExtension() {
    }

    @Override
    public ConfigurationExtension load(Path... files) {
        this.files = files;
        load();
        return this;
    }

    @SuppressWarnings("unchecked")
    private void load() {
        long start = System.currentTimeMillis();
        List<String> parsedFiles = new ArrayList<>();
        for (Path file : files) {
            if (file != null && Files.exists(file) && Files.isRegularFile(file)) {
                Map<String, Object> yamlConfig = Collections.emptyMap();
                LoaderOptions loadingConfig = new LoaderOptions();
                //Default to 3MB
                loadingConfig.setCodePointLimit(
                        Integer.parseInt(
                                WildFlySecurityManager.getPropertyPrivileged(YAML_CODEPOINT_LIMIT, "3145728")));
                Yaml yaml = new Yaml(new OperationConstructor(loadingConfig));
                try (InputStream inputStream = Files.newInputStream(file)) {
                    yamlConfig = yaml.load(inputStream);
                } catch (IOException ioex) {
                    throw MGMT_OP_LOGGER.failedToParseYamlConfigurationFile(file.toAbsolutePath().toString(), ioex);
                }
                if (yamlConfig.containsKey(CONFIGURATION_ROOT_KEY)) {
                    Map<String, Object> config = (Map<String, Object>) yamlConfig.get(CONFIGURATION_ROOT_KEY);
                    for (String excluded : EXCLUDED_ELEMENTS) {
                        boolean isPresent = config.containsKey(excluded);
                        if (isPresent) {
                            Object value = config.remove(excluded);
                            if (value instanceof Map && DEPLOYMENT.equals(excluded)) {
                                deployments.putAll((Map<String, Object>) value);
                            } else {
                                String message = MGMT_OP_LOGGER.ignoreYamlElement(excluded);
                                if (value != null) {
                                    message = message + MGMT_OP_LOGGER.ignoreYamlSubElement(yaml.dump(value).trim());
                                }
                                MGMT_OP_LOGGER.warn(message);
                            }
                        }
                    }
                    parsedFiles.add(file.toAbsolutePath().toString());
                    this.configs.add(config);
                }
            } else {
                throw MGMT_OP_LOGGER.missingYamlFile(file != null ? file.toAbsolutePath().toString() : "");
            }
        }
        MGMT_OP_LOGGER.loadingYamlFiles(System.currentTimeMillis() - start, String.join(",", parsedFiles));
        this.needReload = false;
    }

    @Override
    public boolean shouldProcessOperations(RunningModeControl runningModeControl) {
        return (!this.configs.isEmpty() || (needReload && this.files.length > 0))
                && (RunningMode.ADMIN_ONLY != runningModeControl.getRunningMode() || null == WildFlySecurityManager.getPropertyPrivileged(CLI_SCRIPT_PROPERTY, null))
                && (!runningModeControl.isReloaded() || runningModeControl.isApplyConfigurationExtension());
    }

    @SuppressWarnings("unchecked")
    @Override
    public void processOperations(ImmutableManagementResourceRegistration rootRegistration, List<ParsedBootOp> postExtensionOps) {
        if (needReload) {
            load();
        }
        MGMT_OP_LOGGER.debug("We are applying YAML files to the configuration");
        Map<PathAddress, ParsedBootOp> xmlOperations = new HashMap<>();
        for (ParsedBootOp op : postExtensionOps) {
            if (op.getChildOperations().isEmpty()) {
                xmlOperations.put(op.getAddress(), op);
            } else {
                for (ModelNode childOp : op.getChildOperations()) {
                    ParsedBootOp subOp = new ParsedBootOp(childOp, null);
                    xmlOperations.put(subOp.getAddress(), subOp);
                }
            }
        }
        for (Map<String, Object> config : configs) {
            processResource(PathAddress.EMPTY_ADDRESS, new HashMap<>(config), rootRegistration, rootRegistration, xmlOperations, postExtensionOps, false);
        }
        for (Map.Entry<String, Object> deployment : deployments.entrySet()) {
            processUnmanagedDeployments(rootRegistration, deployment, xmlOperations, postExtensionOps);
        }
        this.configs.clear();
        needReload = true;
    }

    @SuppressWarnings("unchecked")
    private void processResource(PathAddress parentAddress, Map<String, Object> yaml, ImmutableManagementResourceRegistration rootRegistration, ImmutableManagementResourceRegistration resourceRegistration, Map<PathAddress, ParsedBootOp> xmlOperations, List<ParsedBootOp> postExtensionOps, boolean placeHolder) {
        for (String name : yaml.keySet()) {
            if (resourceRegistration.getChildNames(PathAddress.EMPTY_ADDRESS).contains(name) || placeHolder) {
                // we are going into a child resource
                PathAddress address;
                if (placeHolder) {
                    address = parentAddress.getParent().append(parentAddress.getLastElement().getKey(), name);
                } else {
                    address = parentAddress.append(name);
                }
                Object value = yaml.get(name);
                if (value instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>) value;
                    ImmutableManagementResourceRegistration childResourceRegistration = rootRegistration.getSubModel(address);
                    if (childResourceRegistration != null) {
                        processResource(address, map, rootRegistration, childResourceRegistration, xmlOperations, postExtensionOps, false);
                    } else {
                        if (placeHolder) {
                            MGMT_OP_LOGGER.noResourceRegistered(address.toCLIStyleString());
                        } else {
                            MGMT_OP_LOGGER.debugf("No registration found for address %s", address.toCLIStyleString());
                            processResource(address, map, rootRegistration, resourceRegistration, xmlOperations, postExtensionOps, true);
                        }
                    }
                } else {
                    if (value == null && !isExistingResource(xmlOperations, address)) { //empty resource
                        OperationEntry operationEntry = rootRegistration.getOperationEntry(address, ADD);
                        if (operationEntry != null) {
                            processAttributes(address, rootRegistration, operationEntry, Collections.emptyMap(), postExtensionOps, xmlOperations);
                        } else {
                            throw MGMT_OP_LOGGER.missingOperationForResource("ADD", address.toCLIStyleString());
                        }
                    } else if (value != null && value instanceof Operation) {
                        Operation yamlOperation = Operation.class.cast(value);
                        if (isExistingResource(xmlOperations, address)) {
                            yamlOperation.processOperation(rootRegistration, xmlOperations, postExtensionOps, address, name);
                        } else if (yamlOperation instanceof RemoveOperation) {
                            MGMT_OP_LOGGER.removingUnexistingResource(address.toCLIStyleString());
                        } else {
                            if (yamlOperation instanceof UndefineOperation) {
                                throw MGMT_OP_LOGGER.noResourceForUndefiningAttribute(name, address.toCLIStyleString());
                            }
                            throw MGMT_OP_LOGGER.illegalOperationForAttribute(yamlOperation.getOperationName(), name, address.toCLIStyleString());
                        }
                    } else {
                        if (!isExistingResource(xmlOperations, address)) {
                            if (resourceRegistration.getAttributeNames(PathAddress.EMPTY_ADDRESS).contains(name)) {
                                MGMT_OP_LOGGER.noAttributeValueDefined(name, address.toCLIStyleString());
                            } else {
                                MGMT_OP_LOGGER.noAttributeSetForAddress(address.toCLIStyleString());
                            }
                        }
                    }
                }
            } else {
                PathAddress address = parentAddress.isMultiTarget() ? parentAddress.getParent().append(parentAddress.getLastElement().getKey(), name) : parentAddress;
                if (isExistingResource(xmlOperations, address)) {
                    //we will have to check attributes
                    MGMT_OP_LOGGER.debugf("Resource for address %s already exists", address.toCLIStyleString());
                    //need to process attributes for updating
                    Object value = yaml.get(name);
                    if (value instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) value;
                        if (resourceRegistration.getAttributeNames(PathAddress.EMPTY_ADDRESS).contains(name)
                                && resourceRegistration.getAttributeAccess(PathAddress.EMPTY_ADDRESS, name).getAttributeDefinition().getType() == OBJECT) {
                            processAttribute(address, rootRegistration, name, value, postExtensionOps, xmlOperations);
                        } else if (!address.equals(parentAddress)) {
                            processResource(address, map, rootRegistration, rootRegistration.getSubModel(address), xmlOperations, postExtensionOps, false);
                        } else {
                            throw MGMT_OP_LOGGER.noChildResource(name, address.toCLIStyleString());
                        }
                    } else if (value instanceof Operation) {
                        Operation yamlOperation = Operation.class.cast(value);
                        yamlOperation.processOperation(rootRegistration, xmlOperations, postExtensionOps, address, name);
                    } else {
                        if (value != null && resourceRegistration.getAttributeNames(PathAddress.EMPTY_ADDRESS).contains(name)) {
                            //we are processing an attribute:
                            MGMT_OP_LOGGER.debugf("We are processing the attribute %s for address %s", name, parentAddress.toCLIStyleString());
                            processAttribute(parentAddress, rootRegistration, name, value, postExtensionOps, xmlOperations);
                        } else if (value == null) {
                            if (resourceRegistration.getAttributeNames(PathAddress.EMPTY_ADDRESS).contains(name)) {
                                MGMT_OP_LOGGER.noAttributeValueDefined(name, address.toCLIStyleString());
                            } else {
                                MGMT_OP_LOGGER.noAttributeSetForAddress(address.toCLIStyleString());
                            }
                        } else {
                            throw MGMT_OP_LOGGER.noAttributeDefined(name, address.toCLIStyleString());
                        }
                    }
                } else {
                    Object value = yaml.get(name);
                    if (resourceRegistration.getAttributeNames(PathAddress.EMPTY_ADDRESS).contains(name)) {
                        if (value != null) {
                            OperationEntry operationEntry = resourceRegistration.getOperationEntry(PathAddress.EMPTY_ADDRESS, ADD);
                            if (operationEntry == null) {
                                //we are processing an attribute: that is wrong
                                MGMT_OP_LOGGER.debugf("We are processing the attribute %s for address %s", name, address.getParent().toCLIStyleString());
                                processAttribute(parentAddress, rootRegistration, name, value, postExtensionOps, xmlOperations);
                            } else {
                                if (!postExtensionOps.isEmpty()) {
                                    ParsedBootOp op = postExtensionOps.get(postExtensionOps.size() - 1);
                                    if (!address.equals(op.getAddress())) { // else already processed
                                        Map<String, Object> map = value instanceof Map ? new HashMap<>((Map) value) : new HashMap<>(yaml);
                                        //need to process attributes for adding
                                        processAttributes(address, rootRegistration, operationEntry, map, postExtensionOps, xmlOperations);
                                        processResource(address, map, rootRegistration, resourceRegistration, xmlOperations, postExtensionOps, false);
                                    }
                                }
                            }
                        }
                    } else {
                        ImmutableManagementResourceRegistration childResourceRegistration = rootRegistration.getSubModel(address);
                        // we need to create a new resource
                        if (childResourceRegistration != null) {
                            OperationEntry operationEntry = rootRegistration.getOperationEntry(address, ADD);
                            if (operationEntry == null) {
                                MGMT_OP_LOGGER.debugf("Resource for address %s is a placeholder for %s so we don't create it", address.toCLIStyleString(), childResourceRegistration.getPathAddress().toCLIStyleString());
                                if (value instanceof Map) {
                                    Map<String, Object> map = (Map<String, Object>) value;
                                    processResource(address, map, rootRegistration, childResourceRegistration, xmlOperations, postExtensionOps, false);
                                } else {
                                    if (value != null) {
                                        if (value instanceof Operation) {
                                            if (value instanceof RemoveOperation) {
                                                MGMT_OP_LOGGER.removingUnexistingResource(address.toCLIStyleString());
                                            } else {
                                                MGMT_OP_LOGGER.illegalOperationForAttribute(((Operation) value).getOperationName(), name, address.toCLIStyleString());
                                            }
                                        } else {
                                            MGMT_OP_LOGGER.unexpectedValueForResource(value, address.toCLIStyleString(), name);
                                        }
                                    }
                                }
                            } else if (name.equals(address.getLastElement().getValue())) {
                                MGMT_OP_LOGGER.debugf("Resource for address %s needs to be created with parameters %s", address.toCLIStyleString(), Arrays.stream(operationEntry.getOperationDefinition().getParameters()).map(AttributeDefinition::getName).collect(Collectors.joining()));
                                if (value instanceof Map) {
                                    Map<String, Object> map = (Map<String, Object>) value;
                                    //need to process attributes for adding
                                    processAttributes(address, rootRegistration, operationEntry, map, postExtensionOps, xmlOperations);
                                    processResource(address, map, rootRegistration, childResourceRegistration, xmlOperations, postExtensionOps, false);
                                } else {
                                    if (value != null) {
                                        if (value instanceof Operation) {
                                            if (value instanceof RemoveOperation) {
                                                MGMT_OP_LOGGER.removingUnexistingResource(address.toCLIStyleString());
                                            } else {
                                                MGMT_OP_LOGGER.illegalOperationForAttribute(((Operation) value).getOperationName(), name, address.toCLIStyleString());
                                            }
                                        } else {
                                            MGMT_OP_LOGGER.unexpectedValueForResource(value, address.toCLIStyleString(), name);
                                        }
                                    } else {// ADD operation without parameters
                                        processAttributes(address, rootRegistration, operationEntry, null, postExtensionOps, xmlOperations);
                                    }
                                }
                            } else {
                                throw MGMT_OP_LOGGER.noChildResource(name, address.toCLIStyleString());
                            }
                        } else {
                            MGMT_OP_LOGGER.noResourceRegistered(address.toCLIStyleString(), resourceRegistration.getPathAddress().toCLIStyleString());
                        }
                    }
                }
            }
        }
    }

    private boolean isExistingResource(Map<PathAddress, ParsedBootOp> xmlOperations, PathAddress address) {
        return xmlOperations.containsKey(address);
    }

    @SuppressWarnings("unchecked")
    private void processAttribute(PathAddress address, ImmutableManagementResourceRegistration rootRegistration, String attributeName, Object value, List<ParsedBootOp> postExtensionOps, Map<PathAddress, ParsedBootOp> xmlOperations) {
        assert value != null;
        AttributeAccess attributeAccess = rootRegistration.getAttributeAccess(address, attributeName);
        if (attributeAccess.getStorageType() == AttributeAccess.Storage.CONFIGURATION && attributeAccess.getAccessType() == AttributeAccess.AccessType.READ_WRITE) {
            AttributeDefinition att = attributeAccess.getAttributeDefinition();
            if (att != null && !att.isResourceOnly()) {
                switch (att.getType()) {
                    case OBJECT: {
                        //ObjectTypeAttributeDefinition
                        OperationEntry operationEntry = rootRegistration.getOperationEntry(address, WRITE_ATTRIBUTE_OPERATION);
                        ModelNode op = createOperation(address, operationEntry);
                        op.get(NAME).set(attributeName);
                        if (att instanceof MapAttributeDefinition) {
                            ModelNode node = new ModelNode();
                            processMapAttribute((MapAttributeDefinition) att, node, (Map<String, Object>) value);
                            op.get(VALUE).set(node.get(attributeName));
                        } else {
                            op.get(VALUE).set(processObjectAttribute((ObjectTypeAttributeDefinition) att, (Map<String, Object>) value));
                        }
                        MGMT_OP_LOGGER.debugf("Updating attribute %s for resource %s with operation %s", attributeName, address, op);
                        postExtensionOps.add(new ParsedBootOp(op, operationEntry.getOperationHandler()));
                    }
                    break;
                    case LIST: {
                        if (value instanceof Operation) {
                            ((Operation) value).processOperation(rootRegistration, xmlOperations, postExtensionOps, address, att.getName());
                        } else {
                            OperationEntry operationEntry = rootRegistration.getOperationEntry(address, WRITE_ATTRIBUTE_OPERATION);
                            ModelNode op = createOperation(address, operationEntry);
                            op.get(NAME).set(attributeName);
                            ModelNode list = op.get(VALUE).setEmptyList();
                            processListAttribute((ListAttributeDefinition) att, list, value);
                            MGMT_OP_LOGGER.debugf("Updating attribute %s for resource %s with operation %s", attributeName, address, op);
                            postExtensionOps.add(new ParsedBootOp(op, operationEntry.getOperationHandler()));
                        }
                    }
                    break;
                    default: {
                        if (value instanceof Operation) {
                            ((Operation) value).processOperation(rootRegistration, xmlOperations, postExtensionOps, address, attributeName);
                        } else {
                            OperationEntry operationEntry = rootRegistration.getOperationEntry(address, WRITE_ATTRIBUTE_OPERATION);
                            ModelNode op = createOperation(address, operationEntry);
                            op.get(NAME).set(attributeName);
                            op.get(VALUE).set(value.toString());
                            MGMT_OP_LOGGER.debugf("Updating attribute %s for resource %s with operation %s", attributeName, address, op);
                            postExtensionOps.add(new ParsedBootOp(op, operationEntry.getOperationHandler()));
                        }
                    }
                    break;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void processAttributes(PathAddress address, ImmutableManagementResourceRegistration rootRegistration, OperationEntry operationEntry, Map<String, Object> map, List<ParsedBootOp> postExtensionOps, Map<PathAddress, ParsedBootOp> xmlOperations) {
        Set<AttributeDefinition> attributes = new HashSet<>();
        Set<String> attributeNames = new HashSet<>();
        for (AttributeAccess attributeAccess : rootRegistration.getAttributes(address).values()) {
            if (attributeAccess.getStorageType() == AttributeAccess.Storage.CONFIGURATION) {
                AttributeDefinition def = attributeAccess.getAttributeDefinition();
                if (def != null) {
                    if (!def.isResourceOnly()) {
                        attributes.add(def);
                        attributeNames.add(def.getName());
                    }
                }
            }
        }
        for (AttributeDefinition def : operationEntry.getOperationDefinition().getParameters()) {
            if (def != null && ! attributeNames.contains(def.getName())) {
                if (!def.isResourceOnly()) {
                    attributes.add(def);
                }
            }
        }
        attributes.addAll(Arrays.asList(operationEntry.getOperationDefinition().getParameters()));
        ModelNode op = createOperation(address, operationEntry);
        if (map != null) {
            for (AttributeDefinition att : attributes) {
                if (map.containsKey(att.getName())) {
                    Object value = map.get(att.getName());
                    map.remove(att.getName());
                    switch (att.getType()) {
                        case OBJECT:
                            if (att instanceof MapAttributeDefinition) {
                                processMapAttribute((MapAttributeDefinition) att, op, (Map<String, Object>) value);
                            } else {
                                op.get(att.getName()).set(processObjectAttribute((ObjectTypeAttributeDefinition) att, (Map<String, Object>) value));
                            }
                            break;
                        case LIST:
                            ModelNode list = op.get(att.getName()).setEmptyList();
                            processListAttribute((ListAttributeDefinition) att, list, value);
                            break;
                        default:
                            if (value != null) {
                                op.get(att.getName()).set(value.toString());
                            } else {
                                op.get(att.getName());
                            }
                            break;
                    }
                }
            }
        }
        ParsedBootOp operation = new ParsedBootOp(op, operationEntry.getOperationHandler());
        MGMT_OP_LOGGER.debugf("Adding resource with operation %s", op);
        postExtensionOps.add(operation);
        if (ADD.equals(operationEntry.getOperationDefinition().getName())) {
            xmlOperations.put(address, operation);
        }
    }

    private ModelNode createOperation(PathAddress address, OperationEntry operationEntry) {
        ModelNode op = new ModelNode();
        op.get(OP).set(operationEntry.getOperationDefinition().getName());
        op.get(OP_ADDR).set(address.toModelNode());
        return op;
    }

    @SuppressWarnings("unchecked")
    private ModelNode processObjectAttribute(ObjectTypeAttributeDefinition att, Map<String, Object> map) {
        ModelNode objectNode = new ModelNode();
        for (AttributeDefinition child : att.getValueTypes()) {
            if (map.containsKey(child.getName())) {
                Object value = map.get(child.getName());
                switch (child.getType()) {
                    case OBJECT:
                        if (child instanceof MapAttributeDefinition) {
                            processMapAttribute((MapAttributeDefinition) child, objectNode, (Map<String, Object>) value);
                        } else {
                            objectNode.get(child.getName()).set(processObjectAttribute((ObjectTypeAttributeDefinition) child, (Map<String, Object>) value));
                        }
                        break;
                    case LIST:
                        ModelNode list = objectNode.get(child.getName()).setEmptyList();
                        processListAttribute((ListAttributeDefinition) child, list, value);
                        break;
                    default:
                        objectNode.get(child.getName()).set(value.toString());
                        break;
                }
            }
        }
        return objectNode;
    }

    @SuppressWarnings("unchecked")
    private void processListAttribute(ListAttributeDefinition att, ModelNode list, Object value) {
        AttributeDefinition type = att.getValueAttributeDefinition();
        if (type == null) {
            throw ROOT_LOGGER.missingListAttributeValueType(att.getName());
        }
        boolean isObject = OBJECT == type.getType();
        for (Object entry : ((Iterable<? extends Object>) value)) {
            if (isObject) {
                list.add(processObjectAttribute((ObjectTypeAttributeDefinition) type, ((Map<String, Object>) entry)));
            } else {
                list.add(entry.toString());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void processMapAttribute(MapAttributeDefinition att, ModelNode map, Map<String, Object> yaml) {
        if (att instanceof ObjectMapAttributeDefinition) {
            ObjectMapAttributeDefinition objectMapAtt = (ObjectMapAttributeDefinition) att;
            ModelNode objectMapNode = map.get(att.getName()).setEmptyObject();
            for (Map.Entry<String, Object> entry : yaml.entrySet()) {
                ModelNode objectValue = processObjectAttribute(objectMapAtt.getValueType(), (Map<String, Object>) entry.getValue());
                objectMapNode.get(entry.getKey()).set(objectValue);
            }
        } else {
            for (Map.Entry<String, Object> entry : yaml.entrySet()) {
                map.get(att.getName()).get(entry.getKey()).set(entry.getValue().toString());
            }
        }
    }

    @Override
    public String[] getCommandLineUsageArguments() {
        return new String[]{YAML_CONFIG + "=[<paths>]", SHORT_YAML_CONFIG + "=[<paths>]"};
    }

    @Override
    public boolean commandLineContainsArgument(String arg) {
        return arg.startsWith(YAML_CONFIG) || arg.startsWith(SHORT_YAML_CONFIG);
    }

    @Override
    public String getCommandLineInstructions() {
        return MGMT_OP_LOGGER.argYaml();
    }

    @SuppressWarnings("unchecked")
    private void processUnmanagedDeployments(ImmutableManagementResourceRegistration rootRegistration, Map.Entry<String, Object> deployment, Map<PathAddress, ParsedBootOp> xmlOperations, List<ParsedBootOp> postExtensionOps) {
        String name = deployment.getKey();
        OperationEntry operationEntry = rootRegistration.getOperationEntry(PathAddress.pathAddress("deployment", name), ADD);
        assert deployment.getValue() instanceof Map;
        Map<String, Object> attributes = (Map<String, Object>) deployment.getValue();
        if (attributes.get("content") instanceof Iterable) {
            Map<String, Object> content = (Map<String, Object>) (((Iterable<? extends Object>) attributes.get("content")).iterator().next());
            Set<String> result = content.keySet().stream().distinct().filter(MANAGED_CONTENT_ATTRIBUTES::contains).collect(Collectors.toSet());
            if (!result.isEmpty()) {
                throw MGMT_OP_LOGGER.unsupportedDeployment(name, result);
            }
        }
        PathAddress address = PathAddress.pathAddress(DEPLOYMENT, name);
        processAttributes(address, rootRegistration, operationEntry, attributes, postExtensionOps, xmlOperations);
    }

    private interface Operation {

        String getOperationName();

        void processOperation(ImmutableManagementResourceRegistration rootRegistration, Map<PathAddress, ParsedBootOp> xmlOperations, List<ParsedBootOp> postExtensionOps, PathAddress address, String name);
    }

    private class RemoveOperation implements Operation {

        RemoveOperation() {
        }

        @Override
        public void processOperation(ImmutableManagementResourceRegistration rootRegistration, Map<PathAddress, ParsedBootOp> xmlOperations, List<ParsedBootOp> postExtensionOps, PathAddress address, String name) {
            ListIterator<ParsedBootOp> iter = postExtensionOps.listIterator();
            while (iter.hasNext()) {
                ParsedBootOp op = iter.next();
                if (op.getChildOperations().isEmpty()) {
                    if (op.getAddress().toCLIStyleString().startsWith(address.toCLIStyleString())) {
                        iter.remove();
                        xmlOperations.remove(op.getAddress());
                    }
                } else {
                    List<ParsedBootOp> childOps = new ArrayList<>();
                    for (ModelNode childOp : op.getChildOperations()) {
                        ParsedBootOp childBootOp = new ParsedBootOp(childOp, null);
                        if (childBootOp.getAddress().toCLIStyleString().startsWith(address.toCLIStyleString())) {
                            xmlOperations.remove(childBootOp.getAddress());
                        } else {
                            childOps.add(childBootOp);
                        }
                    }
                    ParsedBootOp newOp = new ParsedBootOp(op.operation, op.handler);
                    for (ParsedBootOp childBootOp : childOps) {
                        newOp.addChildOperation(childBootOp);
                    }
                    iter.set(newOp);
                }
            }
        }

        @Override
        public String getOperationName() {
            return REMOVE;
        }

    }

    private class UndefineOperation implements Operation {

        UndefineOperation() {
        }

        @Override
        public void processOperation(ImmutableManagementResourceRegistration rootRegistration, Map<PathAddress, ParsedBootOp> xmlOperations, List<ParsedBootOp> postExtensionOps, PathAddress address, String name) {
            OperationEntry operationEntry = rootRegistration.getOperationEntry(address, UNDEFINE_ATTRIBUTE_OPERATION);
            if (operationEntry != null) {
                ModelNode op = new ModelNode();
                op.get(OP).set(UNDEFINE_ATTRIBUTE_OPERATION);
                op.get(OP_ADDR).set(address.toModelNode());
                op.get(NAME).set(name);
                postExtensionOps.add(new ParsedBootOp(op, operationEntry.getOperationHandler()));
            } else {
                throw MGMT_OP_LOGGER.illegalOperationForAttribute(getOperationName(), name, address.toCLIStyleString());
            }
        }

        @Override
        public String getOperationName() {
            return UNDEFINE_ATTRIBUTE_OPERATION;
        }

    }

    private class ListAddOperation implements Operation {

        private final List<? extends Object> value;

        ListAddOperation(List<? extends Object> obj) {
            this.value = obj;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void processOperation(ImmutableManagementResourceRegistration rootRegistration, Map<PathAddress, ParsedBootOp> xmlOperations, List<ParsedBootOp> postExtensionOps, PathAddress address, String name) {
            OperationEntry operationEntry = rootRegistration.getOperationEntry(address, getOperationName());
            if (operationEntry != null) {
                AttributeAccess access = rootRegistration.getAttributeAccess(address, name);
                if (!(access.getAttributeDefinition() instanceof ListAttributeDefinition)) {
                    throw MGMT_OP_LOGGER.illegalOperationForAttribute(getOperationName(), name, address.toCLIStyleString());
                }
                ListAttributeDefinition att = (ListAttributeDefinition) access.getAttributeDefinition();
                AttributeDefinition type = att.getValueAttributeDefinition();
                if (type == null) {
                    throw ROOT_LOGGER.missingListAttributeValueType(att.getName());
                }
                String attributeName = att.getName();
                for (Object entry : value) {
                    ModelNode op = new ModelNode();
                    op.get(OP_ADDR).set(address.toModelNode());
                    op.get(NAME).set(attributeName);
                    op.get(OP).set("list-add");
                    switch (type.getType()) {
                        case OBJECT:
                            Map<String, Object> map = (Map<String, Object>) entry;
                            if (map != null && map.containsKey("index")) {
                                op.get("index").set((Integer) map.get("index"));
                            }
                            op.get(VALUE).set(processObjectAttribute((ObjectTypeAttributeDefinition) type, map));
                            break;
                        case LIST:
                            if (entry instanceof Map) {
                                Map<String, Object> indexedEntry = (Map<String, Object>) entry;
                                if (indexedEntry.containsKey("index")) {
                                    op.get("index").set((Integer) indexedEntry.get("index"));
                                    indexedEntry.remove("index");
                                }
                                for (Map.Entry<String, Object> realValue : indexedEntry.entrySet()) {
                                    op.get(VALUE).set(realValue.getValue().toString());
                                }
                            } else {
                                if (entry != null) {
                                    op.get(VALUE).set(entry.toString());
                                }
                            }
                            break;
                        default:
                            if (entry instanceof Map) {
                                Map<String, Object> indexedSimpleType = (Map<String, Object>) entry;
                                if (indexedSimpleType.size() == 1) {
                                    String realValue = indexedSimpleType.keySet().iterator().next();
                                    Map<String, Object> indexedEntry = (Map<String, Object>) indexedSimpleType.get(realValue);
                                    if (indexedEntry.containsKey("index")) {
                                        op.get("index").set((Integer) indexedEntry.get("index"));
                                    }
                                    op.get(VALUE).set(realValue);
                                } else {
                                    Map<String, Object> indexedEntry = (Map<String, Object>) entry;
                                    if (indexedEntry.containsKey("index")) {
                                        op.get("index").set((Integer) indexedEntry.get("index"));
                                        indexedEntry.remove("index");
                                    }
                                    for (Map.Entry<String, Object> realValue : indexedEntry.entrySet()) {
                                        op.get(VALUE).set(realValue.getValue().toString());
                                    }
                                }
                            } else {
                                if (entry != null) {
                                    op.get(VALUE).set(entry.toString());
                                }
                            }
                            break;
                    }
                    ParsedBootOp operation = new ParsedBootOp(op, operationEntry.getOperationHandler());
                    MGMT_OP_LOGGER.debugf("Updating attribute %s for resource %s with operation %s", attributeName, address, op);
                    postExtensionOps.add(operation);
                }
            }
        }

        @Override
        public String getOperationName() {
            return "list-add";
        }

    }

    private class OperationConstructor extends Constructor {

        private final Tag REMOVE = new Tag("!remove");
        private final Tag UNDEFINE = new Tag("!undefine");
        private final Tag ADD = new Tag("!list-add");

        public OperationConstructor(LoaderOptions loadingConfig) {
            super(loadingConfig);
            this.yamlConstructors.put(REMOVE, new ConstructRemoveOperation());
            this.yamlConstructors.put(UNDEFINE, new ConstructUndefineOperation());
            this.yamlConstructors.put(ADD, new ConstructListAddOperation());
        }

        @Override
        protected Class<?> getClassForNode(Node node) {
            Class<? extends Object> classForTag = typeTags.get(node.getTag());
            if (classForTag == null) {
                throw new YAMLException("Class not found: " + node.getTag().getClassName());
            }
            return classForTag;
        }

        @Override
        protected void flattenMapping(MappingNode node) {
            // perform merging only on nodes containing merge node(s)
            processDuplicateKeys(node);
            if (node.isMerged()) {
                node.setValue(mergeNode(node, false, new HashMap<>(), new ArrayList<>()));
            }
        }

        @Override
        protected void processDuplicateKeys(MappingNode node) {
            List<NodeTuple> nodeValue = node.getValue();
            Map<Object, Integer> keys = new HashMap<>(nodeValue.size());
            int i = 0;
            for (NodeTuple tuple : nodeValue) {
                Node keyNode = tuple.getKeyNode();
                if (!keyNode.getTag().equals(Tag.MERGE)) {
                    Object key = constructObject(keyNode);
                    if (key != null) {
                        try {
                            key.hashCode();// check circular dependencies
                        } catch (Exception e) {
                            MGMT_OP_LOGGER.infof("Error processing duplicate key %s", key, e);
                        }
                    }

                    Integer prevIndex = keys.put(key, i);
                    if (prevIndex != null) {
                        node.setMerged(true);
                        MGMT_OP_LOGGER.debugf("Duplicate key found  %s", key);
                    }
                }
                i += 1;
            }
        }

        /**
         * Does merge for supplied mapping node.
         *
         * @param node
         * where to merge
         * @param isPreffered
         * true if keys of node should take precedence over others...
         * @param key2index
         * maps already merged keys to index from values
         * @param values
         * collects merged NodeTuple
         * @return list of the merged NodeTuple (to be set as value for the
         * MappingNode)
         */
        private List<NodeTuple> mergeNode(MappingNode node, boolean isPreffered,
                Map<Object, Integer> key2index, List<NodeTuple> values) {
            Iterator<NodeTuple> iter = node.getValue().iterator();
            while (iter.hasNext()) {
                final NodeTuple nodeTuple = iter.next();
                final Node keyNode = nodeTuple.getKeyNode();
                final Node valueNode = nodeTuple.getValueNode();
                if (keyNode.getTag().equals(Tag.MERGE)) {
                    iter.remove();
                    switch (valueNode.getNodeId()) {
                        case mapping:
                            MappingNode mn = (MappingNode) valueNode;
                            mergeNode(mn, false, key2index, values);
                            break;
                        case sequence:
                            SequenceNode sn = (SequenceNode) valueNode;
                            List<Node> vals = sn.getValue();
                            for (Node subnode : vals) {
                                if (!(subnode instanceof MappingNode)) {
                                    throw new YAMLException(MGMT_OP_LOGGER.errorConstructingYAMLMapping(node.getStartMark(), subnode.getNodeId()));
                                }
                                MappingNode mnode = (MappingNode) subnode;
                                mergeNode(mnode, false, key2index, values);
                            }
                            break;
                        default:
                            throw new YAMLException(MGMT_OP_LOGGER.errorConstructingYAMLMapping(node.getStartMark(), valueNode.getNodeId()));
                    }
                } else {
                    // we need to construct keys to avoid duplications
                    Object key = constructObject(keyNode);
                    if (!key2index.containsKey(key)) { // 1st time merging key
                        values.add(nodeTuple);
                        MGMT_OP_LOGGER.debugf("First key %s %s", key, nodeTuple.getValueNode());
                        // keep track where tuple for the key is
                        key2index.put(key, values.size() - 1);
                    } else if (isPreffered) { // there is value for the key, but we
                        // need to override it
                        // change value for the key using saved position
                        values.set(key2index.get(key), nodeTuple);
                    } else {
                        MGMT_OP_LOGGER.debugf("Other value found for key %s %s", key, nodeTuple.getValueNode());
                        int index = key2index.get(key);
                        NodeTuple firstTuple = values.get(index);
                        switch (firstTuple.getValueNode().getNodeId()) {
                            case mapping:
                                if (firstTuple.getValueNode().getNodeId() == NodeId.mapping) {
                                    MappingNode mn1 = (MappingNode) firstTuple.getValueNode();
                                    if (REMOVE.equals(valueNode.getTag())) {
                                        values.set(key2index.get(key), nodeTuple);
                                    } else {
                                        MappingNode mn = (MappingNode) valueNode;
                                        mergeNode(mn, false, key2index, mn1.getValue());
                                    }
                                } else if (REMOVE.equals(firstTuple.getValueNode().getTag())) {
                                    values.set(key2index.get(key), nodeTuple);
                                }
                                break;
                            case scalar:
                                values.set(key2index.get(key), nodeTuple);
                                break;
                            case sequence:
                                SequenceNode sn = (SequenceNode) valueNode;
                                List<Node> vals = sn.getValue();
                                for (Node subnode : vals) {
                                    switch (subnode.getNodeId()) {
                                        case mapping:
                                            MappingNode mnode = (MappingNode) subnode;
                                            mergeNode(mnode, false, key2index, values);
                                            break;
                                        case scalar:
                                            ((SequenceNode) values.get(key2index.get(key)).getValueNode()).getValue().add(subnode);
                                            break;
                                        default:
                                            throw new YAMLException(MGMT_OP_LOGGER.errorConstructingYAMLMapping(node.getStartMark(), subnode.getNodeId()));
                                    }
                                }
                                break;
                            default:
                                throw new YAMLException(MGMT_OP_LOGGER.errorConstructingYAMLMapping(node.getStartMark(), firstTuple.getValueNode().getNodeId()));
                        }
                    }
                }
            }
            return values;
        }

        private class ConstructRemoveOperation extends AbstractConstruct {

            @Override
            public Object construct(Node node) {
                return new RemoveOperation();
            }
        }

        private class ConstructUndefineOperation extends AbstractConstruct {

            @Override
            public Object construct(Node node) {
                return new UndefineOperation();
            }
        }

        private class ConstructListAddOperation implements Construct {

            private final Construct delegate = new SafeConstructor.ConstructYamlSeq();

            @Override
            public Object construct(Node node) {
                return new ListAddOperation((List<? extends Object>) delegate.construct(node));
            }

            @Override
            public void construct2ndStep(Node node, Object object) {
                delegate.construct2ndStep(node, object);
            }
        }
    }
}
