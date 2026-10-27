package com.decentralized.degree.vault.decentralizeddegreevault.repository;

import com.decentralized.degree.vault.decentralizeddegreevault.dto.DegreeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DegreeRecordRepository extends JpaRepository<DegreeRecord, String> {

    List<DegreeRecord> findAllByOrderByIssueDateDesc();

    @Query("SELECT d FROM DegreeRecord d WHERE " +
           "LOWER(d.globalCredentialId) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(d.studentName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(d.rollNo) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(d.department) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(d.universityCode) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "ORDER BY d.issueDate DESC")
    List<DegreeRecord> searchDegrees(@Param("q") String query);

    long countByIsRevokedFalse();

    long countByIsRevokedTrue();

    java.util.Optional<DegreeRecord> findByRollNoIgnoreCase(String rollNo);

    @Query("SELECT d FROM DegreeRecord d WHERE " +
           "LOWER(d.globalCredentialId) = LOWER(:id) OR " +
           "LOWER(d.rollNo) = LOWER(:id) OR " +
           "LOWER(d.studentEmail) = LOWER(:id)")
    java.util.Optional<DegreeRecord> findByIdentifier(@Param("id") String id);
}
