package org.example.quantflowbackend.domain.broker.repository;

import org.example.quantflowbackend.domain.broker.domain.BrokerCredential;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrokerCredentialRepository extends JpaRepository<BrokerCredential, String> { }
