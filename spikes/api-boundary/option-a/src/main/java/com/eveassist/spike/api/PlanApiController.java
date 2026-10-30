package com.eveassist.spike.api;

import com.eveassist.spike.identity.Viewer;
import com.eveassist.spike.industry.IndustryService;
import com.eveassist.spike.industry.PlanCosting;
import com.eveassist.spike.industry.PlanDetails;
import com.eveassist.spike.industry.PlanId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequestMapping("/api/v1/plans")
class PlanApiController {
    private final IndustryService industry;

    PlanApiController(IndustryService industry) {
        this.industry = industry;
    }

    @GetMapping
    List<PlanSummaryResponse> list(Viewer viewer) {
        return industry.listPlans(viewer).stream().map(PlanSummaryResponse::from).toList();
    }

    @GetMapping("/{id}")
    ResponseEntity<PlanResponse> get(Viewer viewer, @PathVariable UUID id, WebRequest request) {
        PlanDetails plan = industry.getPlan(viewer, new PlanId(id));
        String etag = "\"" + plan.version() + "\"";
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(PlanResponse.from(plan));
    }

    /** A resource of its own, because its ETag also depends on prices, which change without the plan changing. */
    @GetMapping("/{id}/costing")
    ResponseEntity<PlanCostingResponse> costing(Viewer viewer, @PathVariable UUID id, WebRequest request) {
        PlanCosting costing = industry.getCosting(viewer, new PlanId(id));
        String etag = "\"" + costing.planVersion() + "-" + costing.priceSnapshot() + "\"";
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(PlanCostingResponse.from(costing));
    }
}
