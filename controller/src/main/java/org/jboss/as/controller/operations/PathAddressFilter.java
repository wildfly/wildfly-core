/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller.operations;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;

/**
 * @author Emanuel Muckenhuber
 */
public final class PathAddressFilter {

    public static final OperationContext.AttachmentKey<PathAddressFilter> KEY = OperationContext.AttachmentKey.create(PathAddressFilter.class);

    private final boolean accept;
    private final Node node = new Node(null);
    public PathAddressFilter(boolean accept) {
        this.accept = accept;
    }

    public boolean accepts(PathAddress address) {
        final Iterator<PathElement> i = address.iterator();
        Node node = this.node;
        while (i.hasNext()) {
            final PathElement element = i.next();
            final Node key = node.children.get(element.getKey());
            if (key == null) {
                return node.accept;
            }
            node = key.children.get(element.getValue());
            if (node == null) {
                node = key.children.get("*");
            }
            if (node == null) {
                return key.accept;
            }
            if (!i.hasNext()) {
                return node.accept;
            }
        }
        return accept;
    }

    public void addReject(final PathAddress address) {
        final Iterator<PathElement> i = address.iterator();
        Node node = this.node;
        while (i.hasNext()) {

            final PathElement element = i.next();
            final String elementKey = element.getKey();
            Node key = node.children.get(elementKey);
            if (key == null) {
                key = new Node(element.getKey());
                node.children.put(elementKey, key);
            }
            final String elementValue = element.getValue();
            Node value = key.children.get(elementValue);
            if (value == null) {
                value = new Node(elementValue);
                key.children.put(elementValue, value);
            }
            if (!i.hasNext()) {
                value.accept = false;
            }
        }
    }

    class Node {

        private final String name;
        private final Map<String, Node> children = new HashMap<>();
        private boolean accept = true;

        Node(String name) {
            this.name = name;
        }

    }

}
