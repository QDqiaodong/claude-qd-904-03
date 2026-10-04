package com.tea.factory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tea.factory.domain.WitheringTrough;

import jakarta.persistence.LockModeType;

public interface WitheringTroughRepository extends JpaRepository<WitheringTrough, Long> {

    /** 取槽号在槽记录的行锁：两人同时开槽 / 登记 / 下槽时结论一致 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from WitheringTrough t where t.troughNo = :troughNo and t.releasedAt is null")
    Optional<WitheringTrough> lockActiveByTroughNo(@Param("troughNo") String troughNo);

    List<WitheringTrough> findByReleasedAtIsNullOrderByTroughNoAsc();

    List<WitheringTrough> findByStatusAndReleasedAtIsNullOrderByTroughNoAsc(String status);

    /** 历史完成记录：最近下槽的在前 */
    List<WitheringTrough> findByReleasedAtIsNotNullOrderByReleasedAtDesc();

    /** 某槽号已下槽的批次履历（用于按槽号追溯），最近一轮在前 */
    List<WitheringTrough> findByTroughNoAndReleasedAtIsNotNullOrderByOpenedAtDesc(String troughNo);

    long countByReleasedAtIsNull();

    /**
     * 手写 JPQL：摊青达标率统计（按茶类汇总槽数、已达标槽数、平均失水率）。
     */
    @Query("select t.teaType, count(t), sum(case when t.currentLossPct >= t.targetLossPct then 1 else 0 end), "
            + "avg(t.currentLossPct) "
            + "from WitheringTrough t "
            + "group by t.teaType")
    List<Object[]> summarizeReadyRate();
}
