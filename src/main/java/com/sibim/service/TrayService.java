package com.sibim.service;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/** Manages the Windows system-tray icon for SIBIM. All AWT calls run on
 *  the AWT event thread via EventQueue.invokeLater — never from the FX thread. */
public final class TrayService {

    private static final Logger log = LoggerFactory.getLogger(TrayService.class);

    private static TrayIcon trayIcon;

    private TrayService() {}

    public static void install(Stage stage) {
        if (!SystemTray.isSupported()) return;

        java.awt.EventQueue.invokeLater(() -> {
            try {
                SystemTray tray = SystemTray.getSystemTray();
                BufferedImage img = buildTrayImage();
                trayIcon = new TrayIcon(img, "SIBIM — Bienes Municipales");
                trayIcon.setImageAutoSize(true);

                PopupMenu popup = new PopupMenu();
                MenuItem miRestore = new MenuItem("Abrir SIBIM");
                miRestore.addActionListener(e -> Platform.runLater(() -> restoreStage(stage)));
                MenuItem miExit = new MenuItem("Salir");
                miExit.addActionListener(e -> Platform.runLater(() -> {
                    tray.remove(trayIcon);
                    stage.close();
                    Platform.exit();
                }));
                popup.add(miRestore);
                popup.addSeparator();
                popup.add(miExit);
                trayIcon.setPopupMenu(popup);

                trayIcon.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        if (e.getClickCount() == 2)
                            Platform.runLater(() -> restoreStage(stage));
                    }
                });

                tray.add(trayIcon);

                Platform.runLater(() -> stage.iconifiedProperty().addListener((obs, wasMin, isMin) -> {
                    if (isMin) {
                        java.awt.EventQueue.invokeLater(() -> {
                            if (trayIcon != null)
                                trayIcon.displayMessage("SIBIM",
                                    "SIBIM sigue ejecutándose en la bandeja",
                                    TrayIcon.MessageType.INFO);
                        });
                        Platform.runLater(() -> stage.hide());
                    }
                }));

            } catch (Exception e) {
                log.warn("No se pudo instalar el ícono en la bandeja del sistema: {}", e.getMessage());
            }
        });
    }

    public static void notify(String title, String message) {
        java.awt.EventQueue.invokeLater(() -> {
            if (trayIcon != null)
                trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        });
    }

    public static void remove() {
        java.awt.EventQueue.invokeLater(() -> {
            if (trayIcon != null && SystemTray.isSupported()) {
                try { SystemTray.getSystemTray().remove(trayIcon); }
                catch (Exception ignored) {
                    log.debug("Could not remove tray icon from system tray", ignored);
                }
                trayIcon = null;
            }
        });
    }

    private static void restoreStage(Stage stage) {
        stage.show();
        stage.setIconified(false);
        stage.toFront();
    }

    private static BufferedImage buildTrayImage() {
        int size = 16;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(8, 145, 178));
        g.fillOval(0, 0, size, size);
        g.setColor(java.awt.Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        String letter = "S";
        int tx = (size - fm.stringWidth(letter)) / 2;
        int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(letter, tx, ty);
        g.dispose();
        return img;
    }
}
