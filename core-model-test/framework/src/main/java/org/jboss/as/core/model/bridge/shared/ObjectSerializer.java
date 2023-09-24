/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.bridge.shared;

import java.io.IOException;

import org.jboss.as.core.model.bridge.impl.ObjectSerializerImpl;

/**
 * This interface will only be loaded up by the app classloader. It is used by both the app and the childfirst classloaders,
 * hence the use of Object for parameters
 *
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface ObjectSerializer {

    byte[] serializeModelNode(Object object) throws IOException;

    Object deserializeModelNode(byte[] object) throws IOException;

    String serializeModelVersion(Object object);

    Object deserializeModelVersion(String object);

    byte[] serializeLegacyModelInitializerEntry(Object object) throws IOException;

    Object deserializeLegacyModelInitializerEntry(byte[] object) throws IOException;

    byte[] serializeIgnoreDomainTypeResource(Object object) throws IOException;

    Object deserializeIgnoreDomainTypeResource(byte[] object) throws IOException;

    byte[] serializeModelTestOperationValidatorFilter(Object object) throws IOException;

    Object deserializeModelTestOperationValidatorFilter(byte[] object) throws IOException, ClassNotFoundException;

    public static class FACTORY {
        public static ObjectSerializer createSerializer(ClassLoader classLoader) {
            try {
                Class<?> clazz = classLoader.loadClass(ObjectSerializerImpl.class.getName());
                return (ObjectSerializer)clazz.newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }
}
