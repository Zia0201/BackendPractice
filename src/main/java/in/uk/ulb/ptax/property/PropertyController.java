package in.uk.ulb.ptax.property;

import in.uk.ulb.ptax.auth.AuthUser;
import in.uk.ulb.ptax.property.PropertyService.CertificateRef;
import in.uk.ulb.ptax.property.PropertyService.PropertyDetail;
import in.uk.ulb.ptax.property.PropertyService.PropertyRequest;
import in.uk.ulb.ptax.property.PropertyService.PropertyView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;

    @GetMapping
    public List<PropertyView> search(AuthUser user,
                                     @RequestParam(required = false) Long ulbId,
                                     @RequestParam(required = false) Long wardId,
                                     @RequestParam(required = false) String q) {
        return propertyService.search(user, ulbId, wardId, q);
    }

    @GetMapping("/{id}")
    public PropertyDetail get(AuthUser user, @PathVariable Long id) {
        return propertyService.detail(user, id);
    }

    @PostMapping
    public PropertyView create(AuthUser user, @Valid @RequestBody PropertyRequest req) {
        return propertyService.create(user, req);
    }

    @PutMapping("/{id}")
    public PropertyView update(AuthUser user, @PathVariable Long id, @Valid @RequestBody PropertyRequest req) {
        return propertyService.update(user, id, req);
    }

    @PostMapping("/{id}/no-dues-certificate")
    public CertificateRef noDues(AuthUser user, @PathVariable Long id) {
        return propertyService.issueNoDues(user, id);
    }
}
