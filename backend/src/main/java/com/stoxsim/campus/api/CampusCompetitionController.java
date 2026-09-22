package com.stoxsim.campus.api;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.stoxsim.campus.service.CampusCompetitionService;
import com.stoxsim.campus.api.CampusCompetitionDtos.*;

@RestController
@RequestMapping("/api/v1/campus")
public class CampusCompetitionController {
    private final CampusCompetitionService service;
    public CampusCompetitionController(CampusCompetitionService service) { this.service=service; }
    private UUID id(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private <T> ResponseEntity<T> ok(T value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value); }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Void> malformedRequest() { return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).build(); }
    @GetMapping("/institutions") public ResponseEntity<List<Institution>> directory(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="") String q) { return ok(service.directory(id(jwt),q)); }
    @GetMapping("/membership-request") public ResponseEntity<JoinRequest> latest(@AuthenticationPrincipal Jwt jwt) { return ok(service.latest(id(jwt))); }
    @PostMapping("/membership-requests/{request}/cancel") public ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID request) { service.cancelRequest(id(jwt),request);return ok(null); }
    @PostMapping("/institutions/{institution}/membership-requests") public ResponseEntity<JoinRequest> apply(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID institution,@Valid @RequestBody Apply request) { return ok(service.apply(id(jwt),institution,request.note())); }
    @GetMapping("/institutions/{institution}") public ResponseEntity<Workspace> workspace(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID institution) { return ok(service.workspace(id(jwt),institution)); }
    @GetMapping("/institutions/{institution}/manage") public ResponseEntity<Management> manage(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID institution) { return ok(service.management(id(jwt),institution)); }
    @PostMapping("/institutions/{institution}/membership-requests/{request}/review") public ResponseEntity<Void> review(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID institution,@PathVariable UUID request,@Valid @RequestBody Review body) { service.review(id(jwt),institution,request,body);return ok(null); }
    @PostMapping("/institutions/{institution}/members/{target}/role") public ResponseEntity<Void> role(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID institution,@PathVariable UUID target,@Valid @RequestBody MemberRole body) { service.changeRole(id(jwt),institution,target,body.role());return ok(null); }
    @PostMapping("/institutions/{institution}/members/{target}/remove") public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID institution,@PathVariable UUID target,@Valid @RequestBody Reason body) { service.removeMember(id(jwt),institution,target,body.note());return ok(null); }
    @PostMapping("/institutions/{institution}/organizer-recovery") public ResponseEntity<Void> recover(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID institution,@Valid @RequestBody Organizer body) { service.recoverOrganizer(id(jwt),institution,body);return ok(null); }
    @PostMapping("/institutions/{institution}/suspension") public ResponseEntity<Void> suspend(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID institution,@Valid @RequestBody Suspension body) { service.suspend(id(jwt),institution,body);return ok(null); }
    @PostMapping("/institutions/{institution}/competitions") public ResponseEntity<Competition> create(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID institution,@Valid @RequestBody NewCompetition body) { return ok(service.create(id(jwt),institution,body)); }
    @GetMapping("/institutions/{institution}/competitions/{competition}") public ResponseEntity<Board> board(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID institution,@PathVariable UUID competition) { return ok(service.board(id(jwt),institution,competition)); }
    @PostMapping("/institutions/{institution}/competitions/{competition}/enroll") public ResponseEntity<Board> enroll(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID institution,@PathVariable UUID competition) { return ok(service.enroll(id(jwt),institution,competition)); }
    @PostMapping("/institutions/{institution}/competitions/{competition}/withdraw") public ResponseEntity<Void> withdraw(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID institution,@PathVariable UUID competition) { service.withdraw(id(jwt),institution,competition);return ok(null); }
    @PostMapping("/institutions/{institution}/competitions/{competition}/cancel") public ResponseEntity<Void> cancelCompetition(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID institution,@PathVariable UUID competition,@Valid @RequestBody Reason body) { service.cancelCompetition(id(jwt),institution,competition,body.note());return ok(null); }
}
