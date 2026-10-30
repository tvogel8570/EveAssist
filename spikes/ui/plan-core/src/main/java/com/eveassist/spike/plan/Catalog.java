package com.eveassist.spike.plan;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fake items and facilities for the spike. Names are from EVE, but quantities and prices are invented and only
 * roughly plausible.
 */
public final class Catalog {

    /** One input of a blueprint run. */
    public record Input(String itemId, int quantityPerRun) {
    }

    /**
     * An item. Items without inputs can only be bought. {@code buyOnly} marks items that have inputs in the game but
     * are always bought for now, such as reaction products.
     */
    public record Item(String id, String name, double jitaSell, double jobFeePerRun, boolean buyOnly,
                       List<Input> inputs) {
        public boolean buildable() {
            return !buyOnly && !inputs.isEmpty();
        }
    }

    /** A facility with its material bonus (0.02 = 2 % less material) and tax rate. */
    public record Facility(String id, String name, double materialBonus, double taxRate) {
    }

    private static Item raw(String id, String name, double price) {
        return new Item(id, name, price, 0, false, List.of());
    }

    private static Item reaction(String id, String name, double price) {
        return new Item(id, name, price, 0, true, List.of());
    }

    private static final List<Item> ITEMS = List.of(
            new Item("ishtar", "Ishtar", 74_000_000, 4_500_000, false, List.of(
                    new Input("vexor", 1),
                    new Input("fusion-thruster", 30),
                    new Input("magnetometric-sensor-cluster", 45),
                    new Input("nanoelectrical-microprocessor", 90),
                    new Input("tungsten-carbide-armor-plate", 300),
                    new Input("ram-starship-tech", 6),
                    new Input("morphite", 60))),
            new Item("vexor", "Vexor", 13_800_000, 250_000, false, List.of(
                    new Input("tritanium", 540_000),
                    new Input("pyerite", 145_000),
                    new Input("mexallon", 42_000),
                    new Input("isogen", 9_000),
                    new Input("nocxium", 2_300),
                    new Input("zydrine", 520),
                    new Input("megacyte", 180))),
            new Item("fusion-thruster", "Fusion Thruster", 610_000, 9_000, false, List.of(
                    new Input("tungsten-carbide", 15),
                    new Input("fernite-carbide", 12),
                    new Input("ferrogel", 1))),
            new Item("magnetometric-sensor-cluster", "Magnetometric Sensor Cluster", 330_000, 6_000, false, List.of(
                    new Input("tungsten-carbide", 6),
                    new Input("hypersynaptic-fibers", 2),
                    new Input("ferrogel", 1))),
            new Item("nanoelectrical-microprocessor", "Nanoelectrical Microprocessor", 150_000, 3_000, false,
                    List.of(
                            new Input("phenolic-composites", 6),
                            new Input("nanotransistors", 1))),
            new Item("tungsten-carbide-armor-plate", "Tungsten Carbide Armor Plate", 41_000, 900, false, List.of(
                    new Input("tungsten-carbide", 2))),
            new Item("ram-starship-tech", "R.A.M.- Starship Tech", 9_500, 400, false, List.of(
                    new Input("tritanium", 500),
                    new Input("pyerite", 400),
                    new Input("mexallon", 200),
                    new Input("isogen", 40))),
            raw("tritanium", "Tritanium", 3.9),
            raw("pyerite", "Pyerite", 16.5),
            raw("mexallon", "Mexallon", 61),
            raw("isogen", "Isogen", 160),
            raw("nocxium", "Nocxium", 520),
            raw("zydrine", "Zydrine", 1_150),
            raw("megacyte", "Megacyte", 2_400),
            raw("morphite", "Morphite", 9_800),
            reaction("tungsten-carbide", "Tungsten Carbide", 15_500),
            reaction("fernite-carbide", "Fernite Carbide", 11_200),
            reaction("ferrogel", "Ferrogel", 92_000),
            reaction("hypersynaptic-fibers", "Hypersynaptic Fibers", 48_000),
            reaction("phenolic-composites", "Phenolic Composites", 13_900),
            reaction("nanotransistors", "Nanotransistors", 55_000));

    private static final List<Facility> FACILITIES = List.of(
            new Facility("raitaru-t1", "Raitaru, T1 ME rig", 0.02 * 1.9, 0.010),
            new Facility("raitaru-t2", "Raitaru, T2 ME rig", 0.01 + 0.024 * 1.9, 0.010),
            new Facility("azbel-t1", "Azbel, T1 ME rig", 0.01 + 0.02 * 1.9, 0.015),
            new Facility("npc", "NPC station (no bonus)", 0, 0.0025));

    private static final Map<String, Item> BY_ID =
            ITEMS.stream().collect(Collectors.toMap(Item::id, Function.identity()));

    private Catalog() {
    }

    public static Item item(String id) {
        Item item = BY_ID.get(id);
        if (item == null) {
            throw new IllegalArgumentException("Unknown item " + id);
        }
        return item;
    }

    public static List<Facility> facilities() {
        return FACILITIES;
    }

    public static Facility facility(String id) {
        return FACILITIES.stream().filter(f -> f.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown facility " + id));
    }

    /** The facility a new plan uses for an item: ships in the Azbel, everything else in the T1 Raitaru. */
    public static String defaultFacility(String itemId) {
        return switch (itemId) {
            case "ishtar", "vexor" -> "azbel-t1";
            default -> "raitaru-t1";
        };
    }
}
