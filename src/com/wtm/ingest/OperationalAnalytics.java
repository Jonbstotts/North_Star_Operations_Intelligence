package com.wtm.ingest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic summaries over Data Collection's canonical operational feeds.
 * This layer intentionally performs no LLM calls and never invents missing
 * operational values; it only summarizes data already imported by
 * {@link DataIngestionService}.
 */
public final class OperationalAnalytics {
    private OperationalAnalytics() {}

    public static String contextFor(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        DataIngestionService service = DataIngestionService.get();
        StringBuilder out = new StringBuilder("VERIFIED NORTHSTAR OPERATIONAL DATA\n");
        try {
            if (q.contains("lhy") || q.contains("lph") || q.contains("productiv") || q.contains("operation")) {
                summarizeProductivity(service, out);
            }
            if (q.contains("floor denial") || q.contains("short")) {
                summarizeFloor(service, out);
            }
            if (q.contains("delete") || q.contains("replen")) {
                summarizeCount(service, DataIngestionService.Type.TASK_DELETIONS, out, "Task deletion records");
            }
            if (q.contains("undo") || q.contains("ticket")) {
                summarizeCount(service, DataIngestionService.Type.TICKET_UNDOS, out, "Ticket undo records");
            }
            if (q.contains("terminal") || q.contains(" rf ") || q.startsWith("rf ")) {
                summarizeCount(service, DataIngestionService.Type.RF_TERMINAL, out, "RF terminal assignment records");
            }
            if (q.contains("equipment")) {
                summarizeCount(service, DataIngestionService.Type.EQUIPMENT_USAGE, out, "Equipment activity records");
            }
            if (q.contains("touch") || q.contains("trace") || q.contains("barcode") || q.contains("location") || q.contains("part")) {
                summarizeCount(service, DataIngestionService.Type.AUDIT_ITEM, out, "Item audit events");
                summarizeCount(service, DataIngestionService.Type.AUDIT_LOCATION, out, "Location audit events");
                summarizeCount(service, DataIngestionService.Type.AUDIT_TASK, out, "Task audit events");
            }
        } catch (Exception ex) {
            out.append("Analytics note: ").append(ex.getMessage()).append('\n');
        }
        return out.length() <= "VERIFIED NORTHSTAR OPERATIONAL DATA\n".length() ? "" : out.toString();
    }

    private static void summarizeProductivity(DataIngestionService service, StringBuilder out) throws IOException {
        Path file = service.dataFile(DataIngestionService.Type.DAILY_PRODUCTIVITY);
        if (!Files.isRegularFile(file)) {
            out.append("Daily productivity feed: not loaded.\n");
            return;
        }
        List<String[]> rows = csv(file);
        if (rows.size() < 2) return;

        String[] header = rows.get(0);
        int lhy = index(header, "LHY");
        int inbound = index(header, "Inbound LPH");
        int outbound = index(header, "Outbound LPH");
        int date = indexAny(header, "LOGDATE", "Date");
        List<Double> values = new ArrayList<>();
        String latestDate = "";
        double latestLhy = Double.NaN;
        double latestInbound = Double.NaN;
        double latestOutbound = Double.NaN;

        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            Double value = number(row, lhy);
            if (value == null) continue;
            values.add(value);
            latestLhy = value;
            latestDate = date >= 0 && date < row.length ? row[date] : "";
            latestInbound = value(row, inbound);
            latestOutbound = value(row, outbound);
        }

        if (values.isEmpty()) {
            out.append("Daily productivity is loaded but contains no completed LHY values; current day may be partial.\n");
            return;
        }

        double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double recent = values.subList(Math.max(0, values.size() - 5), values.size())
                .stream().mapToDouble(Double::doubleValue).average().orElse(0);
        out.append(String.format(Locale.US,
                "Completed LHY days: %d; overall average %.2f; recent 5-day average %.2f; target 17500. Latest completed %s LHY %.2f",
                values.size(), average, recent, latestDate, latestLhy));
        if (!Double.isNaN(latestInbound)) out.append(String.format(Locale.US, ", inbound LPH %.2f", latestInbound));
        if (!Double.isNaN(latestOutbound)) out.append(String.format(Locale.US, ", outbound LPH %.2f", latestOutbound));
        out.append(". Blank LHY rows are PARTIAL and must not be treated as completed days.\n");
        summarizeCount(service, DataIngestionService.Type.HOURLY_PICKS, out, "Latest hourly pick rows");
        summarizeCount(service, DataIngestionService.Type.HOURLY_BINNED, out, "Latest hourly binned-task rows");
    }

    private static void summarizeFloor(DataIngestionService service, StringBuilder out) throws IOException {
        Path file = service.dataFile(DataIngestionService.Type.FLOOR_DENIALS);
        if (!Files.isRegularFile(file)) {
            out.append("Floor denial feed: not loaded.\n");
            return;
        }
        List<String[]> rows = csv(file);
        if (rows.size() < 2) return;
        String[] header = rows.get(0);
        int qty = indexAny(header, "Shorted Task Qty", "ShortedTaskQty");
        int extValue = indexAny(header, "Ext Value Shorted", "ExtValueShorted");
        double quantity = 0;
        double value = 0;
        for (int i = 1; i < rows.size(); i++) {
            Double q = number(rows.get(i), qty);
            Double v = number(rows.get(i), extValue);
            if (q != null) quantity += q;
            if (v != null) value += v;
        }
        out.append(String.format(Locale.US,
                "Floor denials: %d events; %.0f units shorted; $%,.2f extended value represented in current feed.\n",
                rows.size() - 1, quantity, value));
    }

    private static void summarizeCount(DataIngestionService service, DataIngestionService.Type type,
                                       StringBuilder out, String label) throws IOException {
        Path file = service.dataFile(type);
        if (!Files.isRegularFile(file)) return;
        try (var lines = Files.lines(file)) {
            long count = Math.max(0, lines.count() - 1);
            out.append(label).append(": ").append(count).append(".\n");
        }
    }

    private static List<String[]> csv(Path path) throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) rows.add(parse(line));
        return rows;
    }

    private static String[] parse(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                values.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        values.add(field.toString());
        return values.toArray(String[]::new);
    }

    private static int index(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private static int indexAny(String[] header, String... names) {
        for (String name : names) {
            int index = index(header, name);
            if (index >= 0) return index;
        }
        return -1;
    }

    private static Double number(String[] row, int index) {
        if (index < 0 || index >= row.length || row[index].isBlank()) return null;
        try {
            return Double.parseDouble(row[index].replace("$", "").replace(",", "").trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static double value(String[] row, int index) {
        Double value = number(row, index);
        return value == null ? Double.NaN : value;
    }
}
