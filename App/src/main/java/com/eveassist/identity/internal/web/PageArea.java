package com.eveassist.identity.internal.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Which area of the menu the page being drawn belongs to, as {@code area} for the layout: it marks that menu entry as the current page and
 * colours it and the page's title (app.css, {@code data-area}). It is told from the address, so a new page under an address listed here
 * needs nothing, and a new menu entry is added here.
 */
@ControllerAdvice
class PageArea {

    /** An address and everything under it, and the area it belongs to. */
    private record Address(String path, String area) {
    }

    private static final List<Address> ADDRESSES = List.of(
            new Address("/plans", "plans"), new Address("/try", "plans"),
            new Address("/projects", "projects"), new Address("/project-defaults", "projects"),
            new Address("/facilities", "facilities"), new Address("/market/blueprint-copies", "blueprint-copies"),
            new Address("/inventory", "inventory"), new Address("/blueprints", "blueprints"), new Address("/characters", "characters"));

    /** Null for a page of no menu entry, such as the home page or the account. */
    @ModelAttribute("area")
    String area(HttpServletRequest request) {
        var path = request.getRequestURI().substring(request.getContextPath().length());
        return ADDRESSES.stream().filter(address -> path.equals(address.path()) || path.startsWith(address.path() + "/"))
                .map(Address::area).findFirst().orElse(null);
    }
}
