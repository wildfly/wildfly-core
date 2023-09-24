/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.util;

import java.util.LinkedList;
import java.util.List;
import org.jboss.dmr.ModelNode;

/**
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
public class ModelUtil {

    public static List<String> modelNodeAsStingList(ModelNode node) {
        List<String> ret = new LinkedList<String>();
        for (ModelNode n : node.asList()) ret.add(n.asString());
        return ret;
    }

    public static ModelNode createCompositeNode(ModelNode[] steps) {
        ModelNode comp = new ModelNode();
        comp.get("operation").set("composite");
        for (ModelNode step : steps) {
            comp.get("steps").add(step);
        }
        return comp;
    }

    public static ModelNode createOpNode(String address, String operation) {
        ModelNode op = new ModelNode();

        // set address
        ModelNode list = op.get("address").setEmptyList();
        if (address != null) {
            String[] pathSegments = address.split("/");
            for (String segment : pathSegments) {
                String[] elements = segment.split("=");
                list.add(elements[0], elements[1]);
            }
        }
        op.get("operation").set(operation);
        return op;
    }
}
