/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.gui;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JPopupMenu;
import javax.swing.JTree;

/**
 * JPopupMenu that provides graphing for real time attributes.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class GraphingMenu extends JPopupMenu {
    private CliGuiContext cliGuiCtx;
    private JTree invoker;

    public GraphingMenu(CliGuiContext cliGuiCtx, JTree invoker) {
        this.cliGuiCtx = cliGuiCtx;
        this.invoker = invoker;
        setLightWeightPopupEnabled(true);
        setOpaque(true);
    }

    /**
     * Show the OperationMenu based on the selected node.
     * @param node The selected node.
     * @param x The x position of the selection.
     * @param y The y position of the selection.
     */
    public void show(ManagementModelNode node, int x, int y) {
        removeAll();
        add(new OperationAction(node, "Real Time Graph", "Plot this attribute in a real time 2D graph."));
        super.show(invoker, x, y);
    }

    /**
     * Action for a menu selection.  For operations with params, display an Operation Dialog.  For operations
     * without params, just construct the operation and set the command line.
     */
    private class OperationAction extends AbstractAction {

        private ManagementModelNode node;
        private String opName;
        private String addressPath;

        public OperationAction(ManagementModelNode node, String opName, String helpText) {
            super(opName);
            this.node = node;
            this.opName = opName;
            this.addressPath = node.addressPath();
            putValue(Action.SHORT_DESCRIPTION, helpText);
        }

        public void actionPerformed(ActionEvent ae) {
            // TODO what is this class meant to do?
            //System.out.println("selected menu item");
        }

    }
}
