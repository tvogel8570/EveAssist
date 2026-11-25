package com.eveassist.industry.internal.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.ProjectId;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An issued quote as the text and the CSV that go to its buyer. It is made from the quote as it was frozen when it was issued (its price, buyer, note
 * and expiry, and the products and quantities of its inputs), so it never changes, and from nothing of the cost. Another account's quote looks missing.
 */
@Service
@PrimaryPort
@Transactional(readOnly = true)
public class QuoteExport {

    private final ProjectRepository projects;
    private final ProjectQuoteRepository quotes;
    private final ProjectRecipes recipes;

    QuoteExport(ProjectRepository projects, ProjectQuoteRepository quotes, ProjectRecipes recipes) {
        this.projects = projects;
        this.quotes = quotes;
        this.recipes = recipes;
    }

    public ShareableQuote of(Viewer viewer, ProjectId project, int number) {
        var found = projects.find(viewer, project).orElseThrow(ProjectNotFoundException::new);
        var summary = quotes.find(viewer, project, number).orElseThrow(QuoteNotFoundException::new);
        var inputs = quotes.findInputs(viewer, project, number).orElseThrow(QuoteNotFoundException::new);
        var items = inputs.products().stream()
                .map(product -> new QuoteFacts.Item(recipes.name(new com.eveassist.sde.TypeId(product.type())), product.quantity())).toList();
        var facts = new QuoteFacts(summary.number(), summary.buyerName(), summary.note(), summary.expiresAt(), summary.price(), items);
        return new ShareableQuote(QuoteText.text(facts), QuoteText.csv(facts), QuoteText.fileName(found.name(), number));
    }
}
