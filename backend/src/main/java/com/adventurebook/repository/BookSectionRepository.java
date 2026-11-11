package com.adventurebook.repository;

import com.adventurebook.model.BookSection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookSectionRepository extends JpaRepository<BookSection, Long> {
}
