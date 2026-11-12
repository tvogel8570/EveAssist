package com.eveassist.characters.internal;

import com.eveassist.identity.AccountDataSource;
import com.eveassist.identity.AccountId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** The account's linked characters and turned-on features, for the export. No token, and no ESI data (assets and the like). */
@Component
class CharactersAccountData implements AccountDataSource {

    private final CharacterRepository characters;
    private final FeatureRepository features;

    CharactersAccountData(CharacterRepository characters, FeatureRepository features) {
        this.characters = characters;
        this.features = features;
    }

    @Override
    public Map<String, List<Map<String, Object>>> sections(AccountId account) {
        var sections = new LinkedHashMap<String, List<Map<String, Object>>>();
        sections.put("characters", characters.listFor(account).stream().map(character -> {
            var row = new LinkedHashMap<String, Object>();
            row.put("name", character.name());
            row.put("eveCharacterId", character.eveCharacterId());
            row.put("grantedScopes", character.scopes().stream().sorted().toList());
            row.put("state", character.state());
            row.put("linkedAt", character.linkedAt().toString());
            return (Map<String, Object>) row;
        }).toList());
        sections.put("features", features.turnedOn(account).stream().map(Enum::name).sorted()
                .map(name -> Map.<String, Object>of("feature", name)).toList());
        return sections;
    }
}
