package com.musicdiscs.scan;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Replaces the old "shrink the whole cover to 16x16" approach. Instead:
 * every disc uses the SAME procedurally-drawn vinyl icon (dark body, groove
 * rings, spindle hole), and only the label circle in the middle is tinted --
 * to the album cover's most visually dominant colour, if we found one, or a
 * neutral grey otherwise. Reads cleaner at 16x16 than a full mosaic does,
 * and gives your disc shelf a consistent "look" while still being colour-
 * coded per song.
 */
public class DiscIconRenderer {

	private static final int ICON_SIZE = 16;

	/**
	 * Buckets the image's pixels into a coarse colour grid and returns the
	 * centre of whichever bucket scored highest, weighted toward saturated,
	 * mid-brightness colours -- a flat pixel average tends to collapse
	 * everything to muddy grey/brown, which this avoids.
	 */
	public static Color extractDominantColor(Path sourceImage) {
		try {
			BufferedImage img = ImageIO.read(sourceImage.toFile());
			if (img == null) return null;

			// Downscale first purely for speed -- doesn't need to be exact.
			BufferedImage sample = step(img, 64, 64);

			final int BUCKETS_PER_CHANNEL = 8; // 8x8x8 = 512 buckets
			double[] bucketWeight = new double[BUCKETS_PER_CHANNEL * BUCKETS_PER_CHANNEL * BUCKETS_PER_CHANNEL];
			long[] bucketR = new long[bucketWeight.length];
			long[] bucketG = new long[bucketWeight.length];
			long[] bucketB = new long[bucketWeight.length];
			long[] bucketCount = new long[bucketWeight.length];

			for (int y = 0; y < sample.getHeight(); y++) {
				for (int x = 0; x < sample.getWidth(); x++) {
					int rgb = sample.getRGB(x, y);
					int a = (rgb >> 24) & 0xFF;
					if (a < 40) continue; // skip near-transparent pixels (padding/letterboxing)

					int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
					float[] hsb = Color.RGBtoHSB(r, g, b, null);
					float sat = hsb[1], bri = hsb[2];

					// Prefer saturated, not-too-dark, not-too-bright pixels -- de-prioritises
					// black bars, white backgrounds and pure greys without excluding them outright.
					double weight = 0.15 + sat * (1.0 - Math.abs(bri - 0.6));

					int bucket = bucketIndex(r, g, b, BUCKETS_PER_CHANNEL);
					bucketWeight[bucket] += weight;
					bucketR[bucket] += r;
					bucketG[bucket] += g;
					bucketB[bucket] += b;
					bucketCount[bucket]++;
				}
			}

			int best = -1;
			double bestWeight = -1;
			for (int i = 0; i < bucketWeight.length; i++) {
				if (bucketWeight[i] > bestWeight) {
					bestWeight = bucketWeight[i];
					best = i;
				}
			}

			if (best < 0 || bucketCount[best] == 0) return null;
			return new Color(
					(int) (bucketR[best] / bucketCount[best]),
					(int) (bucketG[best] / bucketCount[best]),
					(int) (bucketB[best] / bucketCount[best])
			);
		} catch (IOException e) {
			System.err.println("[musicdiscs] Could not read cover art " + sourceImage + ": " + e.getMessage());
			return null;
		}
	}

	private static int bucketIndex(int r, int g, int b, int perChannel) {
		int rb = Math.min(perChannel - 1, r * perChannel / 256);
		int gb = Math.min(perChannel - 1, g * perChannel / 256);
		int bb = Math.min(perChannel - 1, b * perChannel / 256);
		return (rb * perChannel + gb) * perChannel + bb;
	}

	/** Draws the shared vinyl-disc icon with the given label colour and writes it to targetPng. */
	public static void render(Color labelColor, Path targetPng) {
		try {
			BufferedImage img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = img.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			// Outer body -- dark, slightly bluish, like vinyl.
			g.setColor(new Color(24, 24, 28));
			g.fillOval(0, 0, ICON_SIZE, ICON_SIZE);

			// A couple of faint groove rings.
			g.setColor(new Color(40, 40, 46));
			g.drawOval(1, 1, ICON_SIZE - 3, ICON_SIZE - 3);
			g.drawOval(2, 2, ICON_SIZE - 5, ICON_SIZE - 5);

			// Label circle, tinted to the extracted colour.
			g.setColor(labelColor);
			g.fillOval(4, 4, ICON_SIZE - 8, ICON_SIZE - 8);
			g.setColor(labelColor.darker());
			g.drawOval(4, 4, ICON_SIZE - 8, ICON_SIZE - 8);

			// Spindle hole.
			g.setColor(new Color(10, 10, 12));
			g.fillOval(7, 7, 2, 2);

			g.dispose();

			Files.createDirectories(targetPng.getParent());
			ImageIO.write(img, "png", targetPng.toFile());
		} catch (IOException e) {
			System.err.println("[musicdiscs] Could not write icon " + targetPng + ": " + e.getMessage());
		}
	}

	private static BufferedImage step(BufferedImage src, int w, int h) {
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return out;
	}

	/** Deterministic fallback colour for tracks with no usable embedded art, so re-scans stay stable instead of re-randomising. */
	public static Color placeholderColor(String seed) {
		int hash = seed.hashCode();
		return Color.getHSBColor((hash & 0xFF) / 255f, 0.45f, 0.55f);
	}
}
