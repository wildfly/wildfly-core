/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.gui;

import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JTabbedPane;
import javax.swing.text.BadLocationException;
import org.jboss.as.cli.gui.component.CLIOutput;

/**
 * This class executes whatever command is on the command line.
 * It displays the result in the Output tab and sets "Output" to
 * be the currently selected tab.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class DoOperationActionListener extends AbstractAction {

    private CliGuiContext cliGuiCtx;

    private CLIOutput output;

    private LinkedList<String> cmdHistory = new LinkedList<String>();

    public DoOperationActionListener(CliGuiContext cliGuiCtx) {
        this.cliGuiCtx = cliGuiCtx;
        this.output = cliGuiCtx.getOutput();
    }

    public void actionPerformed(ActionEvent ae) {
        String command = cliGuiCtx.getCommandLine().getCmdText().getText();
        try {
            cmdHistory.push(command);
            CommandExecutor.Response response = cliGuiCtx.getExecutor().doCommandFullResponse(command);
            postOutput(response);
        } catch (Exception e) {
            output.postCommandWithResponse(command, e.getMessage());
        } finally {
            JTabbedPane tabs = cliGuiCtx.getTabs();
            tabs.setSelectedIndex(tabs.getTabCount() - 1); // set to Output tab to view the output
        }
    }

    public List getCmdHistory() {
        return Collections.unmodifiableList(this.cmdHistory);
    }

    private void postOutput(CommandExecutor.Response response) throws BadLocationException {
        boolean verbose = cliGuiCtx.getCommandLine().isVerbose();
        if (verbose) {
            postVerboseOutput(response);
        } else {
            output.postCommandWithResponse(response.getCommand(), response.getDmrResponse().toString());
        }
    }

    private void postVerboseOutput(CommandExecutor.Response response) throws BadLocationException {
        output.postAttributed(response.getDmrResponse().toString() + "\n\n", null);
        output.postAttributed(response.getDmrRequest().toString() + "\n\n", null);
        output.postBold(response.getCommand() + "\n");
    }

}
