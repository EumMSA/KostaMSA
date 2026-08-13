package com.oopsw.authservice.repository;

import com.oopsw.authservice.repository.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Integer> {
    Account findByUsername(String username);
    boolean existsByUsername(String username);
}
