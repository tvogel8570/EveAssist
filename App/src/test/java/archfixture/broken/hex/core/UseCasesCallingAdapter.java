package archfixture.broken.hex.core;

import archfixture.broken.hex.adapter.PlanTable;

public class UseCasesCallingAdapter {
    PlanTable table;
    public PlanTable table() {
        return new PlanTable();
    }
}
