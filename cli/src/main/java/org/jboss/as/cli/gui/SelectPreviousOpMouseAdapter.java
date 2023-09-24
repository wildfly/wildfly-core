/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.gui;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Utilities;
import org.jboss.as.cli.gui.component.CLIOutput;

/**
 * This MouseAdapter lets you double-click in the Output tab to select a previously-run command.  It then copies
 * the command to the command line and the system clipboard.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class SelectPreviousOpMouseAdapter extends MouseAdapter implements ClipboardOwner {
    private CliGuiContext cliGuiCtx;
    private CLIOutput output;
    private DoOperationActionListener opListener;
    private Clipboard systemClipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

    public SelectPreviousOpMouseAdapter(CliGuiContext cliGuiCtx, DoOperationActionListener opListener) {
        this.cliGuiCtx = cliGuiCtx;
        this.output = cliGuiCtx.getOutput();
        this.opListener = opListener;
    }

    @Override
    public void mouseClicked(MouseEvent me) {
        if (me.getClickCount() < 2) return;

        int pos = output.viewToModel(me.getPoint());

        try {
            int rowStart = Utilities.getRowStart(output, pos);
            int rowEnd = Utilities.getRowEnd(output, pos);
            String line = output.getDocument().getText(rowStart, rowEnd - rowStart);
            if (opListener.getCmdHistory().contains(line)) {
                output.select(rowStart, rowEnd);
                cliGuiCtx.getCommandLine().getCmdText().setText(line);
                systemClipboard.setContents(new StringSelection(line), this);
            }
        } catch (BadLocationException e) {
            e.printStackTrace();
        }

    }

    public void lostOwnership(Clipboard clpbrd, Transferable t) {
        // do nothing
    }

}
