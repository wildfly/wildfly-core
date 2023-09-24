/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.gui.component;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;

/**
 * JTable to hold the server logs.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2014 Red Hat Inc.
 */
public class ServerLogsTable extends JTable {

    public ServerLogsTable() {
        setRowHeight(30);
        setAutoCreateRowSorter(true);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }
}
