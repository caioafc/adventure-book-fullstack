package com.adventurebook.repository;

import java.util.Optional;

import com.adventurebook.enums.SectionType;
import com.adventurebook.model.BookSection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookSectionRepository extends JpaRepository<BookSection, Long> {

    Optional<BookSection> findByBookIdAndId(long bookId, long sectionId);

    Optional<SectionIdView> findByBookIdAndType(long bookId, SectionType type);

    interface SectionIdView {
        Long getId();
    }
}
