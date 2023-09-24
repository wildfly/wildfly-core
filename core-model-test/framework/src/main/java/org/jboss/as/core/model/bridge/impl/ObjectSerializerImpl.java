/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.bridge.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.core.model.bridge.shared.ObjectSerializer;
import org.jboss.as.core.model.test.LegacyModelInitializerEntry;
import org.jboss.as.host.controller.ignored.IgnoreDomainResourceTypeResource;
import org.jboss.dmr.ModelNode;
import org.xnio.IoUtils;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ObjectSerializerImpl implements ObjectSerializer {

    private static final String PARENT_ADDRESS = "_parent_address";
    private static final String RELATIVE_RESOURCE_ADDRESS = "_relative_resource_address";
    private static final String MODEL_NODE = "_model_node";
    private static final String CAPABILITIES = "_capabilities";

    @Override
    public byte[] serializeModelNode(Object object) throws IOException {
        //Happens in the app classloader
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try {
            ((ModelNode)object).writeExternal(bout);
        } finally {
            bout.flush();
            IoUtils.safeClose(bout);
        }
        return bout.toByteArray();
    }

    @Override
    public Object deserializeModelNode(byte[] object) throws IOException {
        //Happens in the child classloader
        InputStream in = new ByteArrayInputStream(object);
        try {
            ModelNode modelNode = new ModelNode();
            modelNode.readExternal(in);
            return modelNode;
        } finally {
            IoUtils.safeClose(in);
        }
    }

    @Override
    public String serializeModelVersion(Object object) {
        //Happens in the app classloader
        return ((ModelVersion)object).toString();

    }

    @Override
    public Object deserializeModelVersion(String object) {
        //Happens in the child classloader
        return ModelVersion.fromString(object);
    }

    @Override
    public byte[] serializeLegacyModelInitializerEntry(Object object) throws IOException {
        //Happens in the app classloader
        LegacyModelInitializerEntry entry = (LegacyModelInitializerEntry)object;
        ModelNode node = new ModelNode();
        if (entry.getParentAddress() != null) {
            node.get(PARENT_ADDRESS).set(entry.getParentAddress().toModelNode());
        }
        node.get(RELATIVE_RESOURCE_ADDRESS).set(PathAddress.pathAddress(entry.getRelativeResourceAddress()).toModelNode());
        if (entry.getModel() != null) {
            node.get(MODEL_NODE).set(entry.getModel());
        }
        if (entry.getCapabilities() != null) {
            ModelNode caps = node.get(CAPABILITIES).setEmptyList();
            for (String cap : entry.getCapabilities()) {
                caps.add(cap);
            }
        }
        return serializeModelNode(node);
    }

    @Override
    public Object deserializeLegacyModelInitializerEntry(byte[] object) throws IOException {
        //Happens in the child classloader
        ModelNode node = (ModelNode)deserializeModelNode(object);
        PathAddress parentAddress = null;
        if (node.hasDefined(PARENT_ADDRESS)) {
            parentAddress = PathAddress.pathAddress(node.get(PARENT_ADDRESS));
        }
        PathElement relativeResourceAddress = PathAddress.pathAddress(node.get(RELATIVE_RESOURCE_ADDRESS)).getElement(0);
        ModelNode model = null;
        if (node.hasDefined(MODEL_NODE)) {
            model = node.get(MODEL_NODE);
        }
        String[] capabilities;
        if (node.hasDefined(CAPABILITIES)) {
            List<String> list = new ArrayList<>();
            for (ModelNode mn : node.get(CAPABILITIES).asList()) {
                list.add(mn.asString());
            }
            capabilities = list.toArray(new String[list.size()]);
        } else {
            capabilities = new String[0];
        }
        return new LegacyModelInitializerEntry(parentAddress, relativeResourceAddress, model, capabilities);
    }

    @Override
    public byte[] serializeIgnoreDomainTypeResource(Object object) throws IOException {
        //Happens in the app classloader
        IgnoreDomainResourceTypeResource entry = (IgnoreDomainResourceTypeResource)object;
        ModelNode model = entry.getModel().clone();
        model.get("type").set(entry.getName());
        return serializeModelNode(model);
    }

    @Override
    public Object deserializeIgnoreDomainTypeResource(byte[] object) throws IOException {
        //Happens in the child classloader
        ModelNode model = (ModelNode)deserializeModelNode(object);
        String type = model.require("type").asString();
        ModelNode names = model.get("names");
        Boolean wildcard = null;
        if (model.hasDefined("wildcard")) {
            wildcard = model.get("wildcard").asBoolean();
        }
        return new IgnoreDomainResourceTypeResource(type, names, wildcard);
    }

    @Override
    public byte[] serializeModelTestOperationValidatorFilter(Object object) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bout);
        try {
            out.writeObject(object);
        } finally {
            IoUtils.safeClose(out);
            IoUtils.safeClose(bout);
        }
        return bout.toByteArray();
    }

    @Override
    public Object deserializeModelTestOperationValidatorFilter(byte[] object) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bin = new ByteArrayInputStream(object);
        ObjectInputStream in = new ObjectInputStream(bin);
        Object o = null;
        try {
            o = in.readObject();
        } finally {
            IoUtils.safeClose(in);
            IoUtils.safeClose(bin);
        }
        return o;
    }
}
