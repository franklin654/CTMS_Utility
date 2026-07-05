package com.ctms.ctms_backend.payment.repository;

import com.ctms.ctms_backend.budget.entity.CostCategory;
import com.ctms.ctms_backend.payment.entity.Payment;
import com.ctms.ctms_backend.payment.entity.PaymentStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("""
            select p from Payment p
            where (:studyId is null or p.study.id = :studyId)
              and (:siteId is null or p.site.id = :siteId)
              and (:costCategory is null or p.costCategory = :costCategory)
              and (:status is null or p.status = :status)
            """)
    Page<Payment> search(
            @Param("studyId") Long studyId,
            @Param("siteId") Long siteId,
            @Param("costCategory") CostCategory costCategory,
            @Param("status") PaymentStatus status,
            Pageable pageable);

    /** (costCategory, sum(amount)) pairs across everything except currently-held payments --
     * used as a budget's "actual" per cost category. */
    @Query("""
            select p.costCategory, sum(p.amount)
            from Payment p
            where p.study.id = :studyId and p.status <> com.ctms.ctms_backend.payment.entity.PaymentStatus.ON_HOLD
            group by p.costCategory
            """)
    List<Object[]> sumActualByCategory(@Param("studyId") Long studyId);
}
