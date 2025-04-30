package com.InsightLens.repository;

import com.InsightLens.model.DocumentComparison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentComparisonRepository extends JpaRepository<DocumentComparison, Long> {
}
