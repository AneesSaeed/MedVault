package be.he2b.healthsec.medical_records.repository;

import be.he2b.healthsec.medical_records.model.User;
import be.he2b.healthsec.medical_records.model.UserType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface UserRepository extends JpaRepository<User, UUID> {
    
    boolean existsByKeycloakId(String keycloakId);
    
    Optional<User> findByKeycloakId(String keycloakId);
    
    List<User> findByRole(UserType role);
    
    Optional<User> findByEmailEnc(byte[] emailEnc); // si tu veux chercher par email chiffré (à voir comment faire côté client)

}
