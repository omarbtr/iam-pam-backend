package com.iam.pam.controller;

import com.iam.pam.dto.ApiResponse;
import com.iam.pam.dto.FaceDescriptorDTO;
import com.iam.pam.service.FaceDescriptorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Face Recognition", description = "Enregistrement et vérification par reconnaissance faciale")
@RestController
@RequestMapping("/api/user/face")
@RequiredArgsConstructor
public class FaceDescriptorController {

    private final FaceDescriptorService faceService;

    @Operation(summary = "Enregistrer le visage de l'utilisateur connecté")
    @PostMapping("/enroll")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> enroll(
            @RequestBody FaceDescriptorDTO.EnrollRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        faceService.enrollFace(username, request.getDescriptor());
        return ResponseEntity.ok(ApiResponse.success(null, "Visage enregistré avec succès"));
    }

    @Operation(summary = "Vérifier le visage de l'utilisateur connecté")
    @PostMapping("/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FaceDescriptorDTO.VerifyResponse>> verify(
            @RequestBody FaceDescriptorDTO.VerifyRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        FaceDescriptorDTO.VerifyResponse result = faceService.verifyFace(username, request.getDescriptor());
        return ResponseEntity.ok(ApiResponse.success(result, "Résultat vérification faciale"));
    }

    @Operation(summary = "Statut de l'enregistrement facial de l'utilisateur connecté")
    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FaceDescriptorDTO.StatusResponse>> status(
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.ok(ApiResponse.success(faceService.getStatus(username), "Statut facial"));
    }

    @Operation(summary = "Supprimer l'enregistrement facial de l'utilisateur connecté")
    @DeleteMapping("/enroll")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> removeEnrollment(
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        faceService.removeEnrollment(username);
        return ResponseEntity.ok(ApiResponse.success(null, "Enregistrement facial supprimé"));
    }
}
