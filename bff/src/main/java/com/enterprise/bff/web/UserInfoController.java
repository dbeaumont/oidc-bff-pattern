package com.enterprise.bff.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Expose les informations de l'utilisateur connecté sans jamais retourner de token.
 * Angular utilise cet endpoint pour connaître l'identité et les rôles de l'utilisateur.
 */
@RestController
@RequestMapping("/user-info")
public class UserInfoController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> userInfo(@AuthenticationPrincipal OidcUser user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        // Les rôles Keycloak sont dans realm_access.roles, pas dans un claim top-level "roles"
        Map<String, Object> realmAccess = user.getClaim("realm_access");
        @SuppressWarnings("unchecked")
        List<String> roles = (realmAccess != null)
            ? (List<String>) realmAccess.get("roles")
            : List.of();

        Map<String, Object> info = Map.of(
            "sub",       user.getSubject(),
            "email",     user.getEmail() != null ? user.getEmail() : "",
            "firstName", user.getGivenName() != null ? user.getGivenName() : "",
            "lastName",  user.getFamilyName() != null ? user.getFamilyName() : "",
            "username",  user.getPreferredUsername() != null ? user.getPreferredUsername() : "",
            "roles",     roles != null ? roles : List.of()
        );

        return ResponseEntity.ok(info);
    }
}
