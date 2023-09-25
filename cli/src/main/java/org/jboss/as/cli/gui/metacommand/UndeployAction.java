/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.gui.metacommand;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.jboss.as.cli.gui.CliGuiContext;

/**
 * Action for the undeploy menu selection.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class UndeployAction extends AbstractAction {

    private CliGuiContext cliGuiCtx;

    public UndeployAction(CliGuiContext cliGuiCtx) {
        super("Undeploy");
        this.cliGuiCtx = cliGuiCtx;
    }

    public void actionPerformed(ActionEvent e) {
        UndeployCommandDialog dialog = new UndeployCommandDialog(cliGuiCtx);
        dialog.setLocationRelativeTo(cliGuiCtx.getMainWindow());
        dialog.setVisible(true);
    }

}
