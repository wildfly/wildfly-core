/*
 * Copyright 2021 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.controller.persistence;

import static org.jboss.as.controller.client.impl.AdditionalBootCliScriptInvoker.CLI_SCRIPT_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;
import static org.jboss.dmr.ModelType.LIST;
import static org.jboss.dmr.ModelType.OBJECT;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.ParsedBootOp;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.Construct;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;

/**
 *
 * @author Emmanuel Hugonnet (c) 2021 Red Hat, Inc.
 */
public class YamlConfigurationExtension implements ConfigurationExtension {

    private static final String CONFIGURATION_ROOT_KEY = "wildfly-configuration";

    private boolean needReload;
    private Path[] files;
    private final List<Map<String, Object>> configs = new ArrayList<>();
    private static final String[] EXCLUDED_ELEMENTS = {" deployment", "extension", "deployment-overlay"};

    @SuppressWarnings("unchecked")
    public YamlConfigurationExtension(Path... files) {
        this.files = files;
        load();
    }

    private final void load() {
        long start = System.currentTimeMillis();
        for (Path file : files) {
            if (file != null && Files.exists(file) && Files.isRegularFile(file)) {
                Map<String, Object> yamlConfig = Collections.emptyMap();
                try (InputStream inputStream = Files.newInputStream(file)) {
                    Yaml yaml = new Yaml(new OperationConstructor());
                    yamlConfig = yaml.load(inputStream);
                } catch (IOException ioex) {
                    MGMT_OP_LOGGER.warn("Error parsing yaml file %s ", file.toString(), ioex);
                }
                if (yamlConfig.containsKey(CONFIGURATION_ROOT_KEY)) {
                    Map<String, Object> config = (Map<String, Object>) yamlConfig.get(CONFIGURATION_ROOT_KEY);
                    for (String excluded : EXCLUDED_ELEMENTS) {
                        config.remove(excluded);
                    }
                    this.configs.add(config);
                }
            }
        }
        MGMT_OP_LOGGER.warnf("It took %s ms to load and parse the yaml files", System.currentTimeMillis() - start);
        this.needReload = false;
    }

    @Override
    public boolean shouldProcessOperations(RunningMode mode) {
        return !this.configs.isEmpty() && (RunningMode.ADMIN_ONLY != mode || null == WildFlySecurityManager.getPropertyPrivileged(CLI_SCRIPT_PROPERTY, null));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void processOperations(ImmutableManagementResourceRegistration rootRegistration, List<ParsedBootOp> postExtensionOps) {
        if(needReload) {
            load();
        }
        MGMT_OP_LOGGER.warn("**** Jean-François We are applying the YAML files to the configuration");
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
                        MGMT_OP_LOGGER.debugf("No registration found for address %s", address.toCLIStyleString());
                        processResource(address, map, rootRegistration, resourceRegistration, xmlOperations, postExtensionOps, true);
                    }
                } else {
                    if (value == null && !isExistingResource(xmlOperations, address)) { //empty resource
                        OperationEntry operationEntry = rootRegistration.getOperationEntry(address, ADD);
                        processAttributes(address, rootRegistration, operationEntry, Collections.emptyMap(), postExtensionOps);
                    } else if (value != null && value instanceof Operation) {
                        Operation yamlOperation = Operation.class.cast(value);
                        if (isExistingResource(xmlOperations, address)) {
                            postExtensionOps.addAll(yamlOperation.getOperations(rootRegistration, address, name));
                        } else if (yamlOperation instanceof RemoveOperation) {
                            //ignore
                        } else {
                            throw new RuntimeException("Can't undefine attribute " + name + " since there is no resource at " + address.toCLIStyleString());
                        }
                    } else {
                        if (!isExistingResource(xmlOperations, address)) {
                            MGMT_OP_LOGGER.warnf("You have defined a resource for address %s without any attributes, doing nothing", address.toCLIStyleString());
                        }
                    }
                }
            } else {
                PathAddress address = parentAddress.getParent().append(parentAddress.getLastElement().getKey(), name);
                if (isExistingResource(xmlOperations, address)) {
                    //we will have to check attributes
                    MGMT_OP_LOGGER.debugf("Resource for address %s already exists", address.toCLIStyleString());
                    //need to process attributes for updating
                    Object value = yaml.get(name);
                    if (value instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) value;
                        processResource(address, map, rootRegistration, rootRegistration.getSubModel(address), xmlOperations, postExtensionOps, false);
                    } else if (value != null && value instanceof Operation) {
                        Operation yamlOperation = Operation.class.cast(value);
                        postExtensionOps.addAll(yamlOperation.getOperations(rootRegistration, address, name));
                    } else {
                        //Ignoring
                        //MGMT_OP_LOGGER.warnf("You have defined a resource for address %s without any attributes, doing nothing", address.toCLIStyleString());
                    }
                } else {
                    if (resourceRegistration.getAttributeNames(PathAddress.EMPTY_ADDRESS).contains(name)) {
                        //we are processing an attribute: that is wrong
                        MGMT_OP_LOGGER.debugf("We are processing the attribute %s for address %s", name, address.getParent().toCLIStyleString());
                        processAttribute(parentAddress, rootRegistration, name, yaml.get(name), postExtensionOps);
                    } else {
                        ImmutableManagementResourceRegistration childResourceRegistration = rootRegistration.getSubModel(address);
                        // we need to create a new resource
                        if (childResourceRegistration != null) {
                            OperationEntry operationEntry = rootRegistration.getOperationEntry(address, ADD);
                            if (operationEntry == null) {
                                MGMT_OP_LOGGER.debugf("Resource for address %s is a placeholder for %s so we don't create it", address.toCLIStyleString(), childResourceRegistration.getPathAddress().toCLIStyleString());
                                Object value = yaml.get(name);
                                if (value instanceof Map) {
                                    Map<String, Object> map = (Map<String, Object>) value;
                                    processResource(address, map, rootRegistration, childResourceRegistration, xmlOperations, postExtensionOps, false);
                                } else {
                                    MGMT_OP_LOGGER.warnf("We have a value %s for address %s and name %s", value, address.toCLIStyleString(), name);
                                }
                            } else {
                                MGMT_OP_LOGGER.debugf("Resource for address %s needs to be created with parameters %s", address.toCLIStyleString(), Arrays.stream(operationEntry.getOperationDefinition().getParameters()).map(AttributeDefinition::getName).collect(Collectors.joining()));
                                Object value = yaml.get(name);
                                if (value instanceof Map) {
                                    Map<String, Object> map = (Map<String, Object>) value;
                                    //need to process attributes for adding
                                    processAttributes(address, rootRegistration, operationEntry, map, postExtensionOps);
                                    processResource(address, map, rootRegistration, childResourceRegistration, xmlOperations, postExtensionOps, false);
                                } else {
                                    MGMT_OP_LOGGER.warnf("-------- We have a value %s for address %s and name %s", value, address.toCLIStyleString(), name);
                                }
                            }
                        } else {
                            MGMT_OP_LOGGER.warnf("Couldn't find a resource registration for address %s with current registration %s !!!!!!!!!!!!!!!", address.toCLIStyleString(), resourceRegistration.getPathAddress().toCLIStyleString());
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
    private void processAttribute(PathAddress address, ImmutableManagementResourceRegistration rootRegistration, String attributeName, Object value, List<ParsedBootOp> postExtensionOps) {
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
                        op.get(VALUE).set(processObjectAttribute((ObjectTypeAttributeDefinition) att, (Map<String, Object>) value));
                        MGMT_OP_LOGGER.debugf("Updating attribute %s for resource %s with operation %s", attributeName, address, op);
                        postExtensionOps.add(new ParsedBootOp(op, operationEntry.getOperationHandler()));
                    }
                    break;
                    case LIST: {
                        if (value instanceof Operation) {
                            /*OperationEntry operationEntry = rootRegistration.getOperationEntry(address, "list-add");
                        processListElements((ListAttributeDefinition) att, postExtensionOps, operationEntry, address, value);*/
                            postExtensionOps.addAll(((Operation) value).getOperations(rootRegistration, address, att.getName()));
                        } else {
                            OperationEntry operationEntry = rootRegistration.getOperationEntry(address, WRITE_ATTRIBUTE_OPERATION);
                            ModelNode op = createOperation(address, operationEntry);
                            op.get(NAME).set(attributeName);
                            ModelNode list = op.get(VALUE).setEmptyList();
                            processListAttribute((ListAttributeDefinition) att, list, value);
                        }
                    }
                    break;
                    default: {
                        if (value instanceof Operation) {
                            postExtensionOps.addAll(((Operation) value).getOperations(rootRegistration, address, attributeName));
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
    private void processAttributes(PathAddress address, ImmutableManagementResourceRegistration rootRegistration, OperationEntry operationEntry, Map<String, Object> map, List<ParsedBootOp> postExtensionOps) {
        Set<AttributeDefinition> attributes = new HashSet<>();
        for (AttributeAccess attributeAccess : rootRegistration.getAttributes(address).values()) {
            if (attributeAccess.getStorageType() == AttributeAccess.Storage.CONFIGURATION) {
                AttributeDefinition def = attributeAccess.getAttributeDefinition();
                if (def != null) {
                    if (!def.isResourceOnly()) {
                        attributes.add(def);
                    }
                }
            }
        }
        attributes.addAll(Arrays.asList(operationEntry.getOperationDefinition().getParameters()));
        ModelNode op = createOperation(address, operationEntry);
        for (AttributeDefinition att : attributes) {
            if (map.containsKey(att.getName())) {
                Object value = map.get(att.getName());
                map.remove(att.getName());
                switch (att.getType()) {
                    case OBJECT:
                        op.get(att.getName()).set(processObjectAttribute((ObjectTypeAttributeDefinition) att, (Map<String, Object>) value));
                        break;
                    case LIST:
                        ModelNode list = op.get(att.getName()).setEmptyList();
                        processListAttribute((ListAttributeDefinition) att, list, value);
                        break;
                    default:
                        op.get(att.getName()).set(value.toString());
                        break;
                }
            }
        }
        ParsedBootOp operation = new ParsedBootOp(op, operationEntry.getOperationHandler());
        MGMT_OP_LOGGER.debugf("Adding resource with operation %s", op);
        postExtensionOps.add(operation);
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
                        objectNode.get(child.getName()).set(processObjectAttribute((ObjectTypeAttributeDefinition) child, (Map<String, Object>) value));
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
        AttributeDefinition type = att.getValueType();
        for (Object entry : ((Iterable<? extends Object>) value)) {
            switch (type.getType()) {
                case OBJECT:
                    list.add(processObjectAttribute((ObjectTypeAttributeDefinition) type, ((Map<String, Object>) entry)));
                    break;
                case LIST:
                default:
                    list.add(entry.toString());
                    break;
            }
        }
    }

    private interface Operation {

        Collection<ParsedBootOp> getOperations(ImmutableManagementResourceRegistration rootRegistration, PathAddress address, String name);
    }

    private class RemoveOperation implements Operation {

        RemoveOperation() {
        }

        @Override
        public Collection<ParsedBootOp> getOperations(ImmutableManagementResourceRegistration rootRegistration, PathAddress address, String name) {
            OperationEntry operationEntry = rootRegistration.getOperationEntry(address, REMOVE);
            if (operationEntry == null) {
                return Collections.emptyList();
            }
            ModelNode op = new ModelNode();
            op.get(OP).set(REMOVE);
            op.get(OP_ADDR).set(address.toModelNode());
            return Collections.singletonList(new ParsedBootOp(op, operationEntry.getOperationHandler()));
        }
    }

    private class UndefineOperation implements Operation {

        UndefineOperation() {
        }

        @Override
        public Collection<ParsedBootOp> getOperations(ImmutableManagementResourceRegistration rootRegistration, PathAddress address, String name) {
            OperationEntry operationEntry = rootRegistration.getOperationEntry(address, UNDEFINE_ATTRIBUTE_OPERATION);
            if (operationEntry == null) {
                return Collections.emptyList();
            }
            ModelNode op = new ModelNode();
            op.get(OP).set(UNDEFINE_ATTRIBUTE_OPERATION);
            op.get(OP_ADDR).set(address.toModelNode());
            op.get(NAME).set(name);
            return Collections.singletonList(new ParsedBootOp(op, operationEntry.getOperationHandler()));
        }
    }

    private class ListAddOperation implements Operation {

        private final List<? extends Object> value;

        ListAddOperation(List<? extends Object> obj) {
            this.value = obj;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Collection<ParsedBootOp> getOperations(ImmutableManagementResourceRegistration rootRegistration, PathAddress address, String name) {
            Collection<ParsedBootOp> operations = new ArrayList<>();
            OperationEntry operationEntry = rootRegistration.getOperationEntry(address, "list-add");
            if (operationEntry == null) {
                return Collections.emptyList();
            }
            AttributeAccess access = rootRegistration.getAttributeAccess(address, name);
            ListAttributeDefinition att = (ListAttributeDefinition) access.getAttributeDefinition();
            AttributeDefinition type = att.getValueType();
            String attributeName = att.getName();
            for (Object entry : value) {
                ModelNode op = new ModelNode();
                op.get(OP_ADDR).set(address.toModelNode());
                op.get(NAME).set(attributeName);
                op.get(OP).set("list-add");
                switch (type.getType()) {
                    case OBJECT:
                        Map<String, Object> map = (Map<String, Object>) entry;
                        if (map.containsKey("index")) {
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
                            op.get(VALUE).set(entry.toString());
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
                            op.get(VALUE).set(entry.toString());
                        }
                        break;
                }
                ParsedBootOp operation = new ParsedBootOp(op, operationEntry.getOperationHandler());
                MGMT_OP_LOGGER.debugf("Updating attribute %s for resource %s with operation %s", attributeName, address, op);
                operations.add(operation);
            }
            return operations;
        }
    }

    private class OperationConstructor extends Constructor {

        public OperationConstructor() {
            this.yamlConstructors.put(new Tag("!remove"), new ConstructRemoveOperation());
            this.yamlConstructors.put(new Tag("!undefine"), new ConstructUndefineOperation());
            this.yamlConstructors.put(new Tag("!list-add"), new ConstructListAddOperation());
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

            private Construct delegate = new SafeConstructor.ConstructYamlSeq();

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
