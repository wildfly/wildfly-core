/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.gui.component;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JRadioButton;
import org.jboss.as.cli.gui.CliGuiContext;
import org.jboss.dmr.ModelNode;

/**
 * A table model appropriate for deployments in a domain.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class DomainDeploymentTableModel extends StandaloneDeploymentTableModel {

    private CliGuiContext cliGuiCtx;

    public DomainDeploymentTableModel(CliGuiContext cliGuiCtx) {
        super(cliGuiCtx);
        this.cliGuiCtx = cliGuiCtx;
        colNames = new String[] {"Name", "Runtime Name", "Assigned Server Groups"};
        initializeServerGroups();
        setServerGroups();
    }

    private void initializeServerGroups() {
        for (Object[] deployment : data) {
            deployment[2] = new ArrayList<String>();
        }
    }

    private void setServerGroups() {
        ModelNode deploymentsQuery = null;
        String queryString = "/server-group=*/deployment=*/:read-resource";

        try {
            deploymentsQuery = cliGuiCtx.getExecutor().doCommand(queryString);
            if (deploymentsQuery.get("outcome").asString().equals("failed")) return;
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        for (ModelNode node : deploymentsQuery.get("result").asList()) {
            String serverGroup = node.get("address").asPropertyList().get(0).getValue().asString();
            ModelNode deploymentNode = node.get("result"); // get the inner result

            Object[] deployment = findDeployment(deploymentNode.get("name").asString());

            List<String> serverGroups = (List<String>)deployment[2];

            boolean enabled = deploymentNode.get("enabled").asBoolean();
            if (!enabled) serverGroup += " (disabled)";
            serverGroups.add(serverGroup);
        }
    }

    private Object[] findDeployment(String name) {
        for (Object[] deployment : data) {
            JRadioButton nameButton = (JRadioButton)deployment[0];
            if (nameButton.getText().equals(name)) return deployment;
        }

        throw new IllegalStateException("Deployment " + name + " exists in server group but not in content repository.");
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == 0) return JRadioButton.class;
        if (columnIndex == 2) return List.class;
        return String.class;
    }
}
