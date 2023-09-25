/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.security.common;

import java.util.Map;

import org.jboss.dmr.ModelNode;
import org.wildfly.test.security.common.elytron.ModelNodeConvertable;

/**
 * Helper methods for {@link ModelNode} class.
 *
 * @author Josef Cacek
 */
public class ModelNodeUtil {

    /**
     * Set attribute of given node if the value is not-<code>null</code>.
     */
    public static void setIfNotNull(ModelNode node, String attribute, String value) {
        if (value != null) {
            node.get(attribute).set(value);
        }
    }

    /**
     * Set attribute of given node if the value is not-<code>null</code>.
     */
    public static void setIfNotNull(ModelNode node, String attribute, Boolean value) {
        if (value != null) {
            node.get(attribute).set(value);
        }
    }

    /**
     * Set attribute of given node if the value is not-<code>null</code>.
     */
    public static void setIfNotNull(ModelNode node, String attribute, Integer value) {
        if (value != null) {
            node.get(attribute).set(value);
        }
    }

    /**
     * Set list attribute of given node if the value is not-<code>null</code>.
     */
    public static void setIfNotNull(ModelNode node, String attribute, String... listValue) {
        if (listValue != null) {
            ModelNode listNode = node.get(attribute);
            for (String value : listValue) {
                listNode.add(value);
            }
        }
    }

    /**
     * Set list attribute of given node if the value is not-<code>null</code>.
     */
    public static void setIfNotNull(ModelNode node, String attribute, Map<String, String> objectValue) {
        if (objectValue != null) {
            ModelNode objectNode = node.get(attribute);
            for (Map.Entry<String, String> entry : objectValue.entrySet()) {
                objectNode.get(entry.getKey()).set(entry.getValue());
            }
        }
    }

    /**
     * Adds given items to list attribute.
     */
    public static void setIfNotNull(ModelNode node, String attribute, ModelNodeConvertable... items) {
        if (items != null && items.length > 0) {
            ModelNode listNode = node.get(attribute);
            for (ModelNodeConvertable item : items) {
                listNode.add(item.toModelNode());
            }
        }
    }

    /**
     * Set attribute of given node if the value is not-<code>null</code>.
     */
    public static void setIfNotNull(ModelNode node, String attribute, Long value) {
        if (value != null) {
            node.get(attribute).set(value.longValue());
        }
    }

    /**
     * Set attribute of given node if the value is not-<code>null</code>.
     */
    public static void setIfNotNull(ModelNode node, String attribute, ModelNodeConvertable value) {
        if (value != null) {
            ModelNode modelNode = value.toModelNode();
            if (modelNode != null) {
                node.get(attribute).set(modelNode);
            }
        }
    }
}
