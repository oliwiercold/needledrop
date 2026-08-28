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
 * Replaces the old "shrink the whole cover to 16x16" approach. Instead,
 * every disc uses the SAME procedurally-drawn vinyl icon (dark body, groove
 * rings, spindle hole), and only the label circle in the middle is tinted to
 * the album cover's most visually dominant colour, if we found one, or a
 * neutral grey otherwise. Reads cleaner at 16x16 than a full mosaic does,
 * and gives your disc shelf a consistent look while still being colour-coded
 * per song.
 */
public class DiscIconRenderer {

	private static final int ICON_SIZE = 16;

	/**
	 * Buckets the image's pixels into a coarse colour grid and returns the
	 * centre of whichever bucket scored highest, weighted toward saturated,
	 * mid-brightness colours. A flat pixel average tends to collapse
	 * everything to muddy grey/brown, which this avoids.
	 */
	public static Color extractDominantColor(Path sourceImage) {
		try {
			BufferedImage img = ImageIO.read(sourceImage.toFile());
			if (img == null) return null;

			// Downscale first purely for speed, doesn't need to be exact.
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

					// Prefer saturated, not-too-dark, not-too-bright pixels. De-prioritises
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

	/**
	 * Draws the shared vinyl-disc icon with the given label colour and
	 * writes it to targetPng. Entirely our own procedural drawing (see
	 * renderBuiltinDisc below). There used to be a path that based the icon
	 * on a user-supplied texture extracted from the game's own files, but
	 * that meant shipping (or asking users to supply) a Mojang-derived
	 * asset. Not worth it when the procedural version looks fine on its
	 * own, so that path is gone.
	 */
	public static void render(Color labelColor, Path targetPng) {
		try {
			BufferedImage img = renderBuiltinDisc(labelColor);
			Files.createDirectories(targetPng.getParent());
			ImageIO.write(img, "png", targetPng.toFile());
		} catch (IOException e) {
			System.err.println("[musicdiscs] Could not write icon " + targetPng + ": " + e.getMessage());
		}
	}

	/**
	 * The disc icon: a dark vinyl body with a few subtle groove rings and a
	 * label circle tinted to the extracted dominant colour. Computed by
	 * distance-from-centre rather than a hand-typed pixel grid, so it's
	 * exactly radially symmetric by construction.
	 */
	private static BufferedImage renderBuiltinDisc(Color labelColor) {
		BufferedImage img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
		int labelRgb = labelColor.getRGB() | 0xFF000000;
		int labelEdgeRgb = labelColor.darker().getRGB() | 0xFF000000;
		double center = (ICON_SIZE - 1) / 2.0;
		double maxDist = center + 0.5;

		for (int y = 0; y < ICON_SIZE; y++) {
			for (int x = 0; x < ICON_SIZE; x++) {
				double dist = Math.hypot(x - center, y - center);
				if (dist > maxDist) {
					img.setRGB(x, y, 0);
					continue;
				}

				double t = dist / maxDist; // 0 = centre, 1 = rim
				int rgb;
				if (t < 0.42) {
					rgb = labelRgb;
				} else if (t < 0.47) {
					rgb = labelEdgeRgb;
				} else if (t < 0.60) {
					rgb = 0xFF262626;
				} else if (t < 0.64) {
					rgb = 0xFF3A3A3A;
				} else if (t < 0.80) {
					rgb = 0xFF262626;
				} else if (t < 0.85) {
					rgb = 0xFF3A3A3A;
				} else {
					rgb = 0xFF161616;
				}
				img.setRGB(x, y, rgb);
			}
		}

		int mid = ICON_SIZE / 2;
		int spindleRgb = 0xFF0D0D0D;
		img.setRGB(mid - 1, mid - 1, spindleRgb);
		img.setRGB(mid, mid - 1, spindleRgb);
		img.setRGB(mid - 1, mid, spindleRgb);
		img.setRGB(mid, mid, spindleRgb);
		return img;
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
