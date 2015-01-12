/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package org.jboss.as.cli.gui.component;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.RowSorter;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import org.jboss.as.cli.gui.CliGuiContext;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.dmr.ModelNode;

/**
 * The TableModel for the server logs.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2014 Red Hat Inc.
 */
public class ServerLogsTableModel extends AbstractTableModel {
    private static final char[] SIZE_CHARS = {'K', 'M', 'G', 'T', 'P', 'E'};
    private CliGuiContext cliGuiCtx;
    private List<ModelNode> allLogs;
    private ServerLogsTable table;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final TableCellRenderer dateRenderer = new DefaultTableCellRenderer() {
        @Override
        protected void setValue(final Object value) {
            final DateFormat formatter = DateFormat.getDateTimeInstance();
            setText(value == null ? "" : formatter.format(value));
        }
    };
    private final DefaultTableCellRenderer sizeRenderer = new DefaultTableCellRenderer() {
        protected void setValue(final Object value) {
            final String result;
            if (value instanceof Long) {
                final long len = (long) value;
                final int unit = 1000;
                if (len < unit) {
                    result = len + "B";
                } else {
                    final int exp = (int) (Math.log(len) / Math.log(unit));
                    result = String.format("%.1f %sB", len / Math.pow(unit, exp), SIZE_CHARS[exp - 1]);
                }
            } else {
                result = "";
            }
            setText(result);
        }
    };

    protected final String[] colNames = new String[] {"File", "Last Modified", "Size"};

    public ServerLogsTableModel(CliGuiContext cliGuiCtx, ServerLogsTable table) {
        this.cliGuiCtx = cliGuiCtx;
        this.table = table;
        sizeRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
    }

    /**
     * Initializes the model
     */
    private void init() {
        if (initialized.compareAndSet(false, true)) {
            final RowSorter<? extends TableModel> rowSorter = table.getRowSorter();
            rowSorter.toggleSortOrder(1); // sort by date
            rowSorter.toggleSortOrder(1); // descending
            final TableColumnModel columnModel = table.getColumnModel();
            columnModel.getColumn(1).setCellRenderer(dateRenderer);
            columnModel.getColumn(2).setCellRenderer(sizeRenderer);
        }
    }

    public void refresh() {
        try {
            init();
            this.allLogs = getLogFiles();
            fireTableDataChanged();
            if (!allLogs.isEmpty()) table.setRowSelectionInterval(0, 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getRowCount() {
        if (allLogs == null) return 0;
        return allLogs.size();
    }

    @Override
    public int getColumnCount() {
        return colNames.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ModelNode row = allLogs.get(rowIndex);
        if (columnIndex == 0) return row.get("file-name").asString();
        if (columnIndex == 1) {
            return new Date(row.get("last-modified-time").asLong());
        }

        // column 2
        if (columnIndex == 2) {
            return row.get("file-size").asLong();
        }
        return null;
    }

    @Override
    public String getColumnName(int column) {
        return colNames[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == 1) {
            return Date.class;
        } else if (columnIndex == 2) {
            return Long.class;
        }
        return String.class;
    }

    private List<ModelNode> getLogFiles() throws IOException {
        final ModelControllerClient client = cliGuiCtx.getCommmandContext().getModelControllerClient();
        final ModelNode address = new ModelNode().setEmptyList();
        address.add("subsystem", "logging");
        // address.add("log-file", "*");
        final ModelNode op = Operations.createOperation("read-children-names", address);
        op.get("child-type").set("log-file");
        ModelNode response = client.execute(op);
        if (Operations.isSuccessfulOutcome(response)) {
            ModelNode result = Operations.readResult(response);
            final Collection<String> files = new ArrayList<>();
            final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
            for (ModelNode file : result.asList()) {
                files.add(file.asString());
                builder.addStep(Operations.createReadAttributeOperation(address.clone().add("log-file", file.asString()), "file-size"))
                        .addStep(Operations.createReadAttributeOperation(address.clone().add("log-file", file.asString()), "last-modified-time"));
            }
            result = client.execute(builder.build());
            ModelNode fileListing = new ModelNode().setEmptyList();
            if (Operations.isSuccessfulOutcome(result)) {
                final List<ModelNode> attributes = Operations.readResult(result).asList();
                int i = 0;
                if (attributes.size() != (files.size() * 2)) {
                    throw new IllegalStateException("Error occurred reading the file attributes");
                }
                // Each file result will have two step results from the composite operation
                for (String file : files) {
                    final ModelNode node = new ModelNode();
                    node.get("file-name").set(file);
                    node.get("file-size").set(Operations.readResult(attributes.get(i++).get(0)));
                    node.get("last-modified-time").set(Operations.readResult(attributes.get(i++).get(0)));
                    fileListing.add(node);
                }
            }
            return fileListing.asList();
        }
        throw new RuntimeException(Operations.getFailureDescription(response).asString());
    }

}
