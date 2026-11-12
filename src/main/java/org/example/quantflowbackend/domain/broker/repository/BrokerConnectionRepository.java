package org.example.quantflowbackend.domain.broker.repository;

import org.example.quantflowbackend.domain.broker.domain.BrokerConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BrokerConnectionRepository extends JpaRepository<BrokerConnection, Long> {
    Optional<BrokerConnection> findByUserMemberIdAndRequestKey(Long userId, String requestKey);
    List<BrokerConnection> findAllByUserMemberIdOrderByConnectionIdDesc(Long userId);
}
