/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.gui.metacommand;

import java.awt.event.ActionEvent;
import java.io.File;
import javax.swing.JFileChooser;
import org.jboss.as.cli.gui.CliGuiContext;
import org.jboss.as.cli.gui.component.ScriptMenu;

/**
 * Action that allows the user to choose a script from the file system.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class ChooseScriptAction extends ScriptAction {
    // make this static so that it always retains the last directory chosen
    private static JFileChooser fileChooser;

    public ChooseScriptAction(ScriptMenu menu, CliGuiContext cliGuiCtx) {
        super(menu, "Choose CLI Script", cliGuiCtx);
        putValue(SHORT_DESCRIPTION, "Choose a CLI script from the file system.");
    }

    public void actionPerformed(ActionEvent e) {
        // Do this here or it gets metal look and feel.  Not sure why.
        if (fileChooser == null) {
            fileChooser = new JFileChooser(new File("."));
        }

        int returnVal = fileChooser.showOpenDialog(cliGuiCtx.getMainPanel());
        if (returnVal != JFileChooser.APPROVE_OPTION) return;

        runScript(fileChooser.getSelectedFile());
    }

}
