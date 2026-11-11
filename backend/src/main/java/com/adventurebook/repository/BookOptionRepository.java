package com.adventurebook.repository;

import java.util.List;

import com.adventurebook.model.BookOption;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookOptionRepository extends JpaRepository<BookOption, Long> {

    @EntityGraph(attributePaths = "destination")
    List<BookOption> findAllBySectionIdOrderByPositionAsc(Long sectionId);
}
