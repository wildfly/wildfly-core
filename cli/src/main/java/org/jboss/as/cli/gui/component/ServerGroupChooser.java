/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.gui.component;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import org.jboss.as.cli.gui.CliGuiContext;
import org.jboss.dmr.ModelNode;

/**
 * A Panel that reads and presents all of the server groups with checkboxes.
 * This is also handy for finding out if we are in standalone mode.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class ServerGroupChooser extends JPanel {

    private List<JCheckBox> serverGroups = new ArrayList<JCheckBox>();
    private JPanel serverGroupsPanel = new JPanel(new FlowLayout());

    public ServerGroupChooser(CliGuiContext cliGuiCtx) {
        setLayout(new BorderLayout());
        setBorder(new TitledBorder("Server Groups"));
        setServerGroups(cliGuiCtx);
        add(serverGroupsPanel, BorderLayout.CENTER);
    }

    private void setServerGroups(CliGuiContext cliGuiCtx) {
        Set<String> serverGroupNames = new TreeSet<String>();
        try {
            ModelNode serverGroupQuery = cliGuiCtx.getExecutor().doCommand("/:read-children-names(child-type=server-group)");
            if (serverGroupQuery.get("outcome").asString().equals("failed")) return;

            for (ModelNode node : serverGroupQuery.get("result").asList()) {
                serverGroupNames.add(node.asString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // make sorted server group names into checkboxes
        for (String name : serverGroupNames) {
            JCheckBox serverGroupCheckBox = new JCheckBox(name);
            serverGroups.add(serverGroupCheckBox);
            serverGroupsPanel.add(serverGroupCheckBox);
        }

    }

    /**
     * Return the command line argument
     *
     * @return  "  --server-groups=" plus a comma-separated list
     * of selected server groups.  Return empty String if none selected.
     */
    public String getCmdLineArg() {
        StringBuilder builder = new StringBuilder("  --server-groups=");
        boolean foundSelected = false;
        for (JCheckBox serverGroup : serverGroups) {
            if (serverGroup.isSelected()) {
                foundSelected = true;
                builder.append(serverGroup.getText());
                builder.append(",");
            }
        }
        builder.deleteCharAt(builder.length() - 1); // remove trailing comma

        if (!foundSelected) return "";
        return builder.toString();
    }

    @Override
    public void setEnabled(boolean isEnabled) {
        super.setEnabled(isEnabled);
        for (JCheckBox serverGroup : serverGroups) {
            serverGroup.setEnabled(isEnabled);
        }
    }
}
