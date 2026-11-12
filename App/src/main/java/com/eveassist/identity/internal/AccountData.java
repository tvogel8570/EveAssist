package com.eveassist.identity.internal;

import com.eveassist.identity.AccountDataSource;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** What the account holds, gathered from every module's {@link AccountDataSource}: the export, and the counts shown before a deletion. */
@Service
public class AccountData {

    private final List<AccountDataSource> sources;
    private final Clock clock;

    AccountData(List<AccountDataSource> sources, Clock clock) {
        this.sources = sources;
        this.clock = clock;
    }

    private Map<String, List<Map<String, Object>>> sections(AccountDetails account) {
        var all = new LinkedHashMap<String, List<Map<String, Object>>>();
        sources.forEach(source -> all.putAll(source.sections(account.id())));
        return all;
    }

    /** Everything the user may take with them, as one JSON-ready document. */
    public Map<String, Object> export(AccountDetails account) {
        var document = new LinkedHashMap<String, Object>();
        document.put("application", "EveAssist");
        document.put("exportedAt", clock.instant().toString());
        document.put("account", Map.of("displayName", account.displayName(), "id", account.id().value().toString()));
        document.putAll(sections(account));
        return document;
    }

    /** Section name to the number of rows, for the page that asks before deleting. */
    public Map<String, Integer> counts(AccountDetails account) {
        var counts = new LinkedHashMap<String, Integer>();
        sections(account).forEach((name, rows) -> counts.put(name, rows.size()));
        return counts;
    }
}
