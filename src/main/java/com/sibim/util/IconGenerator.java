package com.sibim.util;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/** One-shot utility — generates app icon PNGs at all required sizes.
 *  Run once when the icon design changes:
 *  mvn exec:java -Dexec.mainClass=com.sibim.util.IconGenerator */
public class IconGenerator {

    public static void main(String[] args) throws IOException {
        String outDir = "src/main/resources/img";
        new File(outDir).mkdirs();
        int[] sizes = {16, 32, 48, 64, 128, 256};
        BufferedImage[] imgs = new BufferedImage[sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            imgs[i] = render(sizes[i]);
            File out = new File(outDir, "icon-" + sizes[i] + ".png");
            ImageIO.write(imgs[i], "PNG", out);
            System.out.printf("OK  %s%n", out.getPath());
        }
        writeIco(new File(outDir, "icon.ico"), imgs, sizes);
        System.out.println("OK  icon.ico");
        System.out.println("Iconos generados correctamente.");
    }

    private static void writeIco(File out, BufferedImage[] imgs, int[] sizes) throws IOException {
        byte[][] pngBytes = new byte[imgs.length][];
        for (int i = 0; i < imgs.length; i++) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(imgs[i], "PNG", bos);
            pngBytes[i] = bos.toByteArray();
        }
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(out))) {
            int count = imgs.length;
            dos.writeShort(leShort(0));
            dos.writeShort(leShort(1));
            dos.writeShort(leShort(count));
            int offset = 6 + 16 * count;
            for (int i = 0; i < count; i++) {
                int sz = sizes[i];
                dos.writeByte(sz == 256 ? 0 : sz);
                dos.writeByte(sz == 256 ? 0 : sz);
                dos.writeByte(0);
                dos.writeByte(0);
                dos.writeShort(leShort(1));
                dos.writeShort(leShort(32));
                dos.writeInt(leInt(pngBytes[i].length));
                dos.writeInt(leInt(offset));
                offset += pngBytes[i].length;
            }
            for (byte[] png : pngBytes) dos.write(png);
        }
    }

    private static short leShort(int v) { return (short)(((v & 0xFF) << 8) | ((v >> 8) & 0xFF)); }
    private static int   leInt(int v) {
        return ((v & 0xFF) << 24) | (((v >> 8) & 0xFF) << 16) | (((v >> 16) & 0xFF) << 8) | ((v >> 24) & 0xFF);
    }

    private static BufferedImage render(int sz) {
        BufferedImage img = new BufferedImage(sz, sz, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,    RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,       RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,  RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        float arc = sz * 0.22f;
        RoundRectangle2D bg = new RoundRectangle2D.Float(0, 0, sz, sz, arc * 2, arc * 2);

        // ── 3-stop diagonal gradient: near-black indigo → dark indigo → vivid violet ──
        float[] bgFrac = {0f, 0.42f, 1f};
        Color[] bgCol  = {new Color(0x0F0E1A), new Color(0x1E1B4B), new Color(0x5B21B6)};
        g.setPaint(new LinearGradientPaint(0, 0, sz, sz, bgFrac, bgCol));
        g.fill(bg);

        // ── Top-left radial sheen (subtle specular highlight) ──
        float[] sheenFrac = {0f, 1f};
        Color[] sheenCol  = {new Color(255, 255, 255, 52), new Color(255, 255, 255, 0)};
        g.setPaint(new RadialGradientPaint(
            new Point2D.Float(sz * 0.18f, sz * 0.14f), sz * 0.60f, sheenFrac, sheenCol));
        g.fill(bg);

        // Clip so building never bleeds outside rounded corners
        g.setClip(bg);

        if (sz <= 16) {
            renderTiny(g, sz);
        } else {
            renderBuilding(g, sz);
        }

        g.dispose();
        return img;
    }

    /** Simplified letter mark for 16–32 px where building detail is noise. */
    private static void renderTiny(Graphics2D g, int sz) {
        Font font = new Font("SansSerif", Font.BOLD, (int)(sz * 0.60));
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        String s = "S";
        int tw = fm.stringWidth(s);
        int x  = (sz - tw) / 2;
        int y  = (sz + fm.getAscent() - fm.getDescent()) / 2;
        // Subtle depth shadow
        g.setColor(new Color(0, 0, 0, 65));
        g.drawString(s, x + 1, y + 1);
        g.setColor(Color.WHITE);
        g.drawString(s, x, y);
    }

    /** Full building silhouette with dome, pediment, wings, windows, and door. */
    private static void renderBuilding(Graphics2D g, int sz) {
        double cx   = sz / 2.0;
        double pad  = sz * 0.10;
        double totW = sz - 2 * pad;

        // Pediment (triangular roof)
        double roofTop = sz * 0.20;
        double roofBot = sz * 0.42;
        double roofHW  = totW * 0.44;
        Path2D roof = new Path2D.Double();
        roof.moveTo(cx,          roofTop);
        roof.lineTo(cx - roofHW, roofBot);
        roof.lineTo(cx + roofHW, roofBot);
        roof.closePath();

        // Centre body
        double bodyW = totW * 0.60;
        double bodyX = cx - bodyW / 2;
        double bodyY = roofBot - 1;
        double bodyH = sz * 0.35;

        // Side wings
        double wingW = totW * 0.15;
        double wingY = bodyY + bodyH * 0.28;
        double wingH = bodyH * 0.72;
        double lWingX = pad;
        double rWingX = sz - pad - wingW;

        // Base step
        double baseH = sz * 0.052;
        double baseX = pad * 0.50;
        double baseW = sz - 2 * baseX;
        double baseY = bodyY + bodyH;

        // Dome on pediment peak
        double domeW = totW * 0.17;
        double domeH = sz * 0.075;
        double domeX = cx - domeW / 2;
        double domeY = roofTop - domeH;

        // ── Draw white building ──
        g.setColor(Color.WHITE);
        // Dome
        g.fill(new Ellipse2D.Double(domeX, domeY, domeW, domeH));
        // Slender stem connecting dome base to pediment apex
        double stemW = Math.max(1.5, sz * 0.022);
        g.fill(new Rectangle2D.Double(cx - stemW / 2, roofTop - domeH * 0.55, stemW, domeH * 0.55));
        // Main silhouette
        g.fill(roof);
        g.fill(new Rectangle2D.Double(bodyX, bodyY, bodyW, bodyH));
        g.fill(new Rectangle2D.Double(lWingX, wingY, wingW, wingH));
        g.fill(new Rectangle2D.Double(rWingX, wingY, wingW, wingH));
        g.fill(new Rectangle2D.Double(baseX,  baseY, baseW, baseH));

        // ── Punch holes (door + windows) to expose gradient behind ──
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));

        // Door arch — centred
        double doorW = bodyW * 0.23;
        double doorH = bodyH * 0.48;
        double doorX = cx - doorW / 2;
        double doorY = bodyY + bodyH - doorH;
        g.fill(new RoundRectangle2D.Double(doorX, doorY, doorW, doorH, doorW * 0.9, doorW * 0.9));

        // Three windows in body
        double winW = bodyW * 0.13;
        double winH = bodyH * 0.24;
        double winY = bodyY + bodyH * 0.12;
        double gap  = (bodyW - 3 * winW) / 4;
        for (int i = 0; i < 3; i++) {
            double winX = bodyX + gap + i * (winW + gap);
            g.fill(new RoundRectangle2D.Double(winX, winY, winW, winH, winW * 0.35, winW * 0.35));
        }

        // Wing windows (only visible at ≥64 px)
        if (sz >= 64) {
            double sw = wingW * 0.48, sh = wingH * 0.26;
            double sy = wingY + wingH * 0.18;
            g.fill(new RoundRectangle2D.Double(lWingX + (wingW - sw) / 2, sy, sw, sh, sw * 0.3, sw * 0.3));
            g.fill(new RoundRectangle2D.Double(rWingX + (wingW - sw) / 2, sy, sw, sh, sw * 0.3, sw * 0.3));
        }

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
    }
}
