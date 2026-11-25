package com.eveassist.sde;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The two reads a project's costing needs from the SDE: the volume to haul, and the skills that shorten a blueprint's time. */
class SdeIndustryDataIT extends IntegrationTest {

    private static final TypeId INDUSTRY = new TypeId(3380);
    private static final TypeId NANITE = new TypeId(11442);
    private static final TypeId MECHANICAL = new TypeId(11452);

    @Autowired
    SdeService sde;

    @Test
    void aShipIsHauledPackagedAndAnItemWithoutAPackagedVolumeUsesItsOwn() {
        assertThat(sde.unitVolume(HAULER)).hasValueSatisfying(volume -> assertThat(volume).isEqualByComparingTo("1000"));
        assertThat(sde.unitVolume(COMPONENT)).hasValueSatisfying(volume -> assertThat(volume).isEqualByComparingTo("2"));
        assertThat(sde.unitVolume(TRITANIUM)).hasValueSatisfying(volume -> assertThat(volume).isEqualByComparingTo("0.01"));
    }

    @Test
    void aTypeNoRecipeMakesOrUsesHasNoVolumeHere() {
        assertThat(sde.unitVolume(INDUSTRY)).isEmpty();
    }

    @Test
    void onlyRequiredSkillsThatSaveTimeAreListedAndTheirPercentIsPositive() {
        // the Hauler's blueprint requires Industry, Mechanical Engineering and Nanite Engineering. Industry has no
        // manufactureTimePerLevel (it is the job's base skill), the other two save 2% and 1% a level
        assertThat(sde.timeSkillsOf(HAULER)).extracting(TimeSkill::skill, skill -> skill.percentPerLevel().intValue())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(NANITE, 1), org.assertj.core.groups.Tuple.tuple(MECHANICAL, 2));
        assertThat(sde.timeSkillsOf(HAULER)).extracting(TimeSkill::skill).doesNotContain(INDUSTRY);
    }

    @Test
    void aBlueprintThatRequiresOnlyIndustryHasNoTimeSkillsAndAnUnknownProductHasNone() {
        assertThat(sde.timeSkillsOf(COMPONENT)).isEmpty();
        assertThat(sde.timeSkillsOf(new TypeId(999_999))).isEmpty();
        assertThat(sde.timeSkillsOf(SECOND_SHIP)).extracting(TimeSkill::percentPerLevel)
                .allSatisfy(percent -> assertThat(percent).isEqualByComparingTo(BigDecimal.ONE));
    }
}
