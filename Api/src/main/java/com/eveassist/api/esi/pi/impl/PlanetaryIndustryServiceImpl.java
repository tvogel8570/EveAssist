package com.eveassist.api.esi.pi.impl;

import com.eveassist.api.esi.pi.PlanetaryIndustryService;
import com.eveassist.api.esi.pi.dto.*;
import com.eveassist.api.esi.response.CharactersPlanetsDetailDto;
import com.eveassist.api.esi.response.CharactersPlanetsDto;
import com.eveassist.api.esi.response.PlanetPinContentsDto;
import com.eveassist.api.esi.response.PlanetPinDto;
import com.eveassist.api.sde.pi.PiDao;
import com.eveassist.api.sde.pi.entity.PiPinDto;
import com.eveassist.api.sde.pi.entity.PiViewDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlanetaryIndustryServiceImpl implements PlanetaryIndustryService {
    private final PiDao piDao;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssz");

    @Override
    public PlanetPiDto summarizePlanet(CharactersPlanetsDto planetsDto, CharactersPlanetsDetailDto planetsDetailDto) {
        List<PiFactory> factories = new ArrayList<>();
        List<PiProduct> products = new ArrayList<>();
        List<PiExtractor> extractors = new ArrayList<>();
        List<PiStorage> storage = new ArrayList<>();

        for (PlanetPinDto pin : planetsDetailDto.pins()) {
            // determine the type of pin
            PiPinDto piPinDto = piDao.piPinByType(pin.type_id());
            switch (piPinDto.getGroupName()) {
                case "Processors" -> {
                    PiFactory piFactory = this.parseFactory(pin);
                    for (PiFactoryProduct product : piFactory.inputs()) {
                        products.add(new PiProduct(true, product.typeId(), product.name(), product.quantity()));
                    }
                    products.add(new PiProduct(false, piFactory.output().typeId(), piFactory.output().name(),
                            piFactory.output().quantity()));
                    factories.add(piFactory);
                }
                case "Command Centers", "Storage Facilities", "Spaceports" -> {
                    PiStorage piStorage = parseStorage(pin, piPinDto);
                    for (PiProduct product : piStorage.products()) {
                        products.add(new PiProduct(false, product.productTypeId(), product.productName(),
                                product.amount()));
                    }
                    storage.add(piStorage);
                }
                case "Extractor Control Units" -> {
                    PiExtractor piExtractor = parseExtractor(pin);
                    products.add(new PiProduct(false, piExtractor.productTypeId(), piExtractor.productName(),
                            piExtractor.qtyPerCycle().longValue()));
                    extractors.add(piExtractor);
                }
                default -> log.warn("Unknown Pin type [{}] group [{}]", pin.type_id(), piPinDto.getGroupName());
            }
        }

        return new PlanetPiDto(
                planetsDto.solar_system_id(),
                piDao.celestialNameById(planetsDto.solar_system_id()),
                planetsDto.planet_id(),
                piDao.celestialNameById(planetsDto.planet_id()),
                planetsDto.planet_type(),
                this.determinePlanetClassification(factories, extractors),
                factories,
                products,
                extractors,
                storage,
                LocalDateTime.parse(planetsDto.last_update(), formatter));
    }

    private String determinePlanetClassification(List<PiFactory> factories,
                                                 List<PiExtractor> extractors) {
        if (!extractors.isEmpty() && factories.isEmpty())
            return "P0 extraction";
        if (!extractors.isEmpty() && maxFactoryType(factories) == 1)
            return "P1 extraction";
        if (extractors.size() == 2 && maxFactoryType(factories) == 2)
            return "P2 single planet production";
        if (extractors.isEmpty() && !factories.isEmpty())
            switch (maxFactoryType(factories)) {
                case 1 -> {
                    return "P1 factory planet";
                }
                case 2 -> {
                    return "P2 / P3 factory planet";
                }
                case 3 -> {
                    return "P4 factory planet";
                }
                default -> {
                    return "Unknown";
                }
            }

        return "Unknown";
    }

    private int maxFactoryType(List<PiFactory> factories) {
        int max = 0;
        for (PiFactory factory : factories)
            switch (factory.factoryType()) {
                case "Basic" -> max = Math.max(max, 1);
                case "Advanced" -> max = Math.max(max, 2);
                case "High-Tech" -> max = 3;
            }

        return max;
    }

    private PiStorage parseStorage(PlanetPinDto planetPinDto, PiPinDto piPinDto) {
        List<PiProduct> products = new ArrayList<>();
        for (PlanetPinContentsDto product : planetPinDto.contents()) {
            String productName = piDao.typeNameByTypeId(product.type_id());
            products.add(new PiProduct(true, product.type_id(), productName, product.amount()));
        }
        return new PiStorage(
                piPinDto.getTypeName(),
                piPinDto.getCapacity(),
                !piPinDto.getGroupName().equals("Storage Facilities"),
                planetPinDto.expiry_time(),
                products);
    }

    private PiExtractor parseExtractor(PlanetPinDto planetPinDto) {
        return new PiExtractor(
                planetPinDto.expiry_time(),
                planetPinDto.install_time(),
                planetPinDto.last_cycle_start(),
                planetPinDto.extractor_details().cycle_time(),
                planetPinDto.extractor_details().heads().length,
                planetPinDto.extractor_details().product_type_id(),
                piDao.typeNameByTypeId(planetPinDto.extractor_details().product_type_id()),
                planetPinDto.extractor_details().qty_per_cycle(),
                planetPinDto.extractor_details().head_radius()
        );
    }

    private PiFactory parseFactory(PlanetPinDto planetPinDto) {
        PiFactoryProduct output = null;
        List<PiFactoryProduct> inputs = new ArrayList<>();
        List<PiViewDto> piViewDto = piDao.piDetailsBySchematicIdAndFactoryId(planetPinDto.schematic_id(), planetPinDto.type_id());
        for (PiViewDto factory : piViewDto) {
            if (Boolean.TRUE.equals(factory.getIsInput())) {
                inputs.add(new PiFactoryProduct(factory.getProductTypeId(), factory.getProductName(), factory.getQuantity()));
            } else {
                output = new PiFactoryProduct(factory.getProductTypeId(), factory.getProductName(), factory.getQuantity());
            }
        }

        return new PiFactory(
                piViewDto.get(0).getSchematicId(),
                piViewDto.get(0).getSchematicName(),
                piViewDto.get(0).getCycleTime(),
                piViewDto.get(0).getFactoryTypeId(),
                piViewDto.get(0).getFactoryType(),
                inputs,
                output);
    }
}
