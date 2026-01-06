package be.he2b.healthsec.medical_records.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Map;


/**
 * Small helper to read Keycloak realm roles from a Spring Security {@link Jwt}.
 *
 * <p>Expected token structure:
 * <ul>
 *   <li>realm_access.roles: list of role names (e.g., "DOCTOR", "PATIENT")</li>
 *   <li>selected_role: optional onboarding claim used before realm roles are assigned</li>
 * </ul>
 * </p>
 */
public final class JwtRoles {
    private JwtRoles() {
    }

    /**
     * Returns true if the JWT contains the given realm role in realm_access.roles.
     */
    public static boolean hasRealmRole(Jwt jwt, String role) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return false;
        }

        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof Collection<?> roles)) {
            return false;
        }

        return roles.stream().anyMatch(r -> role.equals(r));
    }

    /**
     * Returns the application's effective role for the current user.
     *
     * <p>Priority:
     * <ol>
     *   <li>Assigned realm roles (DOCTOR/PATIENT)</li>
     *   <li>Fallback to selected_role during onboarding (DOCTOR/PATIENT)</li>
     * </ol>
     * </p>
     */
    public static String effectiveRole(Jwt jwt) {
        if (hasRealmRole(jwt, "DOCTOR")) {
            return "DOCTOR";
        }
        if (hasRealmRole(jwt, "PATIENT")) {
            return "PATIENT";
        }
        String selected = jwt.getClaimAsString("selected_role");
        return ("DOCTOR".equals(selected) || "PATIENT".equals(selected)) ? selected : null;
    }
}
