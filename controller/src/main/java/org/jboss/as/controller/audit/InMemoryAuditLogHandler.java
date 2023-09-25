/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.audit;

import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.AS_VERSION;
import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.ERROR;
import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.METHOD_NAME;
import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.METHOD_PARAMETERS;
import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.METHOD_SIGNATURE;
import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.REMOTE_ADDRESS;
import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.TYPE;
import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.USER_ID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_MECHANISM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_UUID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ONLY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jboss.as.controller.OperationContext;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public final class InMemoryAuditLogHandler extends AuditLogHandler {

    public static final String OPERATION_DATE = "operation-date";
    private static final ModelNode UNDEFINED = new ModelNode();

    static {
        UNDEFINED.protect();
    }
    private static final String IN_MEMORY_FORMATTER_NAME = "in-memory-formatter";
    private final List<ModelNode> items;
    private int maxHistory;
    private final AuditLogItemFormatter myFormatter = new InMemoryFormatter();


    public InMemoryAuditLogHandler(String name, int maxHistory) {
        super(name, IN_MEMORY_FORMATTER_NAME, maxHistory);
        this.items = new ArrayList<>(maxHistory);
        this.maxHistory = maxHistory;
        setFormatter(myFormatter);
    }

    @Override
    public List<ModelNode> listLastEntries() {
        return Collections.unmodifiableList(items);
    }

    public void setMaxHistory(int maxHistory) {
        this.maxHistory = maxHistory;
        while (maxHistory < items.size()) {
            items.remove(0);
        }
    }

    @Override
    void setFormatter(AuditLogItemFormatter formatter) {
        super.setFormatter(myFormatter);
    }

    public AuditLogItemFormatter getFormatter() {
        return myFormatter;
    }

    @Override
    boolean isDifferent(AuditLogHandler other) {
        if (other instanceof InMemoryAuditLogHandler == false) {
            return true;
        }
        return getName().equals(other.getName());
    }

    private void addItem(ModelNode item) {
        if (items.size() == maxHistory) {
            items.remove(0);
        }
        items.add(item);
    }

    @Override
    void initialize() {
    }

    @Override
    void stop() {
        items.clear();
    }

    @Override
    void writeLogItem(String formattedItem) throws IOException {
    }

    private class InMemoryFormatter extends AuditLogItemFormatter {

        public static final String BOOTING = "booting";

        public InMemoryFormatter() {
            super(IN_MEMORY_FORMATTER_NAME, true, "", "yyyy-MM-dd hh:mm:ss");
        }

        @Override
        String formatAuditLogItem(AuditLogItem.ModelControllerAuditLogItem item) {
            ModelNode entry = new ModelNode().setEmptyObject();
            entry.get(TYPE).set(TYPE_CORE);
            addCommonFields(entry, item);
            entry.get(OUTCOME).set(item.getResultAction() == OperationContext.ResultAction.KEEP ? SUCCESS : FAILED);
            if (item.getOperations() != null && !item.getOperations().isEmpty()) {
                ModelNode operations = entry.get(OPERATIONS).setEmptyList();
                for (ModelNode op : item.getOperations()) {
                    operations.add(op);
                }
            }
            addItem(entry);
            return entry.asString();
        }

        @Override
        String formatAuditLogItem(AuditLogItem.JmxAccessAuditLogItem item) {
            ModelNode entry = new ModelNode();
            entry.get(TYPE).set(TYPE_JMX);
            addCommonFields(entry, item);
            entry.get(METHOD_NAME).set(item.getMethodName());
            entry.get(METHOD_SIGNATURE);
            for (String sig : item.getMethodSignature()) {
                entry.get(METHOD_SIGNATURE).add(sig);
            }
            entry.get(METHOD_PARAMETERS);
            for (Object param : item.getMethodParams()) {
                if (param != null && param.getClass().isArray()) {
                    Object[] arrayParams = (Object[]) param;
                    for (Object arrayParam : arrayParams) {
                        entry.get(METHOD_PARAMETERS).add(arrayParam == null ? UNDEFINED : new ModelNode(arrayParam.toString()));
                    }
                } else {
                    entry.get(METHOD_PARAMETERS).add(param == null ? UNDEFINED : new ModelNode(param.toString()));
                }
            }
            final Throwable throwable = item.getError();
            if (throwable != null) {
                //TODO include stack trace?
                entry.get(ERROR).set(throwable.getMessage());
            }
            addItem(entry);
            return entry.asString();
        }

        private void addCommonFields(ModelNode entry, AuditLogItem item) {
            StringBuilder buffer = new StringBuilder(20);
            appendDate(buffer, item);
            entry.get(OPERATION_DATE).set(buffer.toString());
            entry.get(READ_ONLY).set(item.isReadOnly());
            entry.get(BOOTING).set(item.isBooting());
            entry.get(AS_VERSION).set(item.getAsVersion());
            if (item.getUserId() != null) {
                entry.get(USER_ID).set(item.getUserId());
            }
            if (item.getDomainUUID() != null) {
                entry.get(DOMAIN_UUID).set(item.getDomainUUID());
            }
            if (item.getAccessMechanism() != null) {
                entry.get(ACCESS_MECHANISM).set(item.getAccessMechanism().toString());
            }
            if (item.getRemoteAddress() != null) {
                entry.get(REMOTE_ADDRESS).set(item.getRemoteAddress().toString());
            }
        }
    }
}
