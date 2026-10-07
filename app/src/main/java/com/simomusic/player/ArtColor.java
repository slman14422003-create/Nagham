package com.simomusic.player;

/**
 * Picks one vivid accent colour from a cover (like the system media controls do): a hue histogram weighted by
 * saturation and brightness, so a big grey or black background does not win over the colourful subject.
 * Pure Java (works on an ARGB pixel array) so it is unit-tested on a plain JVM.
 */
public final class ArtColor {
    private ArtColor() {
    }

    /** @return opaque ARGB colour, or 0 when the cover is basically grey / black / white. */
    public static int dominant(int[] px, int n) {
        final int BINS = 24;
        double[] w = new double[BINS], r = new double[BINS], g = new double[BINS], b = new double[BINS];
        double total = 0;
        float[] hsv = new float[3];
        for (int i = 0; i < n; i++) {
            int c = px[i];
            if ((c >>> 24) < 128) continue;
            rgb2hsv((c >> 16) & 255, (c >> 8) & 255, c & 255, hsv);
            double wt = hsv[1] * hsv[1] * (hsv[2] < 0.2f ? 0 : hsv[2]);
            if (wt <= 0) continue;
            int bin = Math.min(BINS - 1, (int) (hsv[0] / 360f * BINS));
            w[bin] += wt;
            r[bin] += wt * ((c >> 16) & 255);
            g[bin] += wt * ((c >> 8) & 255);
            b[bin] += wt * (c & 255);
            total += wt;
        }
        if (n == 0 || total < n * 0.02) return 0;
        int best = 0;
        double bestW = -1;
        for (int i = 0; i < BINS; i++) {                      // smooth over the two neighbours so a hue split across bins still wins
            double s = w[i] + 0.5 * (w[(i + 1) % BINS] + w[(i + BINS - 1) % BINS]);
            if (s > bestW) {
                bestW = s;
                best = i;
            }
        }
        double rr = 0, gg = 0, bb = 0, ww = 0;
        for (int d = -1; d <= 1; d++) {
            int k = (best + d + BINS) % BINS;
            rr += r[k];
            gg += g[k];
            bb += b[k];
            ww += w[k];
        }
        if (ww <= 0) return 0;
        rgb2hsv((int) (rr / ww), (int) (gg / ww), (int) (bb / ww), hsv);
        hsv[1] = Math.max(hsv[1], 0.50f);
        hsv[2] = Math.max(0.62f, Math.min(hsv[2], 0.95f));
        return 0xFF000000 | hsv2rgb(hsv[0], hsv[1], hsv[2]);
    }

    static void rgb2hsv(int r, int g, int b, float[] out) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float mx = Math.max(rf, Math.max(gf, bf)), mn = Math.min(rf, Math.min(gf, bf)), d = mx - mn, h;
        if (d == 0) h = 0;
        else if (mx == rf) h = 60f * (((gf - bf) / d) % 6f);
        else if (mx == gf) h = 60f * (((bf - rf) / d) + 2f);
        else h = 60f * (((rf - gf) / d) + 4f);
        if (h < 0) h += 360f;
        out[0] = h;
        out[1] = mx == 0 ? 0 : d / mx;
        out[2] = mx;
    }

    static int hsv2rgb(float h, float s, float v) {
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2f - 1)), m = v - c, r, g, b;
        int sector = (int) (h / 60f) % 6;
        switch (sector) {
            case 0: r = c; g = x; b = 0; break;
            case 1: r = x; g = c; b = 0; break;
            case 2: r = 0; g = c; b = x; break;
            case 3: r = 0; g = x; b = c; break;
            case 4: r = x; g = 0; b = c; break;
            default: r = c; g = 0; b = x; break;
        }
        return (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }
}
