package com.adventurebook.service;

import com.adventurebook.dto.BookPageDTO;
import com.adventurebook.enums.Difficulty;
import org.springframework.data.domain.Pageable;

public interface BookCatalogueService {

    BookPageDTO findBooks(String search, String author, Difficulty difficulty, Pageable pageable);
}
