package be.he2b.healthsec.medical_records.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Map;

public final class JwtRoles {
    private JwtRoles() {
    }

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

    // Used mainly for /user/me display and onboarding transitions
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
