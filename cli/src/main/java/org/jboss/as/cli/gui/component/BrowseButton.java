/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.gui.component;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JTextField;

/**
 * Standard Browse button.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2014 Red Hat Inc.
 */
public class BrowseButton extends JButton {

    private static final JFileChooser fileChooser = new JFileChooser(new File("."));

    public BrowseButton(final JDialog parentDialog, final JTextField targetField) {
        super("Browse ...");
        addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                int returnVal = fileChooser.showOpenDialog(parentDialog);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    try {
                        targetField.setText(fileChooser.getSelectedFile().getCanonicalPath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }
}
