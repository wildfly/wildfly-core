/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.notification;

import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;

/**
 * A notification emitted by a resource and handled by {@link NotificationHandler}.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2013 Red Hat inc.
 */
public class Notification {

    public static final String SOURCE = "source";
    public static final String TYPE = "type";
    public static final String MESSAGE = "message";
    public static final String TIMESTAMP = "timestamp";
    public static final String DATA = "data";

    private final String type;
    private final PathAddress source;
    private final String message;
    private final long timestamp;
    private final ModelNode data;

    public Notification(String type, PathAddress source, String message) {
        this(type, source, message, null);
    }

    /**
     *
     * @param data can be {@code null}
     */
    public Notification(String type, PathAddress source, String message, ModelNode data) {
        this(type, source, message, System.currentTimeMillis(), data);
    }

    private Notification(String type, PathAddress source, String message, long timestamp, ModelNode data) {
        this.type = type;
        this.source = source;
        this.message = message;
        this.timestamp = timestamp;
        this.data = data;
    }

    /**
     * @return the type of notification
     */
    public String getType() {
        return type;
    }

    /**
     * @return the address of the resource that emitted the notification (its source)
     */
    public PathAddress getSource() {
        return source;
    }

    /**
     * @return a human-readable i18end description of the notification
     */
    public String getMessage() {
        return message;
    }

    /**
     * The timestamp is set when the notification is instantiaged.
     *
     * @return the timestamp (in ms since the Unix Epoch) of the notification.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @return data contextual to the notification or {@code null}
     */
    public ModelNode getData() {
        return data;
    }

    /**
     * @return a detyped representation of the notification
     */
    public ModelNode toModelNode() {
        ModelNode node = new ModelNode();
        node.get(TYPE).set(type);
        node.get(SOURCE).set(source.toModelNode());
        node.get(TIMESTAMP).set(timestamp);
        node.get(MESSAGE).set(message);
        if (data != null) {
            node.get(DATA).set(data);
        }
        node.protect();
        return node;
    }

    /**
     * @return a Notification created from the detyped representation in {@code node}
     */
    public static Notification fromModelNode(ModelNode node) {
        String type = node.require(TYPE).asString();
        PathAddress source = PathAddress.pathAddress(node.require(SOURCE));
        long timestamp = node.require(TIMESTAMP).asLong();
        String message = node.require(MESSAGE).asString();
        ModelNode data = node.hasDefined(DATA)? node.get(DATA): null;
        return new Notification(type, source, message, timestamp, data);
    }

    @Override
    public String toString() {
        return "Notification{" +
                "type='" + type + '\'' +
                ", source=" + source +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                ", data=" + data +
                '}';
    }
}
