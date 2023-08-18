package com.eveassist.api.sde.chr.impl;

import com.eveassist.api.sde.chr.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ChrDaoImpl.class})
class ChrDaoImplTest {
    @Autowired
    ChrDaoImpl cut;

    @Test
    void contextLoads() {
        assertThat(cut).isNotNull();
    }

    @Test
    void givenValidId_thenAncestryReturned() {
        ChrAncestry chrAncestry = cut.getAncestry(1);
        assertThat(chrAncestry).isNotNull();
        assertThat(chrAncestry.getAncestryName()).isEqualTo("Liberal Holders");
    }

    @Test
    void givenValidId_thenAttributeReturned() {
        ChrAttribute chrAttribute = cut.getAttribute(2);
        assertThat(chrAttribute).isNotNull();
        assertThat(chrAttribute.getAttributeName()).isEqualTo("Charisma");
    }

    @Test
    void givenValidId_thenBloodlineReturned() {
        ChrBloodline chrBloodline = cut.getBloodline(3);
        assertThat(chrBloodline).isNotNull();
        assertThat(chrBloodline.getBloodlineName()).isEqualTo("Sebiestor");
    }

    @Test
    void givenValidId_thenFactionReturned() {
        ChrFaction chrFaction = cut.getFaction(500004);
        assertThat(chrFaction).isNotNull();
        assertThat(chrFaction.getFactionName()).isEqualTo("Gallente Federation");
    }

    @Test
    void givenValidId_thenRaceReturned() {
        ChrRace chrRace = cut.getRace(16);
        assertThat(chrRace).isNotNull();
        assertThat(chrRace.getRaceName()).isEqualTo("Jove");
    }
}