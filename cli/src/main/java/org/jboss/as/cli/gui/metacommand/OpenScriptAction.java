/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.gui.metacommand;

import java.awt.event.ActionEvent;
import java.io.File;
import org.jboss.as.cli.gui.CliGuiContext;
import org.jboss.as.cli.gui.component.ScriptMenu;

/**
 * Action that allows user to run a previously-run script.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class OpenScriptAction extends ScriptAction {

    private File file;

    public OpenScriptAction(ScriptMenu menu, CliGuiContext cliGuiCtx, File file) {
        super(menu, file.getName(), cliGuiCtx);
        this.file = file;
        putValue(SHORT_DESCRIPTION, "Run " + file.getAbsolutePath());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        runScript(file);
    }
}
