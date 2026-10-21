package com.sibim.util;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The title bar and the border of every SIBIM window in the institutional
 * guinda, with white text, instead of Windows' generic white bar. Done through
 * Windows 11's own window attributes (DWM), so the window keeps everything a
 * native one does — snapping to half the screen, maximizing, resizing from any
 * edge — which a title bar drawn by the app itself would have to imitate.
 * On Windows 10 and other systems it does nothing: the bar stays as it was.
 */
public final class VentanaWindows {

    private static final Logger log = LoggerFactory.getLogger(VentanaWindows.class);

    private VentanaWindows() {}

    private interface Dwm extends Library {
        int DwmSetWindowAttribute(WinDef.HWND hwnd, int atributo, IntByReference valor, int tamano);
    }

    private static final int DWMWA_BORDER_COLOR = 34, DWMWA_CAPTION_COLOR = 35, DWMWA_TEXT_COLOR = 36;

    /** The bar: guinda oscuro (sibim-marca-dark), like the dialogs' headers. */
    static final String BARRA = AppColors.PRIMARY_D, TEXTO = "#FFFFFF";

    private static volatile Dwm dwm;
    private static volatile boolean instalado;

    /** COLORREF is 0x00BBGGRR. */
    static int colorref(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return (b << 16) | (g << 8) | r;
    }

    /** Colors every window SIBIM opens from now on, the main one and each
     *  dialog. Call once, on the FX thread, at startup. */
    public static void instalar() {
        if (instalado || !System.getProperty("os.name", "").toLowerCase().startsWith("windows")) return;
        instalado = true;
        try {
            dwm = Native.load("dwmapi", Dwm.class);
        } catch (Throwable t) {
            log.debug("Sin dwmapi: la barra de título queda como la de Windows", t);
            return;
        }
        Window.getWindows().addListener((ListChangeListener<Window>) c -> {
            while (c.next()) if (c.wasAdded()) Platform.runLater(VentanaWindows::colorearVentanas);
        });
        Platform.runLater(VentanaWindows::colorearVentanas);
    }

    /** Every visible window of this process that has a title bar (tooltips and
     *  menus are windows too, without one: they are left alone). Coloring one
     *  twice changes nothing. */
    private static void colorearVentanas() {
        Dwm d = dwm;
        if (d == null) return;
        try {
            int pid = Kernel32.INSTANCE.GetCurrentProcessId();
            User32.INSTANCE.EnumWindows((hwnd, data) -> {
                IntByReference duenio = new IntByReference();
                User32.INSTANCE.GetWindowThreadProcessId(hwnd, duenio);
                if (duenio.getValue() == pid && User32.INSTANCE.IsWindowVisible(hwnd)
                        && (User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE) & WinUser.WS_CAPTION) == WinUser.WS_CAPTION) {
                    d.DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR, new IntByReference(colorref(BARRA)), 4);
                    d.DwmSetWindowAttribute(hwnd, DWMWA_TEXT_COLOR, new IntByReference(colorref(TEXTO)), 4);
                    d.DwmSetWindowAttribute(hwnd, DWMWA_BORDER_COLOR, new IntByReference(colorref(BARRA)), 4);
                }
                return true;
            }, null);
        } catch (Throwable t) {
            log.debug("No se pudo colorear la barra de título", t);
        }
    }
}
