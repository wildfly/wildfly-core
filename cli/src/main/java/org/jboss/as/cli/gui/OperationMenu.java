/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.gui;

import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.text.JTextComponent;
import org.jboss.as.cli.gui.ManagementModelNode.UserObject;
import org.jboss.as.cli.gui.metacommand.ExploreNodeAction;
import org.jboss.dmr.ModelNode;

/**
 * JPopupMenu that selects the available operations for a node address.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class OperationMenu extends JPopupMenu {
    private static final String[] genericOps = {"add", "read-operation-description", "read-resource-description", "read-operation-names"};
    private static final List<String> genericOpList = Arrays.asList(genericOps);
    private static final String[] leafOps = {"write-attribute", "undefine-attribute"};
    private static final List<String> leafOpList = Arrays.asList(leafOps);

    private CliGuiContext cliGuiCtx;
    private CommandExecutor executor;
    private JTree invoker;

    public OperationMenu(CliGuiContext cliGuiCtx, JTree invoker) {
        this.cliGuiCtx = cliGuiCtx;
        this.executor = cliGuiCtx.getExecutor();
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
        addExploreOption(node);

        String addressPath = node.addressPath();
        try {
            ModelNode  opNames = executor.doCommand(addressPath + ":read-operation-names");
            if (opNames.get("outcome").asString().equals("failed")) return;

            for (ModelNode name : opNames.get("result").asList()) {
                String strName = name.asString();

                // filter operations
                if (node.isGeneric() && !genericOpList.contains(strName)) continue;
                if (node.isLeaf() && !leafOpList.contains(strName)) continue;
                if (!node.isGeneric() && !node.isLeaf() && strName.equals("add")) continue;

                ModelNode opDescription = getResourceDescription(addressPath, strName);
                add(new OperationAction(node, strName, opDescription));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        super.show(invoker, x, y);
    }

    private void addExploreOption(ManagementModelNode node) {
        if (node.isLeaf()) return;
        add(new ExploreNodeAction(cliGuiCtx));
        addSeparator();
    }

    private ModelNode getResourceDescription(String addressPath, String name) {
        try {
            return executor.doCommand(addressPath + ":read-operation-description(name=\"" + name + "\")");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Action for a menu selection.  For operations with params, display an Operation Dialog.  For operations
     * without params, just construct the operation and set the command line.
     */
    private class OperationAction extends AbstractAction {

        private ManagementModelNode node;
        private String opName;
        private String addressPath;
        private ModelNode opDescription;
        private String strDescription; // help text

        public OperationAction(ManagementModelNode node, String opName, ModelNode opDescription) {
            super(opName);
            this.node = node;
            this.opName = opName;
            this.addressPath = node.addressPath();
            this.opDescription = opDescription;

            if (opDescription != null) {
                strDescription = opDescription.get("result", "description").asString();
                putValue(Action.SHORT_DESCRIPTION, strDescription);
            }
        }

        private boolean isNoArgOperation(ModelNode requestProperties) {
            // add operation has implicit 'name' param
            if (opName.equals("add")) return false;

            return (requestProperties == null) || (!requestProperties.isDefined()) || requestProperties.asList().isEmpty();
        }

        public void actionPerformed(ActionEvent ae) {
            JTextComponent cmdText = cliGuiCtx.getCommandLine().getCmdText();
            ModelNode requestProperties = opDescription.get("result", "request-properties");

            if (isNoArgOperation(requestProperties)) {
                cmdText.setText(addressPath + ":" + opName);
                cmdText.requestFocus();
                return;
            }

            if (node.isLeaf() && opName.equals("undefine-attribute")) {
                UserObject usrObj = (UserObject)node.getUserObject();
                cmdText.setText(addressPath + ":" + opName + "(name=" + usrObj.getName() + ")");
                cmdText.requestFocus();
                return;
            }

            OperationDialog dialog = new OperationDialog(cliGuiCtx, node, opName, strDescription, requestProperties);
            dialog.setLocationRelativeTo(cliGuiCtx.getMainWindow());
            dialog.setVisible(true);
        }

    }
}
