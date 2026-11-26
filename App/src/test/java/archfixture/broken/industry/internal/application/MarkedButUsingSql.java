package archfixture.broken.industry.internal.application;

import archfixture.broken.identity.ReadsNoAccountData;

@ReadsNoAccountData
public class MarkedButUsingSql {
    public void load(java.sql.Connection connection) {}
}
