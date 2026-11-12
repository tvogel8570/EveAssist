package com.eveassist.industry.internal.application;

import java.math.BigDecimal;

public record PlanLineCosting(
        int typeId,
        String name,
        String groupName,
        long quantity,
        boolean buildable,
        boolean built,
        BigDecimal unitPrice,
        BigDecimal cost) {
}
