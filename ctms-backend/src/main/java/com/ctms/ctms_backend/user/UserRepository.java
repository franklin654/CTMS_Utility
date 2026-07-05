package com.ctms.ctms_backend.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findByRoles_Code(String roleCode);

    @Query("""
            select u from User u join u.roles r
            where r.code = :roleCode
              and (:search = ''
                   or lower(u.username) like lower(concat('%', :search, '%'))
                   or lower(u.fullName) like lower(concat('%', :search, '%')))
            order by u.username
            """)
    List<User> searchByRole(@Param("roleCode") String roleCode, @Param("search") String search);
}
