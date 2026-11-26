package archfixture.clean.industry.internal.application;

import archfixture.clean.identity.ReadsNoAccountData;

@ReadsNoAccountData
public class RecipeHelper {
    public String name(int type) {
        return "" + type;
    }
}
