package archfixture.clean.industry.internal.application;

import archfixture.clean.identity.Viewer;

@org.jmolecules.architecture.hexagonal.SecondaryPort
public interface PlanRepository {

    String find(Viewer viewer, String planId);
}
