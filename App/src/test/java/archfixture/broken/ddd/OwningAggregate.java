package archfixture.broken.ddd;

@org.jmolecules.ddd.annotation.AggregateRoot
public class OwningAggregate {
    @org.jmolecules.ddd.annotation.Identity
    java.util.UUID id;
    OwnedAggregate other;
}
