package com.example.travelwiki.auth.repository;

import com.example.travelwiki.auth.entity.AuthProvider;
import com.example.travelwiki.auth.entity.UserAuthIdentity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAuthIdentityRepository extends JpaRepository<UserAuthIdentity, Long> {

    Optional<UserAuthIdentity> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);
}
