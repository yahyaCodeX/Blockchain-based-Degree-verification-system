package com.decentralized.degree.vault.decentralizeddegreevault.repository;

import com.decentralized.degree.vault.decentralizeddegreevault.dto.DegreeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DegreeRecordRepository extends JpaRepository<DegreeRecord, String> {
}

