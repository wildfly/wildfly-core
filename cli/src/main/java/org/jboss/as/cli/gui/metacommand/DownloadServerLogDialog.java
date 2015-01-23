/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.cli.gui.metacommand;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.gui.CliGuiContext;
import org.jboss.as.cli.gui.CommandExecutor.Response;
import org.jboss.as.controller.client.OperationResponse.StreamEntry;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;

/**
 * Dialog to choose destination file and download log.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2014 Red Hat Inc.
 */
public class DownloadServerLogDialog extends JDialog implements ActionListener, PropertyChangeListener {
    // make these static so that they always retains the last value chosen
    private static final JFileChooser fileChooser = new JFileChooser(new File("."));
    private static final JCheckBox viewInLogViewer = new JCheckBox("View in default log viewer");
    static {
        viewInLogViewer.setSelected(true);
    }

    private CliGuiContext cliGuiCtx;
    private String fileName;
    private Long fileSize;
    private JPanel inputPanel = new JPanel(new GridBagLayout());
    private JTextField pathField = new JTextField(40);

    private ProgressMonitor progressMonitor;
    private DownloadLogTask downloadTask;

    private boolean openInViewerSupported = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN);

    public DownloadServerLogDialog(CliGuiContext cliGuiCtx, String fileName, Long fileSize) {
        super(cliGuiCtx.getMainWindow(), "Download " + fileName, Dialog.ModalityType.APPLICATION_MODAL);
        this.cliGuiCtx = cliGuiCtx;
        this.fileName = fileName;
        this.fileSize = fileSize;

        fileChooser.setSelectedFile(new File(fileChooser.getCurrentDirectory(), fileName));
        setPathField();

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout(10, 10));

        contentPane.add(makeInputPanel(), BorderLayout.CENTER);

        contentPane.add(makeButtonPanel(), BorderLayout.SOUTH);
        pack();
        setResizable(false);
    }

    private void setPathField() {
        try {
            pathField.setText(fileChooser.getSelectedFile().getCanonicalPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private JPanel makeInputPanel() {
        GridBagConstraints gbConst = new GridBagConstraints();
        gbConst.anchor = GridBagConstraints.WEST;
        gbConst.insets = new Insets(5, 5, 5, 5);

        JLabel pathLabel = new JLabel("Download To:");
        gbConst.gridwidth = 1;
        inputPanel.add(pathLabel, gbConst);

        addStrut();
        inputPanel.add(pathField, gbConst);

        addStrut();
        JButton browse = new JButton("Browse ...");
        browse.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                int returnVal = fileChooser.showOpenDialog(DownloadServerLogDialog.this);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    setPathField();
                }
            }
        });
        gbConst.gridwidth = GridBagConstraints.REMAINDER;
        inputPanel.add(browse, gbConst);

        if (openInViewerSupported) {
            JLabel emptyLabel = new JLabel("");
            gbConst.gridwidth = 1;
            inputPanel.add(emptyLabel, gbConst);
            addStrut();
            gbConst.gridwidth = GridBagConstraints.REMAINDER;
            inputPanel.add(viewInLogViewer, gbConst);
        }

        return inputPanel;
    }

    private void addStrut() {
        inputPanel.add(Box.createHorizontalStrut(5));
    }

    private JPanel makeButtonPanel() {
        JPanel buttonPanel = new JPanel();

        JButton ok = new JButton("OK");
        ok.addActionListener(this);
        ok.setMnemonic(KeyEvent.VK_ENTER);

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                DownloadServerLogDialog.this.dispose();
            }
        });

        buttonPanel.add(ok);
        buttonPanel.add(cancel);
        return buttonPanel;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        String path = pathField.getText();
        if (path.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "A file path must be selected.", "Empty File Path", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File selectedFile = new File(path);
        if (selectedFile.exists()) {
            this.setVisible(false);
            int option = JOptionPane.showConfirmDialog(cliGuiCtx.getMainWindow(), "Overwrite " + path, "Overwrite?", JOptionPane.YES_NO_OPTION);
            if (option == JOptionPane.NO_OPTION) {
                this.setVisible(true);
                return;
            }
        }

        this.dispose();

        progressMonitor = new ProgressMonitor(cliGuiCtx.getMainWindow(), "Downloading " + fileName, "", 0, 100);
        progressMonitor.setProgress(0);
        downloadTask = new DownloadLogTask(selectedFile);
        downloadTask.addPropertyChangeListener(this);
        downloadTask.execute();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("progress".equals(evt.getPropertyName())){
            int percentRead = (Integer) evt.getNewValue();
            progressMonitor.setProgress(percentRead);
        }

        if ("bytesRead".equals(evt.getPropertyName())) {
            progressMonitor.setNote(evt.getNewValue() + " of " + fileSize + " bytes received.");
        }

        if (progressMonitor.isCanceled()) {
            downloadTask.cancel(false);
        }
    }

    class DownloadLogTask extends SwingWorker<Void, Void> {
        private final File selectedFile;

        public DownloadLogTask(File selectedFile) {
            this.selectedFile = selectedFile;
        }

        @Override
        public Void doInBackground() {
            try {
                String command = "/subsystem=logging/log-file=" + fileName + ":read-attribute(name=stream)";
                final Response response = cliGuiCtx.getExecutor().doCommandFullResponse(command);
                final ModelNode outcome = response.getDmrResponse();
                if (!Operations.isSuccessfulOutcome(outcome)) {
                    cancel(false);
                    String error = "Failure at server: " + Operations.getFailureDescription(outcome).asString();
                    JOptionPane.showMessageDialog(cliGuiCtx.getMainWindow(), error, "Download Failed", JOptionPane.ERROR_MESSAGE);
                    return null;
                }

                // Get the UUID of the stream
                final String uuid = Operations.readResult(outcome).asString();

                // Should only be a single entry
                final byte[] buffer = new byte[512];
                try (
                        final StreamEntry entry = response.getOperationResponse().getInputStream(uuid);
                        final InputStream in = entry.getStream();
                        final OutputStream out = Files.newOutputStream(selectedFile.toPath(), StandardOpenOption.CREATE);
                ) {
                    int bytesRead = 0;
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                        final int oldValue = bytesRead;
                        bytesRead += len;
                        firePropertyChange("bytesRead", oldValue, bytesRead);
                        setProgress(Math.max(Math.round(((float) bytesRead / (float) fileSize) * 100), 100));
                    }
                }
            } catch (IOException | CommandFormatException ex) {
                throw new RuntimeException(ex);
            } finally {

                if (isCancelled()) {
                    selectedFile.delete();
                }
            }

            return null;
        }

        @Override
        public void done() {
            String message = "Download " + fileName + " ";
            if (isCancelled()) {
                JOptionPane.showMessageDialog(cliGuiCtx.getMainWindow(), message + "cancelled.", message + "cancelled.", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (!viewInLogViewer.isSelected() || !openInViewerSupported) {
                JOptionPane.showMessageDialog(cliGuiCtx.getMainWindow(), message + "complete.");
                return;
            }

            try {
                Desktop.getDesktop().open(selectedFile);
            } catch (IOException ioe) {
                // try to open in file manager for destination directory
                try {
                    Desktop.getDesktop().open(fileChooser.getCurrentDirectory());
                } catch (IOException ioe2) {
                    JOptionPane.showMessageDialog(cliGuiCtx.getMainWindow(), "Download success.  No registered application to view " + fileName, "Can't view file.", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }
}
