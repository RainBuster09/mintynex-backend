package com.mintynex.binder.repository;

import com.mintynex.binder.model.BinderCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BinderCardRepository extends JpaRepository<BinderCard, Long> {
    Page<BinderCard> findByUserIdOrderByAddedAtDesc(Long userId, Pageable pageable);
    long countByUserId(Long userId);
    long countByUserIdAndGradeCompany(Long userId, String gradeCompany);
}
