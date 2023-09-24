/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.gui.component.ServerLogsTable;
import org.jboss.as.cli.gui.component.ServerLogsTableModel;
import org.jboss.as.cli.gui.metacommand.DownloadServerLogDialog;
import org.jboss.dmr.ModelNode;

/**
 * The main panel for listing the server logs.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2014 Red Hat Inc.
 */
public class ServerLogsPanel extends JPanel {

    private final ServerLogsTable table;
    private final ServerLogsTableModel tableModel;
    private final CliGuiContext cliGuiCtx;

    public ServerLogsPanel(final CliGuiContext cliGuiCtx) {
        this.cliGuiCtx = cliGuiCtx;

        setLayout(new BorderLayout());
        this.table = new ServerLogsTable();
        this.tableModel = new ServerLogsTableModel(cliGuiCtx, this.table);
        this.table.setModel(tableModel);
        this.tableModel.refresh();

        JScrollPane scroller = new JScrollPane(table);
        add(scroller, BorderLayout.CENTER);
        add(makeButtonPanel(), BorderLayout.SOUTH);
        // Add double click listener for table row
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                if (e.getClickCount() == 2) {
                    drawDownloadDialog();
                }
            }
        });
    }

    /**
     * Does the server support log downloads?
     *
     * @param cliGuiCtx The context.
     * @return <code>true</code> if the server supports log downloads,
     *         <code>false</code> otherwise.
     */
    public static boolean isLogDownloadAvailable(CliGuiContext cliGuiCtx) {
        ModelNode readOps = null;
        try {
            readOps = cliGuiCtx.getExecutor().doCommand("/subsystem=logging:read-children-types");
        } catch (CommandFormatException | IOException e) {
            return false;
        }
        if (!readOps.get("result").isDefined()) return false;
        for (ModelNode op:  readOps.get("result").asList()) {
            if ("log-file".equals(op.asString())) return true;
        }
        return false;
    }

    private JPanel makeButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(new DownloadButton());
        buttonPanel.add(new RefreshButton());
        return buttonPanel;
    }

    private String getSelectedFileName() {
        return (String)table.getValueAt(table.getSelectedRow(), 0);
    }

    private Long getSelectedFileSize() {
        return (Long)table.getValueAt(table.getSelectedRow(), 2);
    }

    private void drawDownloadDialog() {
        DownloadServerLogDialog dialog = new DownloadServerLogDialog(cliGuiCtx, getSelectedFileName(), getSelectedFileSize());
        dialog.setLocationRelativeTo(cliGuiCtx.getMainWindow());
        dialog.setVisible(true);
    }

    private class DownloadButton extends JButton {
        public DownloadButton() {
            super("Download");
            addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    drawDownloadDialog();
                }
            });
        }
    }

    private class RefreshButton extends JButton {
        public RefreshButton() {
            super("Refresh List");
            addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    tableModel.refresh();
                }
            });
        }
    }
}
