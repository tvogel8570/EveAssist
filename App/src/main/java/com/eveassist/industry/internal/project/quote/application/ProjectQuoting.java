package com.eveassist.industry.internal.project.quote.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.FacilityId;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.costing.domain.FacilityTerms;
import com.eveassist.industry.internal.facility.application.FacilityTermsLookup;
import com.eveassist.industry.internal.project.application.ProjectChanged;
import com.eveassist.industry.internal.project.application.ProjectChangedException;
import com.eveassist.industry.internal.project.application.ProjectNotFoundException;
import com.eveassist.industry.internal.project.application.ProjectRepository;
import com.eveassist.industry.internal.project.costing.application.CostingOutOfDateException;
import com.eveassist.industry.internal.project.costing.application.ProjectCostingRepository;
import com.eveassist.industry.internal.project.domain.ProjectLockedException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quotes: issuing one, making a new version from one, and deleting one. None of these asks ESI or costs anything: a quote is issued
 * from the costing already stored for the version the caller saw, so what was priced is what is quoted.
 */
@Service
@PrimaryPort
@Transactional
public class ProjectQuoting {

    private final ProjectRepository projects;
    private final ProjectCostingRepository costings;
    private final ProjectQuoteRepository quotes;
    private final FacilityTermsLookup facilities;
    private final Clock clock;

    ProjectQuoting(ProjectRepository projects, ProjectCostingRepository costings, ProjectQuoteRepository quotes,
            FacilityTermsLookup facilities, Clock clock) {
        this.projects = projects;
        this.costings = costings;
        this.quotes = quotes;
        this.facilities = facilities;
        this.clock = clock;
    }

    /**
     * Issues the next quote and locks the project. The project must be a Draft at the version the caller saw, and must have a stored costing
     * for that very version that can be quoted: if it was edited since it was costed the answer is {@link CostingOutOfDateException}
     * ("cost it again"), and if the costing has a warning that blocks a quote it is {@link NotQuotableException}. Nothing is costed here.
     */
    public QuoteIssued issue(Viewer viewer, IssueQuoteCommand command) {
        var project = projects.find(viewer, command.project()).orElseThrow(ProjectNotFoundException::new);
        if (!project.draft()) {
            throw new ProjectLockedException();
        }
        if (project.version() != command.expectedVersion()) {
            throw new ProjectChangedException();
        }
        if (command.expiresAt() != null && !command.expiresAt().isAfter(clock.instant())) {
            throw new InvalidQuoteException("The quote must expire in the future");
        }
        var costing = costings.findDraft(viewer, command.project()).orElseThrow(CostingOutOfDateException::new);
        if (costing.costedProjectVersion() != project.version()) {
            throw new CostingOutOfDateException();
        }
        var price = costing.document().quote();
        if (!costing.quotable() || price == null) {
            throw new NotQuotableException();
        }
        var buyer = command.buyerName() != null ? command.buyerName() : project.config().buyerName();
        var note = command.note() != null ? command.note() : project.config().note();
        // what is quoted is what the Draft held, with the buyer and note the quote is issued with
        project.buyer(buyer, note);
        var inputs = QuoteInputs.snapshot(project, termsOf(viewer, QuoteInputs.facilitiesOf(project)));
        var issued = quotes.issue(viewer, command.project(), command.expectedVersion(),
                new ProjectQuoteRepository.NewQuote(buyer, note, command.expiresAt(), price.mode(), price.price(), inputs))
                .orElseThrow(ProjectChangedException::new);
        return new QuoteIssued(command.project(), issued.number(), issued.projectVersion(), issued.issuedAt());
    }

    /**
     * Makes a Quoted project a Draft again, with the quote's inputs in place of the project's, and the quote as it was sent. The version
     * rises, so a costing made before is out of date, and there is none until the Draft is costed.
     */
    public NewVersion newVersionFrom(Viewer viewer, NewVersionCommand command) {
        var project = projects.find(viewer, command.project()).orElseThrow(ProjectNotFoundException::new);
        if (project.version() != command.expectedVersion()) {
            throw new ProjectChangedException();
        }
        if (project.draft()) {
            throw new ProjectNotQuotedException();
        }
        var inputs = quotes.findInputs(viewer, command.project(), command.number()).orElseThrow(QuoteNotFoundException::new);
        Set<FacilityId> quoted = new java.util.HashSet<>();
        inputs.facilities().forEach(values -> quoted.add(new FacilityId(java.util.UUID.fromString(values.id()))));
        var restored = QuoteInputs.restore(inputs, termsOf(viewer, quoted));
        project.reopen();
        project.replaceInputs(restored.products(), restored.boughtParts(), restored.items(), restored.prices(), restored.typedPrices(),
                restored.config());
        var version = projects.save(viewer, project).orElseThrow(ProjectChangedException::new);
        return new NewVersion(new ProjectChanged(command.project(), version), restored.changedFacilities(), restored.goneFacilities());
    }

    /**
     * Deletes one quote with its costing and trace. If it was the only quote of a Quoted project, the project is a Draft again. The number
     * is not used again, so a number a buyer was given never means another quote.
     */
    public QuoteDeleted deleteQuote(Viewer viewer, ProjectId project, int number) {
        return quotes.delete(viewer, project, number).map(deleted -> new QuoteDeleted(deleted.projectReopened()))
                .orElseThrow(QuoteNotFoundException::new);
    }

    /** What each facility of the viewer's that is asked for does now. A facility that is not the viewer's, or is gone, is not in it. */
    private Map<FacilityId, FacilityTerms> termsOf(Viewer viewer, Set<FacilityId> ids) {
        Map<FacilityId, FacilityTerms> terms = new HashMap<>();
        for (var id : ids) {
            facilities.termsOf(viewer, id).ifPresent(found -> terms.put(id, found));
        }
        return terms;
    }
}
