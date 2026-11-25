package com.eveassist.industry.internal.domain;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

/**
 * A structure a facility can be, and what the structure itself gives a manufacturing job, as whole percents: the material bonus, the time
 * bonus and the job fee bonus. The facility form proposes these when a type is chosen, and the user can type over them.
 *
 * <p>The values are the structures' own bonuses and not the rigs': SDE attributes 2600 (materials), 2602 (time) and 2601 (job fee) of
 * Raitaru, Azbel and Sotiyo, which the game's Industry window confirmed for the Azbel ({@code costing-detail.md}, 4.1 and readings G1 to
 * G16). They change with game patches, so this is the one place to change them. An NPC station has no structure bonus.
 */
public enum StructureType {
    RAITARU("1", "15", "3"),
    AZBEL("1", "20", "4"),
    SOTIYO("1", "30", "5"),
    NPC_STATION("0", "0", "0");

    private final BigDecimal materialPercent;
    private final BigDecimal timePercent;
    private final BigDecimal costPercent;

    StructureType(String materialPercent, String timePercent, String costPercent) {
        this.materialPercent = new BigDecimal(materialPercent);
        this.timePercent = new BigDecimal(timePercent);
        this.costPercent = new BigDecimal(costPercent);
    }

    public BigDecimal materialPercent() {
        return materialPercent;
    }

    public BigDecimal timePercent() {
        return timePercent;
    }

    public BigDecimal costPercent() {
        return costPercent;
    }

    /** The name the database and the form use: lower case, such as {@code azbel} and {@code npc_station}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The type for a stored or submitted name, or none for nothing or for a name that is not one. */
    public static Optional<StructureType> ofKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        for (var type : values()) {
            if (type.key().equals(key.strip())) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
