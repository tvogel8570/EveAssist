package com.eveassist.sde;

import java.math.BigDecimal;

/**
 * A skill that a blueprint requires and that shortens its manufacturing time for each level the builder has: the SDE gives the skill
 * `manufactureTimePerLevel` (attribute 1982), which is -1 for most and -2 for one. A required skill without that attribute, such as
 * Capital Ship Construction, saves nothing and is not listed.
 *
 * @param percentPerLevel the time saved for each level, in percent, as a positive number
 */
public record TimeSkill(TypeId skill, BigDecimal percentPerLevel) {
}
