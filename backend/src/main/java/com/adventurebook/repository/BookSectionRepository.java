package com.adventurebook.repository;

import java.util.List;
import java.util.Optional;

import com.adventurebook.model.BookSection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookSectionRepository extends JpaRepository<BookSection, Long> {

    List<BookSection> findAllByBookId(Long bookId);

    Optional<BookSection> findByBookIdAndSectionKey(Long bookId, String sectionKey);
}
