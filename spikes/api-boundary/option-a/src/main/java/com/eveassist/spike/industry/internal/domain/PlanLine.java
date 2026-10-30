package com.eveassist.spike.industry.internal.domain;

import java.util.UUID;

public class PlanLine {
    private final UUID id;
    private final String itemName;
    private final long unitPrice;
    private int quantity;

    public PlanLine(UUID id, String itemName, int quantity, long unitPrice) {
        this.id = id;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public UUID id() {
        return id;
    }

    public String itemName() {
        return itemName;
    }

    public int quantity() {
        return quantity;
    }

    public long unitPrice() {
        return unitPrice;
    }

    public long lineCost() {
        return quantity * unitPrice;
    }

    void changeQuantity(int newQuantity) {
        if (newQuantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        quantity = newQuantity;
    }
}
