package com.eveassist.inventory.internal.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The filter each list page was last shown with, kept in the user's session. The address is the filter, so an address
 * always shows what it says, and one without a filter is the list unfiltered, whether it was typed, bookmarked or reached
 * with the Back button. Only a link that asks to resume, as the menu does with {@code resume=1}, is sent on to the
 * address the page had last, so coming back through the menu finds the list as it was left. Each page keeps its own:
 * the two do not share a filter, because their columns differ. It lasts as long as the session, and nothing is kept for
 * another user.
 */
@Component
class RememberedFilters {

    enum Page {
        INVENTORY("filter.inventory"), BLUEPRINTS("filter.blueprints");

        private final String attribute;

        Page(String attribute) {
            this.attribute = attribute;
        }
    }

    /** More than this is not a filter anyone chose, so it is not kept. */
    private static final int LONGEST = 2_000;

    /** The query the session has for the page, if any. */
    Optional<String> recall(HttpServletRequest request, Page page) {
        var session = request.getSession(false);
        return session == null ? Optional.empty() : Optional.ofNullable((String) session.getAttribute(page.attribute));
    }

    /** True when the request is a link that asks for the page as it was left, such as the menu's. */
    static boolean resumes(HttpServletRequest request) {
        return "1".equals(request.getParameter("resume"));
    }

    /** The address to go on to for a link that resumes: the remembered filter's, or the page's own when there is none. */
    String resumeAt(HttpServletRequest request, Page page, String path) {
        return "redirect:" + path + recall(request, page).map(query -> "?" + query).orElse("");
    }

    void remember(HttpServletRequest request, Page page, String query) {
        if (query.length() <= LONGEST) {
            request.getSession(true).setAttribute(page.attribute, query);
        }
    }
}
