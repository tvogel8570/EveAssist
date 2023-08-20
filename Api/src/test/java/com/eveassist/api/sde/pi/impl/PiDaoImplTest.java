package com.eveassist.api.sde.pi.impl;

import com.eveassist.api.sde.pi.PiDao;
import com.eveassist.api.sde.pi.entity.PiPinDto;
import com.eveassist.api.sde.pi.entity.PiViewDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PiDaoImpl.class})
class PiDaoImplTest {
    @Autowired
    PiDao cut;

    @Test
    void contextLoads() {
        assertThat(cut).isNotNull();
    }

    @Test
    void givenValidSchemaId_thenSchemaDetails() {
        List<PiViewDto> schema = cut.piDetailsBySchematicId(65);
        assertThat(schema).isNotNull().hasSize(24);
    }

    @Test
    void givenValidSchemaIdAndFactoryType_thenSchemaDetails() {
        List<PiViewDto> schema = cut.piDetailsBySchematicIdAndFactoryId(65, 2472);
        assertThat(schema).isNotNull().hasSize(3);
    }

    @Test
    void givenValidPinType_thenPiPinDto() {
        PiPinDto piPinDto = cut.piPinByType(2544);
        assertThat(piPinDto).isNotNull();
        assertThat(piPinDto.getTypeName()).isEqualTo("Barren Launchpad");
        assertThat(piPinDto.getGroupName()).isEqualTo("Spaceports");
    }

    @Test
    void givenValidType_thenName() {
        String name = cut.typeNameByTypeId(2393);
        assertThat(name).isNotNull().isEqualTo("Bacteria");
    }
}