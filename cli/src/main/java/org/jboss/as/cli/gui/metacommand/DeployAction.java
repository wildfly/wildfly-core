/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.gui.metacommand;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.jboss.as.cli.gui.CliGuiContext;

/**
 * Action for the deploy menu selection.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class DeployAction extends AbstractAction {

    private CliGuiContext cliGuiCtx;

    public DeployAction(CliGuiContext cliGuiCtx) {
        super("Deploy");
        this.cliGuiCtx = cliGuiCtx;
    }

    public void actionPerformed(ActionEvent e) {
        DeployCommandDialog dialog = new DeployCommandDialog(cliGuiCtx);
        dialog.setLocationRelativeTo(cliGuiCtx.getMainWindow());
        dialog.setVisible(true);
    }

}
