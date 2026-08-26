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
        // Multi-resolution ICO (PNG-inside-ICO, supported Windows Vista+)
        writeIco(new File(outDir, "icon.ico"), imgs, sizes);
        System.out.println("OK  icon.ico");
        System.out.println("Iconos generados correctamente.");
    }

    /** Writes a multi-resolution ICO file embedding PNG-compressed images.
     *  Windows Vista+ reads PNG-inside-ICO natively, giving crisp icons at
     *  every DPI. */
    private static void writeIco(File out, BufferedImage[] imgs, int[] sizes) throws IOException {
        // Collect PNG bytes for each image
        byte[][] pngBytes = new byte[imgs.length][];
        for (int i = 0; i < imgs.length; i++) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(imgs[i], "PNG", bos);
            pngBytes[i] = bos.toByteArray();
        }

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(out))) {
            int count = imgs.length;
            // ICONDIR header (6 bytes)
            dos.writeShort(leShort(0));     // reserved
            dos.writeShort(leShort(1));     // type = 1 (ICO)
            dos.writeShort(leShort(count)); // image count

            // Image directory (16 bytes × count)
            int offset = 6 + 16 * count;
            for (int i = 0; i < count; i++) {
                int sz = sizes[i];
                dos.writeByte(sz == 256 ? 0 : sz); // width  (0 = 256)
                dos.writeByte(sz == 256 ? 0 : sz); // height (0 = 256)
                dos.writeByte(0);                   // color count
                dos.writeByte(0);                   // reserved
                dos.writeShort(leShort(1));          // planes
                dos.writeShort(leShort(32));         // bit count
                dos.writeInt(leInt(pngBytes[i].length));
                dos.writeInt(leInt(offset));
                offset += pngBytes[i].length;
            }
            // Image data
            for (byte[] png : pngBytes) dos.write(png);
        }
    }

    // ICO uses little-endian; DataOutputStream is big-endian — swap bytes.
    private static short leShort(int v) { return (short)(((v & 0xFF) << 8) | ((v >> 8) & 0xFF)); }
    private static int   leInt(int v) {
        return ((v & 0xFF) << 24) | (((v >> 8) & 0xFF) << 16) | (((v >> 16) & 0xFF) << 8) | ((v >> 24) & 0xFF);
    }

    private static BufferedImage render(int sz) {
        BufferedImage img = new BufferedImage(sz, sz, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,    RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,   RenderingHints.VALUE_COLOR_RENDER_QUALITY);

        // ── Background: indigo → violet gradient rounded square ──
        float arc = sz * 0.22f;
        RoundRectangle2D bg = new RoundRectangle2D.Float(0, 0, sz, sz, arc * 2, arc * 2);
        g.setPaint(new GradientPaint(0, 0, new Color(0x4F46E5), sz, sz, new Color(0x7C3AED)));
        g.fill(bg);

        // Clip so building silhouette never bleeds outside rounded corners
        g.setClip(bg);

        if (sz <= 32) {
            renderTiny(g, sz);
        } else {
            renderBuilding(g, sz);
        }

        g.dispose();
        return img;
    }

    /** Simplified silhouette for 16–32 px where fine detail would be noise. */
    private static void renderTiny(Graphics2D g, int sz) {
        g.setColor(Color.WHITE);
        double p  = sz * 0.18;
        double cw = sz - 2 * p;
        // Roof triangle
        Path2D roof = new Path2D.Double();
        roof.moveTo(sz / 2.0,      p);
        roof.lineTo(p,             sz * 0.48);
        roof.lineTo(sz - p,        sz * 0.48);
        roof.closePath();
        g.fill(roof);
        // Body + base
        double bx = p + cw * 0.12, bw = cw * 0.76;
        g.fill(new Rectangle2D.Double(bx, sz * 0.45, bw, sz * 0.38));
        g.fill(new Rectangle2D.Double(p,  sz * 0.83, cw, sz * 0.09));
    }

    /** Full building with pediment, wings, windows, and door. */
    private static void renderBuilding(Graphics2D g, int sz) {
        double cx   = sz / 2.0;
        double pad  = sz * 0.11;
        double totW = sz - 2 * pad;

        // Pediment (triangular roof)
        double roofTop = sz * 0.16;
        double roofBot = sz * 0.41;
        double roofHW  = totW * 0.46;
        Path2D roof = new Path2D.Double();
        roof.moveTo(cx,          roofTop);
        roof.lineTo(cx - roofHW, roofBot);
        roof.lineTo(cx + roofHW, roofBot);
        roof.closePath();

        // Centre body
        double bodyW = totW * 0.62;
        double bodyX = cx - bodyW / 2;
        double bodyY = roofBot - 1; // 1 px overlap for crisp edge
        double bodyH = sz * 0.37;

        // Side wings (shorter than body)
        double wingW = totW * 0.155;
        double wingY = bodyY + bodyH * 0.30;
        double wingH = bodyH * 0.70;
        double lWingX = pad;
        double rWingX = sz - pad - wingW;

        // Base step
        double baseH = sz * 0.055;
        double baseX = pad * 0.55;
        double baseW = sz - 2 * baseX;
        double baseY = bodyY + bodyH;

        // ── Draw white building ──
        g.setColor(Color.WHITE);
        g.fill(roof);
        g.fill(new Rectangle2D.Double(bodyX, bodyY, bodyW, bodyH));
        g.fill(new Rectangle2D.Double(lWingX, wingY, wingW, wingH));
        g.fill(new Rectangle2D.Double(rWingX, wingY, wingW, wingH));
        g.fill(new Rectangle2D.Double(baseX,  baseY, baseW, baseH));

        // ── Punch holes (door + windows) to expose gradient behind ──
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));

        // Door arch — centred, arched top
        double doorW = bodyW * 0.24;
        double doorH = bodyH * 0.50;
        double doorX = cx - doorW / 2;
        double doorY = bodyY + bodyH - doorH;
        g.fill(new RoundRectangle2D.Double(doorX, doorY, doorW, doorH, doorW * 0.9, doorW * 0.9));

        // Three windows in body
        double winW  = bodyW * 0.135;
        double winH  = bodyH * 0.25;
        double winY2 = bodyY + bodyH * 0.13;
        double gap   = (bodyW - 3 * winW) / 4;
        for (int i = 0; i < 3; i++) {
            double winX = bodyX + gap + i * (winW + gap);
            g.fill(new RoundRectangle2D.Double(winX, winY2, winW, winH, winW * 0.35, winW * 0.35));
        }

        // One small window on each wing
        if (sz >= 64) {
            double swinW = wingW * 0.50;
            double swinH = wingH * 0.28;
            double swinY = wingY + wingH * 0.20;
            double swinXL = lWingX + (wingW - swinW) / 2;
            double swinXR = rWingX + (wingW - swinW) / 2;
            g.fill(new RoundRectangle2D.Double(swinXL, swinY, swinW, swinH, swinW * 0.3, swinW * 0.3));
            g.fill(new RoundRectangle2D.Double(swinXR, swinY, swinW, swinH, swinW * 0.3, swinW * 0.3));
        }

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
    }
}
