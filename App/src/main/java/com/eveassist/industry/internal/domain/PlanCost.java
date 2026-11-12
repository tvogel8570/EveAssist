package com.eveassist.industry.internal.domain;

import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record PlanCost(List<LineCost> lines, BigDecimal total, int unpricedLines) {

    public PlanCost {
        lines = List.copyOf(lines);
    }

    /** The lines that are new or differ from the earlier costing. One edit can ripple through the material tree. */
    public List<LineCost> changedSince(PlanCost before) {
        Map<TypeId, LineCost> earlier = before.lines.stream()
                .collect(Collectors.toMap(LineCost::type, Function.identity()));
        return lines.stream()
                .filter(line -> !sameLine(line, earlier.get(line.type())))
                .toList();
    }

    private static boolean sameLine(LineCost line, LineCost other) {
        return other != null
                && line.quantity() == other.quantity()
                && line.built() == other.built()
                && line.runs() == other.runs()
                && line.seconds() == other.seconds()
                && sameAmount(line.unitPrice(), other.unitPrice())
                && sameAmount(line.cost(), other.cost());
    }

    private static boolean sameAmount(BigDecimal one, BigDecimal other) {
        return one == null || other == null ? Objects.equals(one, other) : one.compareTo(other) == 0;
    }
}
