package com.aws.imageapp.repository;

import com.aws.imageapp.entity.ImageMetaData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ImageMetaDataRepository extends JpaRepository<ImageMetaData, Long> {
    @Query("SELECT i FROM ImageMetaData i " +
            "WHERE LOWER(i.fileName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "   OR LOWER(i.description) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<ImageMetaData> searchByFilenameOrDescription(@Param("search") String search, Pageable pageable);
    
    @Query("SELECT COUNT(i) FROM ImageMetaData i " +
            "WHERE LOWER(i.fileName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "   OR LOWER(i.description) LIKE LOWER(CONCAT('%', :search, '%'))")
    long countByFilenameOrDescription(@Param("search") String search);
    Page<ImageMetaData> findAllByOrderByUploadTimestampDesc(Pageable pageable);
    
    Optional<ImageMetaData> findByKey (String key);
}
