/*
 * Copyright 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
