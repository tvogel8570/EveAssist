package com.eveassist.spike.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A job board: manufacturing jobs from the Ishtar plan, assigned to characters or not yet assigned. Each character
 * has a number of manufacturing slots and a time bonus from skills, so a job's duration depends on who runs it.
 * Fake data, like {@link Catalog}.
 */
public class JobBoard {

    public record Pilot(String id, String name, int slots, double timeBonus) {
    }

    public record Job(String id, String product, int runs, long baseSeconds) {
    }

    /** A job as shown in a column: its duration is for the column's character (the base time when unassigned). */
    public record JobView(String id, String product, int runs, long baseSeconds, long seconds, String duration) {
    }

    /** A column of the board. {@code pilotId} and {@code slots} are null for the unassigned column. */
    public record Column(String pilotId, String name, Integer slots, int used, double timeBonus,
                         List<JobView> jobs) {
        public boolean full() {
            return slots != null && used >= slots;
        }
    }

    public record BoardView(List<Column> columns, String message) {
    }

    /** Thrown when a move breaks a rule, such as a full character. */
    public static class MoveRefused extends RuntimeException {
        public MoveRefused(String message) {
            super(message);
        }
    }

    private static final List<Pilot> PILOTS = List.of(
            new Pilot("main", "Main Builder", 11, 0.25),
            new Pilot("alt-one", "Alt One", 10, 0.20),
            new Pilot("alt-two", "Alt Two", 4, 0.05));

    private static final List<Job> JOBS = List.of(
            new Job("j1", "Ishtar", 10, 10 * 10 * 3600L),
            new Job("j2", "Vexor", 10, 10 * 4 * 3600L),
            new Job("j3", "Fusion Thruster", 150, 150 * 1200L),
            new Job("j4", "Fusion Thruster", 150, 150 * 1200L),
            new Job("j5", "Magnetometric Sensor Cluster", 225, 225 * 1200L),
            new Job("j6", "Magnetometric Sensor Cluster", 225, 225 * 1200L),
            new Job("j7", "Nanoelectrical Microprocessor", 450, 450 * 600L),
            new Job("j8", "Nanoelectrical Microprocessor", 450, 450 * 600L),
            new Job("j9", "Tungsten Carbide Armor Plate", 1500, 1500 * 120L),
            new Job("j10", "R.A.M.- Starship Tech", 60, 60 * 300L));

    /** Job id to pilot id; a missing entry means unassigned. Insertion order is the order within a column. */
    private final Map<String, String> assignments = new LinkedHashMap<>();

    public JobBoard() {
        reset();
    }

    public final synchronized void reset() {
        assignments.clear();
        assignments.put("j9", "alt-two");
        assignments.put("j10", "alt-two");
        assignments.put("j3", "alt-one");
    }

    public synchronized BoardView view() {
        return view(null);
    }

    /** Moves a job to a pilot, or to the unassigned column when {@code pilotId} is null or blank. */
    public synchronized BoardView move(String jobId, String pilotId) {
        job(jobId);
        if (pilotId == null || pilotId.isBlank()) {
            assignments.remove(jobId);
            return view(null);
        }
        Pilot pilot = pilot(pilotId);
        if (pilotId.equals(assignments.get(jobId))) {
            return view(null);
        }
        long used = assignments.values().stream().filter(pilotId::equals).count();
        if (used >= pilot.slots()) {
            throw new MoveRefused(pilot.name() + " has no free manufacturing slot.");
        }
        assignments.remove(jobId);
        assignments.put(jobId, pilotId);
        return view(null);
    }

    public synchronized BoardView view(String message) {
        List<Column> columns = new ArrayList<>();
        columns.add(new Column(null, "Unassigned", null, 0, 0, JOBS.stream()
                .filter(j -> !assignments.containsKey(j.id()))
                .map(j -> jobView(j, 0)).toList()));
        for (Pilot pilot : PILOTS) {
            List<JobView> jobs = assignments.entrySet().stream()
                    .filter(e -> e.getValue().equals(pilot.id()))
                    .map(e -> jobView(job(e.getKey()), pilot.timeBonus())).toList();
            columns.add(new Column(pilot.id(), pilot.name(), pilot.slots(), jobs.size(), pilot.timeBonus(), jobs));
        }
        return new BoardView(columns, message);
    }

    private static JobView jobView(Job job, double timeBonus) {
        long seconds = Math.round(job.baseSeconds() * (1 - timeBonus));
        return new JobView(job.id(), job.product(), job.runs(), job.baseSeconds(), seconds, duration(seconds));
    }

    /** Formats a duration as players read it: 4d 6h, 3h 20m, 45m. */
    public static String duration(long seconds) {
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3_600;
        long minutes = seconds % 3_600 / 60;
        if (days > 0) {
            return String.format(Locale.ROOT, "%dd %dh", days, hours);
        }
        if (hours > 0) {
            return String.format(Locale.ROOT, "%dh %dm", hours, minutes);
        }
        return String.format(Locale.ROOT, "%dm", minutes);
    }

    private static Job job(String id) {
        return JOBS.stream().filter(j -> j.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown job " + id));
    }

    private static Pilot pilot(String id) {
        return PILOTS.stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown character " + id));
    }
}
