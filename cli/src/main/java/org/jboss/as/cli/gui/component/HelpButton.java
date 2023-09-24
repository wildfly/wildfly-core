/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.gui.component;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;

/**
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class HelpButton extends JButton {

    private JScrollPane helpScroller;

    public HelpButton(String helpFile) {
        super("Help");
        try {
            readHelpFile(helpFile);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(HelpButton.this, "Unable to read " + helpFile);
            return;
        }

        addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                JOptionPane helpPane = new JOptionPane(helpScroller, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION);
                JDialog dialog = helpPane.createDialog(HelpButton.this, "Help");
                dialog.setResizable(true);
                dialog.setModal(false);
                dialog.setSize(dialog.getHeight(), helpScroller.getWidth() + 10);
                dialog.setVisible(true);
            }

        });
    }

    private void readHelpFile(String helpFile) throws IOException {
        InputStream in = getClass().getResourceAsStream("/help/" + helpFile);
        JEditorPane helpText = new JEditorPane();
        helpText.read(in, null);
        helpText.setEditable(false);
        helpScroller = new JScrollPane(helpText);
    }
}
