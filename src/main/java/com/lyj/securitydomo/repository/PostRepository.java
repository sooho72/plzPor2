
package com.lyj.securitydomo.repository;


import com.lyj.securitydomo.domain.Post;
import com.lyj.securitydomo.repository.search.PostSearch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long>, PostSearch {


//    @Query("SELECT p FROM Post p WHERE " //동적쿼리
//            + "(:keyword IS NULL OR p.title LIKE %:keyword% OR p.contentText LIKE %:keyword%) "
//            + "AND (:types IS NULL OR p.title IN (:types))")


//    Page<Post> searchAll(@Param("types") String[] types,
//                         @Param("keyword") String keyword,
//                         Pageable pageable);


    @EntityGraph(attributePaths = {"imageSet"})
    @Query("select p from Post p where p.postId=:postId")
    Optional<Post> findByIdWithImages(Long postId);
    // isVisible == true인 게시글만 조회
    Page<Post> findByIsVisibleTrue(Pageable pageable);
}
