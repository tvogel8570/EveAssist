package com.eveassist.industry.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.sde.Recipe;
import com.eveassist.sde.RecipeMaterial;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * The expected values are worked out by hand from the rules in the requirements. They are arithmetic checks, not
 * numbers read from a job in the game.
 */
class JobCostingTest {

    private static final TypeId HULL = new TypeId(1);
    private static final TypeId ORE = new TypeId(2);
    private static final TypeId PART = new TypeId(3);

    private static BigDecimal pct(String value) {
        return new BigDecimal(value);
    }

    private static FacilityTerms facility(
            String material, String rigMaterial, String time, String rigTime, String cost, String index, String tax,
            String scc) {
        return new FacilityTerms(pct(material), pct(rigMaterial), pct(time), pct(rigTime), pct(cost), pct(index),
                pct(tax), pct(scc));
    }

    @Test
    void bonusesMultiplyAndTheJobIsRoundedUpOnce() {
        var terms = new JobTerms(10, 0, facility("1", "2.1", "0", "0", "0", "0", "0", "0"), BuilderTerms.NONE);

        // 100 x 10 runs x 0.9 x 0.99 x 0.979 = 872.289
        assertThat(JobCosting.materialQuantity(100, 10, terms)).isEqualTo(873);
    }

    @Test
    void aJobNeverUsesLessThanOneOfAMaterialForEachRun() {
        assertThat(JobCosting.materialQuantity(1, 10, JobTerms.PLAN)).isEqualTo(10);
    }

    @Test
    void aWholeNumberIsNotRoundedUp() {
        // 10 x 3 runs x 0.9 = 27 exactly
        assertThat(JobCosting.materialQuantity(10, 3, JobTerms.PLAN)).isEqualTo(27);
    }

    @Test
    void timeUsesBlueprintSkillsImplantAndFacilityTogether() {
        var terms = new JobTerms(0, 20, facility("0", "0", "15", "0", "0", "0", "0", "0"),
                new BuilderTerms(5, 5, BigDecimal.ZERO));

        // 600 s x 10 runs x 0.8 (TE) x 0.8 (Industry V) x 0.85 (Advanced Industry V) x 0.85 (structure) = 2774.4,
        // which the game rounds to the nearest second
        assertThat(JobCosting.seconds(600, 10, terms)).isEqualTo(2774);
    }

    @Test
    void anImplantShortensTheTimeButNotTheMaterials() {
        var implant = new JobTerms(10, 20, FacilityTerms.NONE, new BuilderTerms(0, 0, pct("4")));

        assertThat(JobCosting.seconds(1000, 1, JobTerms.PLAN)).isEqualTo(800);
        assertThat(JobCosting.seconds(1000, 1, implant)).isEqualTo(768);
        assertThat(JobCosting.materialQuantity(1000, 1, implant)).isEqualTo(JobCosting.materialQuantity(1000, 1, JobTerms.PLAN));
    }

    @Test
    void theStructureBonusReducesTheJobFeeButNotTheTaxes() {
        var facility = facility("0", "0", "0", "0", "3", "3.21", "1", "4");

        // 100,000,000 x 3.21% x 0.97 = 3,113,700 and 100,000,000 x (1% + 4%) = 5,000,000
        assertThat(JobCosting.installCost(new BigDecimal("100000000"), facility)).isEqualByComparingTo("8113700");
    }

    @Test
    void theEstimatedItemValueUsesTheBaseMaterialsAndNotTheReducedOnes() {
        var recipe = new Recipe(HULL, 1, 600, List.of(new RecipeMaterial(ORE, 100), new RecipeMaterial(PART, 5)));
        var terms = new JobTerms(10, 20, facility("0", "0", "0", "0", "0", "2", "0", "0"), BuilderTerms.NONE);

        var job = JobCosting.cost(recipe, 10, terms,
                type -> type.equals(ORE) ? Optional.of(pct("10")) : Optional.of(pct("200")));

        // 10 runs x (100 x 10 + 5 x 200) = 20,000, at an index of 2% = 400
        assertThat(job.estimatedItemValue()).isEqualByComparingTo("20000");
        assertThat(job.installCost()).isEqualByComparingTo("400");
        assertThat(job.materials()).containsExactly(new RecipeMaterial(ORE, 900), new RecipeMaterial(PART, 45));
        assertThat(job.unitsMade()).isEqualTo(10);
        assertThat(job.seconds()).isEqualTo(4800);
    }

    @Test
    void aMaterialWithoutAnAdjustedPriceIsCountedAndLeftOutOfTheValue() {
        var recipe = new Recipe(HULL, 1, 600, List.of(new RecipeMaterial(ORE, 100), new RecipeMaterial(PART, 5)));

        var job = JobCosting.cost(recipe, 1, JobTerms.PLAN,
                type -> type.equals(ORE) ? Optional.of(pct("10")) : Optional.empty());

        assertThat(job.unpricedMaterials()).isEqualTo(1);
        assertThat(job.estimatedItemValue()).isEqualByComparingTo("1000");
    }

    private static final Recipe CAP_BOOSTER_800 = new Recipe(new TypeId(11289), 10, 900, List.of(
            new RecipeMaterial(new TypeId(34), 1502), new RecipeMaterial(new TypeId(35), 440),
            new RecipeMaterial(new TypeId(36), 252), new RecipeMaterial(new TypeId(38), 13),
            new RecipeMaterial(new TypeId(39), 6), new RecipeMaterial(new TypeId(40), 6)));

    /** The adjusted prices ESI returned on 2026-11-18 for Tritanium, Pyerite, Mexallon, Nocxium, Zydrine and Megacyte. */
    private static final Map<TypeId, BigDecimal> ADJUSTED_PRICES = Map.of(
            new TypeId(34), new BigDecimal("2.9139375060564285"), new TypeId(35), new BigDecimal("13.218374620307985"),
            new TypeId(36), new BigDecimal("40.8409326615202"), new TypeId(38), new BigDecimal("627.279129884147"),
            new TypeId(39), new BigDecimal("1078.5761333054306"), new TypeId(40), new BigDecimal("1439.4304372972902"));

    /** A character with Industry and Advanced Industry at V and no implant, as in the readings below. */
    private static final BuilderTerms ALL_SKILLS_V = new BuilderTerms(5, 5, BigDecimal.ZERO);

    private static JobCost capBoosterJob(long runs, int me, int te, String costIndexPercent) {
        var azbel = facility("1", "0", "20", "0", "4", costIndexPercent, "0", "4");
        return JobCosting.cost(CAP_BOOSTER_800, runs, new JobTerms(me, te, azbel, ALL_SKILLS_V),
                type -> Optional.of(ADJUSTED_PRICES.get(type)));
    }

    private static void assertWithinOneIsk(BigDecimal actual, String expected) {
        assertThat(actual).isCloseTo(new BigDecimal(expected), Offset.offset(BigDecimal.ONE));
    }

    /**
     * Read in the game on 2026-11-18: 100 runs of a Cap Booster 800 copy at ME 0 and TE 0 in an Azbel in wormhole space
     * with no rigs, built by a character with Industry and Advanced Industry at V and no implant. The window showed
     * materials of 148,698, 43,560, 24,948, 1,287, 594 and 594, a duration of 13:36:00, an estimated item value of
     * 4,374,740 ISK, a system cost index line of 24,885 ISK less a 4.0% structure role bonus of 995 ISK, an SCC surcharge
     * of 4.00% of the item value (174,990 ISK) and a total job cost of 198,879 ISK, with no facility tax line. The
     * duration hover showed Skills and Implants at -32.0% and the Structure Role Bonus at -20.0%, in both spaces. The
     * adjusted prices are the ones ESI returned that day, and the index is the one that gives the window's amounts (ESI
     * does not list wormhole systems).
     */
    @Test
    void matchesAnAzbelJobReadInTheGame() {
        var job = capBoosterJob(100, 0, 0, "0.56883");

        assertThat(job.materials()).extracting(RecipeMaterial::quantity)
                .containsExactly(148698L, 43560L, 24948L, 1287L, 594L, 594L);
        assertThat(job.seconds()).isEqualTo(13 * 3600 + 36 * 60);
        assertThat(job.unitsMade()).isEqualTo(1000);
        assertThat(ALL_SKILLS_V.skillFactor()).isEqualByComparingTo("0.68");
        assertWithinOneIsk(job.estimatedItemValue(), "4374740");
        assertWithinOneIsk(job.installCost(), "198879");
    }

    /**
     * Read in the game on 2026-11-18: the same 100 runs in an Azbel in high-sec space with no rigs. The materials and the
     * duration were the same as in the wormhole. The window showed a system cost index line of 87,894 ISK (the
     * wormhole's was 24,885), less the 4.0% role bonus (3,516), a gross cost of 84,378, the same SCC surcharge of
     * 174,990 and a total job cost of 259,368 ISK, again with no facility tax line. ESI lists the system (Dodenvale) with
     * a cost index of 0.0201, and ESI rounds every index to four decimals, so the window's own index (2.00912%, worked out
     * from its amounts) is a little different from ESI's.
     */
    @Test
    void anIndexRoundedToFourDecimalsMovesTheInstallCostByAFewTensOfIsk() {
        var inGame = capBoosterJob(100, 0, 0, "2.00912");
        var fromEsi = capBoosterJob(100, 0, 0, "2.01");

        assertWithinOneIsk(inGame.installCost(), "259368");
        assertThat(inGame.seconds()).isEqualTo(13 * 3600 + 36 * 60);
        // ESI's 0.0201 gives 259,404.6: 36 ISK (0.014%) more than the window
        assertThat(fromEsi.installCost().subtract(new BigDecimal("259368")).abs()).isLessThan(new BigDecimal("40"));
    }

    /**
     * Read in the game on 2026-11-18, in both Azbels: 100 runs of a Cap Booster 800 copy at ME 10 and TE 20. The window
     * showed 0.13M, 39.20K and 22.45K for the first three materials (it shortens big numbers), exactly 1,159, 535 and 535
     * for the last three, a duration of 10:52:48, and the same estimated item value and the same total job cost as at
     * ME 0 and TE 0 (198,879 ISK in the wormhole, 259,368 ISK in high-sec). Adding the reductions (10% and 1%) would give
     * 1,157, 534 and 534, so the last three also show that the reductions multiply.
     */
    @Test
    void theBlueprintsLevelsChangeTheMaterialsAndTheTimeButNotTheInstallCost() {
        var atMe10 = capBoosterJob(100, 10, 20, "0.56883");
        var atMe0 = capBoosterJob(100, 0, 0, "0.56883");

        assertThat(atMe10.materials()).extracting(RecipeMaterial::quantity)
                .containsExactly(133829L, 39204L, 22454L, 1159L, 535L, 535L);
        assertThat(atMe10.seconds()).isEqualTo(10 * 3600 + 52 * 60 + 48);
        assertThat(atMe10.estimatedItemValue()).isEqualByComparingTo(atMe0.estimatedItemValue());
        assertThat(atMe10.installCost()).isEqualByComparingTo(atMe0.installCost());
        assertWithinOneIsk(atMe10.installCost(), "198879");
        assertWithinOneIsk(capBoosterJob(100, 10, 20, "2.00912").installCost(), "259368");
    }

    /**
     * Read in the game on 2026-11-18, in both Azbels: a Cap Booster 800 copy at ME 10 and TE 20 with 4 runs (the job runs box
     * said 47, but the window had not applied it: the output said x 40 and the amounts are those of 4 runs). The window
     * showed 5,354, 1,569, 899, 47, 22 and 22, a duration of 00:26:07 and an estimated item value of 174,990 ISK. The total
     * job cost was 7,956 ISK in the wormhole and 10,375 ISK in high-sec. Rounding once for each run instead of once for
     * the job would give 5,356, 1,572, 900, 48, 24 and 24, so every material shows that it is rounded once per job.
     */
    @Test
    void aJobIsRoundedOnceForAllItsRuns() {
        var wormhole = capBoosterJob(4, 10, 20, "0.56883");
        var highSec = capBoosterJob(4, 10, 20, "2.00912");

        assertThat(wormhole.materials()).extracting(RecipeMaterial::quantity)
                .containsExactly(5354L, 1569L, 899L, 47L, 22L, 22L);
        assertThat(wormhole.seconds()).isEqualTo(26 * 60 + 7);
        assertWithinOneIsk(wormhole.estimatedItemValue(), "174990");
        // the exact total is 7,955.16: the window adds its rounded gross cost (956) to its rounded taxes (7,000)
        assertWithinOneIsk(wormhole.installCost(), "7956");
        assertWithinOneIsk(highSec.installCost(), "10375");
    }

    /**
     * Read in the game on 2026-11-18, in the high-sec Azbel: 47 runs of the Cap Booster 800 copy at ME 10 and TE 20. The
     * window showed 62.90K, 18.43K, 10.55K, 545, 252 and 252, a duration of 05:06:49, an estimated item value that gives a
     * total job cost of 121,903 ISK, and, on hovering Mexallon, **10,553**. Mexallon is 252 x 47 x 0.9 x 0.99 =
     * 10,553.004, so the game does not round up what is exactly 10,553.004: it rounds to two decimals first. (Rounding the
     * exact value up would give 10,554.)
     */
    @Test
    void theGameRoundsToTwoDecimalsBeforeRoundingUp() {
        var job = capBoosterJob(47, 10, 20, "2.00912");

        assertThat(job.materials()).extracting(RecipeMaterial::quantity)
                .containsExactly(62900L, 18426L, 10553L, 545L, 252L, 252L);
        assertThat(job.seconds()).isEqualTo(5 * 3600 + 6 * 60 + 49);
        assertWithinOneIsk(job.installCost(), "121903");
    }

    /**
     * Read in the game on 2026-11-18, in the high-sec Azbel: 94 runs of the Cap Booster 800 copy at ME 10 and TE 20. The
     * window showed 0.13M, 36.85K, 21.11K, 1,089, 503 and 503, a duration of 10:13:38 and a total job cost of 243,806 ISK,
     * and, on hovering Mexallon, **21,107**. Mexallon is 252 x 94 x 0.891 = 21,106.008: the third decimal is 8, so the game
     * rounds half up to 21,106.01 and then up to 21,107. Dropping the third decimal would give 21,106.
     */
    @Test
    void theGameRoundsTheThirdDecimalHalfUp() {
        var job = capBoosterJob(94, 10, 20, "2.00912");

        assertThat(job.materials()).extracting(RecipeMaterial::quantity).containsSubsequence(21107L, 1089L, 503L, 503L);
        assertThat(job.seconds()).isEqualTo(10 * 3600 + 13 * 60 + 38);
        assertWithinOneIsk(job.installCost(), "243806");
    }

    /**
     * Read in the game on 2026-11-18, in the high-sec Azbel: 10 runs of a Multispectrum Coating II copy at ME 2 and TE 4,
     * built by a character with every skill at V. The blueprint needs Industry, Nanite Engineering and Hydromagnetic Physics.
     * The window showed 10 of each of the seven materials (9.70 rounded up; this does not separate the one-per-run minimum,
     * since rounding up gives 10 as well),
     * a duration of 03:03:49 and, on hovering it, Blueprint Time Efficiency -4.0%, Skills and Implants -38.6% and Structure
     * Role Bonus -20.0%. Adding the two Tech II skills (-10%) instead of multiplying them (0.95 x 0.95) would give 03:03:19.
     * The window's job cost of 35,407 ISK is not asserted: ESI's adjusted prices give 35,417.5, and the 0.03% between them
     * is not explained (see the requirements).
     */
    @Test
    void theTechIiSkillsATechIiBlueprintRequiresShortenTheTimeAndMultiply() {
        var materials = java.util.stream.Stream.of(11399, 11544, 11475, 11542, 11543, 1304, 11545)
                .map(id -> new RecipeMaterial(new TypeId(id), 1)).toList();
        var recipe = new Recipe(new TypeId(2340), 1, 2340, materials);
        var builder = new BuilderTerms(5, 5,
                List.of(new ProductSkill(5, BigDecimal.ONE), new ProductSkill(5, BigDecimal.ONE)), BigDecimal.ZERO);
        var terms = new JobTerms(2, 4, facility("1", "0", "20", "0", "4", "2.00912", "0", "4"), builder);

        var job = JobCosting.cost(recipe, 10, terms, type -> Optional.empty());

        assertThat(job.materials()).extracting(RecipeMaterial::quantity).containsOnly(10L).hasSize(7);
        assertThat(job.seconds()).isEqualTo(3 * 3600 + 3 * 60 + 49);
        assertThat(builder.skillFactor()).isEqualByComparingTo("0.6137");
    }

    /**
     * Read in the game on 2026-11-18, in the high-sec Azbel: the Multispectrum Coating II copy again, with 9 runs. The window
     * showed 9 of each material, an estimated item value of 537,646 ISK, an index line of 10,793 ISK, a role bonus of -432,
     * a gross cost of 10,361, an SCC surcharge of 21,506, a total job cost of 31,867 ISK and a duration of 02:45:26.
     * The time is 2,340 x 9 x 0.96 x 0.6137 x 0.80 = 9,926.03 s, so the game rounds it to the nearest second and does not
     * round it up (that would be 9,927 s). The 10-run reading (35,407 ISK) agrees with the same index of 2.00737%, which is
     * lower than the 2.00912% of the Cap Booster readings an hour or so earlier: the system's index moves.
     */
    @Test
    void theGameRoundsTheTimeToTheNearestSecondAndTheCostIndexMoves() {
        var adjusted = Map.of(
                new TypeId(11399), new BigDecimal("17724.103115699"), new TypeId(11544), new BigDecimal("6212.107735294834"),
                new TypeId(11475), new BigDecimal("616.129425947184"), new TypeId(11542), new BigDecimal("5849.413231828102"),
                new TypeId(11543), new BigDecimal("6344.795841945499"), new TypeId(1304), new BigDecimal("16557.25242148275"),
                new TypeId(11545), new BigDecimal("6434.675613387671"));
        var recipe = new Recipe(new TypeId(2340), 1, 2340, adjusted.keySet().stream()
                .map(type -> new RecipeMaterial(type, 1)).toList());
        var builder = new BuilderTerms(5, 5,
                List.of(new ProductSkill(5, BigDecimal.ONE), new ProductSkill(5, BigDecimal.ONE)), BigDecimal.ZERO);
        var terms = new JobTerms(2, 4, facility("1", "0", "20", "0", "4", "2.00737", "0", "4"), builder);

        var nine = JobCosting.cost(recipe, 9, terms, type -> Optional.of(adjusted.get(type)));
        var ten = JobCosting.cost(recipe, 10, terms, type -> Optional.of(adjusted.get(type)));

        assertThat(nine.seconds()).isEqualTo(2 * 3600 + 45 * 60 + 26);
        assertThat(ten.seconds()).isEqualTo(3 * 3600 + 3 * 60 + 49);
        assertWithinOneIsk(nine.estimatedItemValue(), "537646");
        assertWithinOneIsk(nine.installCost(), "31867");
        assertWithinOneIsk(ten.installCost(), "35407");
    }

    /**
     * Read in the game on 2026-11-18, in the high-sec Azbel: 100 runs of the Cap Booster 800 copy at ME 10 and TE 20, built by
     * a character with Industry and Advanced Industry at V and a Zainou 'Beancounter' Industry BX-804 (4%) fitted. The
     * duration hover showed Blueprint Time Efficiency -20.0%, **Skills and Implants -34.7%** and Structure Role Bonus -20.0%, and
     * the duration was 10:26:41 (37,601 s). Multiplying gives 1 - 0.80 x 0.85 x 0.96 = 34.72%, and adding the implant to the
     * skills' 32% would give 36.0% and 10:14:24. The materials were those of ME 10 (0.13M, 39.20K, 22.45K, 1,159, 535, 535)
     * and the job cost 259,297 ISK, which is the same estimated item value at an index of about 2.00744%.
     */
    @Test
    void anImplantMultipliesWithTheSkills() {
        var builder = new BuilderTerms(5, 5, new BigDecimal("4"));
        var terms = new JobTerms(10, 20, facility("1", "0", "20", "0", "4", "2.00744", "0", "4"), builder);

        var job = JobCosting.cost(CAP_BOOSTER_800, 100, terms, type -> Optional.of(ADJUSTED_PRICES.get(type)));

        assertThat(builder.timeFactor()).isEqualByComparingTo("0.6528");
        assertThat(job.seconds()).isEqualTo(10 * 3600 + 26 * 60 + 41);
        assertThat(job.materials()).extracting(RecipeMaterial::quantity)
                .containsExactly(133829L, 39204L, 22454L, 1159L, 535L, 535L);
        assertWithinOneIsk(job.installCost(), "259297");
    }

    /**
     * Read in the game on 2026-11-18, in the high-sec Azbel: 1 run of a 10000MN Afterburner I copy at ME 8 and TE 14, built
     * by a character with Industry and Advanced Industry at V and a BX-804. The window showed 1.20M, 0.39M, 0.12M, 28.81K,
     * 4,992, 1,593 and 1,044 (the last three exactly), a duration of 04:29:29, a total job cost of 1,653,860 ISK, and a
     * duration hover of Blueprint Time Efficiency -14.0% (the blueprint's own TE), Skills and Implants -34.7% and Structure
     * Role Bonus -20.0%. The blueprint also requires Capital Ship Construction, which saves no time and is not in the hover.
     * The index that gives the cost, 2.00744%, is the one the BX-804 reading gave.
     */
    @Test
    void aBlueprintWithItsOwnMeAndTeIsRead() {
        var recipe = new Recipe(new TypeId(41236), 1, 36000, List.of(
                new RecipeMaterial(new TypeId(34), 1313168), new RecipeMaterial(new TypeId(35), 430794),
                new RecipeMaterial(new TypeId(36), 135255), new RecipeMaterial(new TypeId(37), 31628),
                new RecipeMaterial(new TypeId(38), 5480), new RecipeMaterial(new TypeId(39), 1748),
                new RecipeMaterial(new TypeId(40), 1146)));
        var prices = new java.util.HashMap<>(ADJUSTED_PRICES);
        prices.put(new TypeId(37), new BigDecimal("186.09791763273623"));
        var terms = new JobTerms(8, 14, facility("1", "0", "20", "0", "4", "2.00744", "0", "4"),
                new BuilderTerms(5, 5, new BigDecimal("4")));

        var job = JobCosting.cost(recipe, 1, terms, type -> Optional.of(prices.get(type)));

        assertThat(job.materials()).extracting(RecipeMaterial::quantity)
                .containsExactly(1196034L, 392368L, 123191L, 28807L, 4992L, 1593L, 1044L);
        assertThat(job.seconds()).isEqualTo(4 * 3600 + 29 * 60 + 29);
        assertWithinOneIsk(job.installCost(), "1653860");
    }

    /**
     * Read in the game on 2026-11-18, in the wormhole Azbel: 235 runs of the ME 10 / TE 20 Cap Booster 800 copy (1,500 runs
     * remaining). The window showed an output of x 2350, a duration of 1D 01:34:05, a total job cost of 467,319 ISK and, on
     * hovering Nocxium, **2,723**, with Zydrine and Megacyte at 1,257. Nocxium is 13 x 235 x 0.891 = 2,722.005, exactly on
     * the half: half up gives 2,723 and half even would give 2,722. The cost hover showed an estimated item value of
     * 10,280,639 ISK, an index line of 58,430 ISK (an index of 0.56835%, a little lower than the 0.56883% of the first
     * reading), a role bonus of -2,337, a gross cost of 56,093 and an SCC surcharge of 411,226, which the window adds to 467,319
     * (the exact sum is 467,318.3).
     */
    @Test
    void anExactHalfInTheThirdDecimalRoundsUp() {
        var job = capBoosterJob(235, 10, 20, "0.56835");

        assertThat(job.materials()).extracting(RecipeMaterial::quantity).containsSubsequence(2723L, 1257L, 1257L);
        assertThat(job.unitsMade()).isEqualTo(2350);
        assertThat(job.seconds()).isEqualTo(86400 + 3600 + 34 * 60 + 5);
        assertWithinOneIsk(job.estimatedItemValue(), "10280639");
        assertWithinOneIsk(job.installCost(), "467319");
    }

    /**
     * Read in the game on 2026-11-18, in the high-sec Azbel: 1 run of an Atron Blueprint copy (a standard T1 frigate) at ME 10
     * and TE 20, built by the character with the BX-804. The window showed 28.51K, 5,346, 2,228 and 446, an estimated item
     * value of 367,708 ISK, an index line of 7,382 ISK less the 4.0% role bonus (295), a gross cost of 7,086, an SCC surcharge of
     * 14,708, a total job cost of 21,794 ISK and a duration of 00:41:47 (2,507 s; without the implant it would be 2,611 s). A ship
     * is costed like a module: no per-product factor was needed. Pyerite is exactly 6,000 x 0.891 = 5,346, which stays 5,346, and
     * Mexallon and Isogen are on a half unit (2,227.5 and 445.5) and round up.
     */
    @Test
    void aStandardShipBlueprintIsCostedLikeAnyOther() {
        var recipe = new Recipe(new TypeId(608), 1, 6000, List.of(
                new RecipeMaterial(new TypeId(34), 32000), new RecipeMaterial(new TypeId(35), 6000),
                new RecipeMaterial(new TypeId(36), 2500), new RecipeMaterial(new TypeId(37), 500)));
        var prices = new java.util.HashMap<>(ADJUSTED_PRICES);
        prices.put(new TypeId(37), new BigDecimal("186.09791763273623"));
        var terms = new JobTerms(10, 20, facility("1", "0", "20", "0", "4", "2.00744", "0", "4"),
                new BuilderTerms(5, 5, new BigDecimal("4")));

        var job = JobCosting.cost(recipe, 1, terms, type -> Optional.of(prices.get(type)));

        assertThat(job.materials()).extracting(RecipeMaterial::quantity).containsExactly(28512L, 5346L, 2228L, 446L);
        assertThat(job.seconds()).isEqualTo(41 * 60 + 47);
        assertWithinOneIsk(job.estimatedItemValue(), "367708");
        assertWithinOneIsk(job.installCost(), "21794");
    }

    /**
     * Read in the game on 2026-11-18, the "Maximum runs" of four blueprint originals in the high-sec Azbel: 5,295 for the Cap
     * Booster 800 at ME 0 / TE 0 (one run takes 489.6 s), 6,618 at ME 10 / TE 20 (391.68 s), 6,894 with a BX-804 (376.0128 s)
     * and 161 for the 10000MN Afterburner I at ME 8 / TE 14 (16,168.55 s). 30 days is 2,592,000 s. The first one separates the
     * time of one run before rounding (5,294.1, so 5,295) from the rounded 490 s (5,289.8, so 5,290).
     */
    @Test
    void theMaximumRunsOfAJobAreTheRunsThatReachThirtyDays() {
        var azbel = facility("1", "0", "20", "0", "4", "0.56883", "0", "4");

        assertThat(JobCosting.maximumRuns(900, new JobTerms(0, 0, azbel, ALL_SKILLS_V))).isEqualTo(5295);
        assertThat(JobCosting.maximumRuns(900, new JobTerms(10, 20, azbel, ALL_SKILLS_V))).isEqualTo(6618);
        assertThat(JobCosting.maximumRuns(900,
                new JobTerms(10, 20, azbel, new BuilderTerms(5, 5, new BigDecimal("4"))))).isEqualTo(6894);
        assertThat(JobCosting.maximumRuns(36000,
                new JobTerms(8, 14, azbel, new BuilderTerms(5, 5, new BigDecimal("4"))))).isEqualTo(161);
    }

    @Test
    void aSingleRunIsAllowedEvenWhenItTakesLongerThanThirtyDays() {
        // 4,000,000 s at TE 20 is 3,200,000 s, more than the 2,592,000 s of 30 days. Not read in the game yet.
        assertThat(JobCosting.maximumRuns(4_000_000, JobTerms.PLAN)).isEqualTo(1);
    }

    /**
     * The lines of the game's cost hover for the first two readings (100 runs, ME 0 / TE 0). Wormhole: index line 24,885,
     * role bonus -995, gross cost 23,889, SCC 174,990, total 198,879. High-sec: 87,894, -3,516, 84,378, 174,990 and
     * 259,368. The window rounds each line and adds the rounded gross cost and taxes, so the exact lines are within 1 ISK.
     */
    @Test
    void theInstallCostIsBrokenIntoTheLinesOfTheGamesHover() {
        var wormhole = capBoosterJob(100, 0, 0, "0.56883").installBreakdown();
        var highSec = capBoosterJob(100, 0, 0, "2.00912").installBreakdown();

        assertWithinOneIsk(wormhole.estimatedItemValue(), "4374740");
        assertWithinOneIsk(wormhole.jobFee(), "24885");
        assertWithinOneIsk(wormhole.structureBonus(), "-995");
        assertWithinOneIsk(wormhole.grossCost(), "23889");
        assertWithinOneIsk(wormhole.sccSurcharge(), "174990");
        assertThat(wormhole.facilityTax()).isEqualByComparingTo("0");
        assertWithinOneIsk(wormhole.total(), "198879");
        assertWithinOneIsk(highSec.jobFee(), "87894");
        assertWithinOneIsk(highSec.structureBonus(), "-3516");
        assertWithinOneIsk(highSec.grossCost(), "84378");
        assertWithinOneIsk(highSec.total(), "259368");
        assertThat(highSec.grossCost().add(highSec.totalTaxes())).isEqualByComparingTo(highSec.total());
    }

    /**
     * What the trace keeps of a material's rounding. 235 runs of the ME 10 copy: Nocxium is 13 x 235 x 0.891 = 2,722.005,
     * which is 2,722.01 to two decimals and 2,723 (the game showed 2,723). The 47-run reading: Mexallon is 10,553.004, which
     * is 10,553.00 and so 10,553. The one-per-run minimum is not read in the game (the wiki's rule): 100 runs of a material
     * with a base of 1 at ME 10 in an Azbel is 89.10, which rounds up to 90, so the minimum raises it to 100.
     */
    @Test
    void aMaterialKeepsItsExactProductItsTwoDecimalFigureAndWhetherTheMinimumApplied() {
        var half = capBoosterJob(235, 10, 20, "0.56836").materialUses().get(3);
        var near = capBoosterJob(47, 10, 20, "0.56883").materialUses().get(2);
        var azbel = facility("1", "0", "20", "0", "4", "2", "0", "4");
        var minimum = JobCosting.materialUse(new TypeId(1), 1, 100, new JobTerms(10, 0, azbel, ALL_SKILLS_V));

        assertThat(half.exactProduct()).isEqualByComparingTo("2722.005");
        assertThat(half.roundedToTwoDecimals()).isEqualByComparingTo("2722.01");
        assertThat(half.quantity()).isEqualTo(2723);
        assertThat(half.raisedToMinimum()).isFalse();
        assertThat(near.exactProduct()).isEqualByComparingTo("10553.004");
        assertThat(near.roundedToTwoDecimals()).isEqualByComparingTo("10553.00");
        assertThat(near.quantity()).isEqualTo(10553);
        assertThat(minimum.exactProduct()).isEqualByComparingTo("89.1");
        assertThat(minimum.quantity()).isEqualTo(100);
        assertThat(minimum.raisedToMinimum()).isTrue();
    }

    /**
     * The duration hover of the BX-804 reading: Blueprint Time Efficiency -20.0%, Skills and Implants -34.7%, Structure Role
     * Bonus -20.0%, 10:26:41 (37,601 s) and "Maximum runs" 6,894 on the original.
     */
    @Test
    void theTimeIsBrokenIntoTheModifiersOfTheDurationHover() {
        var azbel = facility("1", "0", "20", "0", "4", "2.00744", "0", "4");
        var terms = new JobTerms(10, 20, azbel, new BuilderTerms(5, 5, new BigDecimal("4")));

        var time = JobCosting.cost(CAP_BOOSTER_800, 100, terms, type -> Optional.of(ADJUSTED_PRICES.get(type))).time();

        assertThat(TimeBreakdown.savedPercent(time.blueprintFactor())).isEqualByComparingTo("20");
        assertThat(TimeBreakdown.savedPercent(time.skillsAndImplantsFactor())).isCloseTo(new BigDecimal("34.72"), Offset.offset(new BigDecimal("0.01")));
        assertThat(TimeBreakdown.savedPercent(time.structureFactor())).isEqualByComparingTo("20");
        assertThat(time.exactSeconds()).isEqualByComparingTo("37601.28");
        assertThat(time.seconds()).isEqualTo(37601);
        assertThat(time.maximumRuns()).isEqualTo(6894);
    }

    /**
     * Read in the game on 2026-11-18, in the high-sec Azbel: 1,000 runs of a Combat Scanner Probe I copy at ME 10 / TE 0, with
     * Industry V, Advanced Industry V and a BX-804. Isogen and Nocxium have a base of 1, so 1,000 runs at 0.891 is 891.00, and
     * both showed **1,000**: the one-per-run minimum is real (without it they would show 891). The window showed 2,673, 8,019,
     * 12.47K, 1,000, 1,000, 1,782 and 6,237 for the seven materials, a duration of 3D 15:02:24 (313,344 s), a "Maximum runs"
     * of 8,273 and a total job cost of 374,738 ISK. The cost is not asserted: its index (2.0021%) is worked back from the total.
     */
    @Test
    void aMaterialWithABaseOfOneIsRaisedToOneForEachRun() {
        var probe = new Recipe(new TypeId(30028), 1, 600, List.of(
                new RecipeMaterial(new TypeId(37), 1), new RecipeMaterial(new TypeId(38), 1),
                new RecipeMaterial(new TypeId(40), 2), new RecipeMaterial(new TypeId(34), 3),
                new RecipeMaterial(new TypeId(16275), 7), new RecipeMaterial(new TypeId(35), 9),
                new RecipeMaterial(new TypeId(36), 14)));
        var azbel = facility("1", "0", "20", "0", "4", "2.002122", "0", "4");
        var terms = new JobTerms(10, 0, azbel, new BuilderTerms(5, 5, new BigDecimal("4")));

        var job = JobCosting.cost(probe, 1000, terms, type -> Optional.empty());

        assertThat(job.materials()).extracting(RecipeMaterial::quantity)
                .containsExactly(1000L, 1000L, 1782L, 2673L, 6237L, 8019L, 12474L);
        assertThat(job.materialUses().get(0).exactProduct()).isEqualByComparingTo("891");
        assertThat(job.materialUses().get(0).raisedToMinimum()).isTrue();
        assertThat(job.materialUses().get(2).raisedToMinimum()).isFalse();
        assertThat(job.seconds()).isEqualTo(3 * 86400 + 15 * 3600 + 2 * 60 + 24);
        assertThat(job.time().maximumRuns()).isEqualTo(8273);
    }

    @Test
    void impossibleLevelsAndPercentagesAreRefused() {
        assertThatThrownBy(() -> new JobTerms(11, 0, FacilityTerms.NONE, BuilderTerms.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JobTerms(0, 21, FacilityTerms.NONE, BuilderTerms.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BuilderTerms(6, 0, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> facility("101", "0", "0", "0", "0", "0", "0", "0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JobCosting.cost(new Recipe(HULL, 1, 1, List.of()), 0, JobTerms.PLAN, t -> Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
