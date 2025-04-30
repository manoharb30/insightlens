package com.InsightLens.repository;
import com.InsightLens.model.DocumentSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentSectionRepository extends JpaRepository<DocumentSection, Long> {
}
