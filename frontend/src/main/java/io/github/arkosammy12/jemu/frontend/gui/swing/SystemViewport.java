package io.github.arkosammy12.jemu.frontend.gui.swing;

import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

public class SystemViewport {

    private final JPanel viewportPanel;
    private Component systemDisplayPanel;

    public SystemViewport() {
        MigLayout viewportPanelLayout = new MigLayout(new LC().insets("0"));
        this.viewportPanel = new JPanel(viewportPanelLayout);
        this.viewportPanel.setFocusable(true);
        this.viewportPanel.setBackground(Color.BLACK);
        this.viewportPanel.setPreferredSize(new Dimension(960, this.viewportPanel.getHeight()));
        this.viewportPanel.addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                SwingUtilities.invokeLater(viewportPanel::requestFocusInWindow);
            }

        });

    }

    @ApiStatus.Internal
    JPanel getJPanel() {
        return this.viewportPanel;
    }

    public void setSystemDisplay(@Nullable Supplier<Component> displaySupplier) {
        SwingUtilities.invokeLater(() -> {
            if (this.systemDisplayPanel != null) {
                this.viewportPanel.remove(this.systemDisplayPanel);
                this.systemDisplayPanel = null;
            }

            if (displaySupplier != null) {
                this.systemDisplayPanel = displaySupplier.get();
                this.systemDisplayPanel.setFocusable(true);
                this.systemDisplayPanel.setBackground(Color.BLACK);
                this.systemDisplayPanel.setMinimumSize(new Dimension(0, 0));
                this.systemDisplayPanel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        SwingUtilities.invokeLater(systemDisplayPanel::requestFocusInWindow);
                    }
                });
                this.viewportPanel.add(this.systemDisplayPanel, "grow, push");
                SwingUtilities.invokeLater(this.systemDisplayPanel::requestFocusInWindow);
            }

            this.viewportPanel.revalidate();
            this.viewportPanel.repaint();

        });
    }

}
