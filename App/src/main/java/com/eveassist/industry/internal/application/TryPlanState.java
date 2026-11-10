package com.eveassist.industry.internal.application;

import com.eveassist.sde.TypeId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A plan without an account, written as text so that the browser can keep it and send it back with each request, for
 * example {@code p=1001:2,1004:200;b=1002}: products with their units, then the parts that are bought. Nothing about
 * it is stored on the server.
 */
record TryPlanState(List<ProductEntry> products, Set<TypeId> bought) {

    /** Long enough for ten products and every part they could use, and too short to be used to send anything else. */
    static final int LONGEST = 4_000;

    private static final Pattern FORMAT = Pattern.compile("p=(\\d{1,9}:\\d{1,9}(,\\d{1,9}:\\d{1,9})*)?;b=(\\d{1,9}(,\\d{1,9})*)?");

    record ProductEntry(int type, long quantity) {
    }

    static TryPlanState empty() {
        return new TryPlanState(List.of(), Set.of());
    }

    /** Anything that is not in the format is taken as an empty plan, so a damaged saved plan never breaks the page. */
    static TryPlanState parse(String text) {
        if (text == null || text.length() > LONGEST || !FORMAT.matcher(text).matches()) {
            return empty();
        }
        var parts = text.split(";b=", -1);
        var products = new ArrayList<ProductEntry>();
        for (var item : parts[0].substring(2).split(",")) {
            if (!item.isEmpty()) {
                var pair = item.split(":");
                products.add(new ProductEntry(Integer.parseInt(pair[0]), Long.parseLong(pair[1])));
            }
        }
        var bought = new LinkedHashSet<TypeId>();
        for (var item : parts[1].split(",")) {
            if (!item.isEmpty()) {
                bought.add(new TypeId(Integer.parseInt(item)));
            }
        }
        return new TryPlanState(products, bought);
    }

    String format() {
        var text = new StringBuilder("p=");
        for (int i = 0; i < products.size(); i++) {
            text.append(i == 0 ? "" : ",").append(products.get(i).type()).append(':').append(products.get(i).quantity());
        }
        text.append(";b=");
        text.append(String.join(",", bought.stream().map(TypeId::value).sorted().map(String::valueOf).toList()));
        return text.toString();
    }
}
