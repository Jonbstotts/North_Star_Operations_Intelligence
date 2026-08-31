package com.wtm.ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dashboard glyph atlas supplied for NorthStar.
 *
 * The source art is stored as four rows of nine company-style glyphs. The
 * renderer derives the cell size from the packaged row instead of assuming a
 * fixed source resolution, so both the current 72 px atlas and future higher
 * resolution replacements render correctly. Icons are exposed as
 * multi-resolution images for crisp Swing/HiDPI rendering.
 */
public final class NorthStarDashboardGlyphs {
    private static final int GLYPHS_PER_ROW = 9;
    private static final Map<String, Point> INDEX = buildIndex();
    private static final BufferedImage[] ROWS = new BufferedImage[4];
    private static final Map<String, Icon> CACHE = new ConcurrentHashMap<>();

    private NorthStarDashboardGlyphs() {}

    public static Icon icon(String key, int logicalSize) {
        if (key == null || logicalSize <= 0) return null;
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        Point p = INDEX.get(normalized);
        if (p == null) return null;
        String cacheKey = normalized + "@" + logicalSize;
        return CACHE.computeIfAbsent(cacheKey, ignored -> buildIcon(p, logicalSize));
    }

    private static Icon buildIcon(Point p, int logicalSize) {
        BufferedImage row = loadRow(p.y);
        if (row == null) return null;

        int cell = atlasCellSize(row);
        if (cell <= 0) return null;
        int x = p.x * cell;
        if (x < 0 || x + cell > row.getWidth()) return null;

        BufferedImage sprite = row.getSubimage(x, 0, cell, cell);
        BufferedImage oneX = scale(sprite, logicalSize, logicalSize);
        int hi = Math.max(logicalSize, Math.min(cell, logicalSize * 2));
        BufferedImage twoX = scale(sprite, hi, hi);
        Image multi = new BaseMultiResolutionImage(oneX, twoX);
        return new ImageIcon(multi);
    }

    private static int atlasCellSize(BufferedImage row) {
        if (row == null || row.getWidth() <= 0 || row.getHeight() <= 0) return 0;
        if (row.getWidth() % GLYPHS_PER_ROW != 0) return 0;
        int widthCell = row.getWidth() / GLYPHS_PER_ROW;
        if (widthCell <= 0 || row.getHeight() != widthCell) return 0;
        return widthCell;
    }

    private static BufferedImage loadRow(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= ROWS.length) return null;
        BufferedImage cached = ROWS[rowIndex];
        if (cached != null) return cached;
        synchronized (ROWS) {
            cached = ROWS[rowIndex];
            if (cached != null) return cached;
            String resource = "/glyphs/northstar_dashboard_row" + rowIndex + ".png";
            try (InputStream in = NorthStarDashboardGlyphs.class.getResourceAsStream(resource)) {
                if (in == null) return null;
                cached = ImageIO.read(in);
                if (cached == null || atlasCellSize(cached) <= 0) {
                    System.err.println("NorthStar dashboard glyph row has invalid atlas geometry: " + resource);
                    return null;
                }
                ROWS[rowIndex] = cached;
                return cached;
            } catch (Exception ex) {
                System.err.println("NorthStar dashboard glyph row failed to load: " + resource + " - " + ex.getMessage());
                return null;
            }
        }
    }

    private static BufferedImage scale(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static Map<String, Point> buildIndex() {
        Map<String, Point> m = new HashMap<>();
        putRow(m, 0, List.of(
                "new_year", "mlk", "presidents_day", "memorial_day", "independence_day",
                "labor_day", "columbus_day", "veterans_day", "thanksgiving"
        ));
        putRow(m, 1, List.of(
                "christmas", "halloween", "valentines_day", "st_patricks_day", "easter",
                "mothers_day", "fathers_day", "juneteenth", "fall_season"
        ));
        putRow(m, 2, List.of(
                "birthday", "work_anniversary", "employee_of_month", "team_success", "promotion",
                "graduation", "welcome", "congratulations", "new_hire"
        ));
        putRow(m, 3, List.of(
                "safety_milestone", "milestone", "project_complete", "thank_you", "holiday_season",
                "seasonal", "weather_alert", "special_event", "lets_celebrate"
        ));
        return Collections.unmodifiableMap(m);
    }

    private static void putRow(Map<String, Point> m, int row, List<String> keys) {
        for (int col = 0; col < keys.size(); col++) m.put(keys.get(col), new Point(col, row));
    }
}
