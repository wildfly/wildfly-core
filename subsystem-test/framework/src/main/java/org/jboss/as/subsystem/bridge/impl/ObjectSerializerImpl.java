/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.bridge.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.subsystem.bridge.shared.ObjectSerializer;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ObjectSerializerImpl implements ObjectSerializer {

    private static final String PARENT_ADDRESS = "_parent_address";
    private static final String RELATIVE_RESOURCE_ADDRESS = "_relative_resource_address";
    private static final String MODEL_NODE = "_model_node";

    @Override
    public byte[] serializeModelNode(Object object) throws IOException {
        //Happens in the app classloader
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try {
            ((ModelNode)object).writeExternal(bout);
        } finally {
            bout.flush();
            safeClose(bout);
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
            safeClose(in);
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
    public byte[] serializeAdditionalInitialization(Object object) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bout);
        try {
            out.writeObject(object);
        } finally {
            safeClose(out);
            safeClose(bout);
        }
        return bout.toByteArray();
    }

    @Override
    public Object deserializeAdditionalInitialization(byte[] object) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bin = new ByteArrayInputStream(object);
        ObjectInputStream in = new ObjectInputStream(bin);
        Object read = null;
        try {
            read = in.readObject();
        } finally {
            safeClose(in);
            safeClose(bin);
        }
        return read;
    }

    @Override
    public byte[] serializeModelTestOperationValidatorFilter(Object object) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bout);
        try {
            out.writeObject(object);
        } finally {
            safeClose(out);
            safeClose(bout);
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
            safeClose(in);
            safeClose(bin);
        }
        return o;
    }

    private static void safeClose(final Closeable closeable) {
        if(closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                //
            }
        }
    }
}
