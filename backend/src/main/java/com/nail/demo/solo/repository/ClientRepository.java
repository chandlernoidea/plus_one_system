package com.nail.demo.solo.repository;

import java.util.List;
import java.util.Optional;

import com.nail.demo.solo.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByCode(String code);

    boolean existsByCode(String code);

    /** 取最新一条，用于生成下一个短号（#C012）。 */
    Optional<Client> findTopByOrderByIdDesc();

    /** 客户列表默认按最近到访倒序。 */
    List<Client> findByDeletedOrderByLastVisitDateDescIdDesc(Integer deleted);

    /**
     * 三合一搜索：短号 / 昵称 / 手机后 4 位都能命中。keyword 可为空，为空时返回全部未删除客户。
     */
    @Query("""
            select c from Client c
            where c.deleted = 0
              and (:keyword is null
                   or c.code like concat('%', :keyword, '%')
                   or c.nickname like concat('%', :keyword, '%')
                   or c.phoneTail = :keyword)
            order by c.lastVisitDate desc, c.id desc
            """)
    List<Client> search(@Param("keyword") String keyword);
}
