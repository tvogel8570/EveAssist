package com.eveassist.sde;

import com.eveassist.sde.internal.IndustryData;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Read-only industry data from the SDE, held in memory. */
@Service
public class SdeService {

    private final IndustryData data;

    SdeService(IndustryData data) {
        this.data = data;
    }

    public String version() {
        return data.version();
    }

    public Optional<ItemType> itemType(TypeId id) {
        return data.itemType(id);
    }

    public Optional<Recipe> recipeFor(TypeId product) {
        return data.recipeFor(product);
    }

    /** The manufacturing recipe produced by a blueprint type, when its output is in the SDE. */
    public Optional<Recipe> recipeOfBlueprint(TypeId blueprint) {
        return data.recipeOfBlueprint(blueprint);
    }

    public List<ItemType> searchBuildable(String text, int limit) {
        return data.searchBuildable(text, limit);
    }

    /** Blueprints whose names contain the text, for choosing one to price. */
    public List<ItemType> searchBlueprints(String text, int limit) {
        return data.searchBlueprints(text, limit);
    }

    /** True for an Abyssal (mutated) item type. Names are no guide, so this comes from the SDE's mutaplasmid mappings. */
    public boolean isAbyssal(TypeId id) {
        return data.isAbyssal(id);
    }

    /** The volume of one unit in cubic meters, packaged where it has a packaged volume, for hauling. */
    public Optional<java.math.BigDecimal> unitVolume(TypeId id) {
        return data.unitVolume(id);
    }

    /** The skills a product's blueprint requires that shorten its manufacturing time (SDE attribute 1982). */
    public List<TimeSkill> timeSkillsOf(TypeId product) {
        return data.timeSkillsOf(product);
    }

    public boolean isBlueprint(TypeId id) {
        return data.isBlueprint(id);
    }

    public Set<TypeId> materialTypes() {
        return data.materialTypes();
    }
}
