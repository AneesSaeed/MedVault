package be.he2b.healthsec.medical_records.service.keycloak;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Minimal Keycloak Admin API client used to assign realm roles during onboarding.
 *
 * <p>Uses client_credentials to obtain an admin token, then:
 * <ol>
 *   <li>Fetches the realm role representation</li>
 *   <li>Posts it to the user's realm role-mappings endpoint</li>
 * </ol>
 * </p>
 */
@Service
public class KeycloakAdminService {

    private final RestTemplate http = new RestTemplate();

    @Value("${keycloak.admin.base-url}")
    private String baseUrl;

    @Value("${keycloak.admin.realm}")
    private String realm;

    @Value("${keycloak.admin.client-id}")
    private String clientId;

    @Value("${keycloak.admin.client-secret}")
    private String clientSecret;

    /**
     * Assigns a Keycloak realm role to a user (limited to PATIENT/DOCTOR in this project).
     *
     * @param userId   Keycloak user id (subject)
     * @param roleName Realm role name ("PATIENT" or "DOCTOR")
     */
    public void assignRealmRole(String userId, String roleName) {
        if (!"PATIENT".equals(roleName) && !"DOCTOR".equals(roleName)) {
            throw new IllegalArgumentException("Invalid role: " + roleName);
        }

        String adminToken = getAdminToken();

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(adminToken);

        // 1) Get realm role representation
        String roleUrl = baseUrl + "/admin/realms/" + realm + "/roles/" + roleName;

        ResponseEntity<KcRoleRep> roleResp = http.exchange(
                roleUrl,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                KcRoleRep.class
        );

        KcRoleRep roleRep = roleResp.getBody();
        if (roleRep == null || roleRep.id() == null) {
            throw new IllegalArgumentException("Realm role not found in Keycloak: " + roleName);
        }

        // 2) Assign role to user
        String mapUrl = baseUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm";

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setBearerAuth(adminToken);
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

        http.exchange(
                mapUrl,
                HttpMethod.POST,
                new HttpEntity<>(List.of(roleRep), jsonHeaders),
                Void.class
        );
    }
    
    /**
     * Retrieves an admin access token using client_credentials.
     */
    private String getAdminToken() {
        String tokenUrl = baseUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        ResponseEntity<KcTokenResponse> resp = http.exchange(
                tokenUrl,
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                KcTokenResponse.class
        );

        KcTokenResponse token = resp.getBody();
        if (token == null || token.access_token() == null) {
            throw new IllegalStateException("Failed to get Keycloak admin token");
        }
        return token.access_token();
    }

    public record KcTokenResponse(String access_token) {
    }
    public record KcRoleRep(String id, String name) {
    }
}
