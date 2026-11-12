package com.adventurebook.service;

import java.util.List;

import com.adventurebook.dto.BookFilterDTO;
import com.adventurebook.dto.BookPageDTO;
import com.adventurebook.enums.Difficulty;
import org.springframework.data.domain.Pageable;

public interface BookCatalogueService {

    BookPageDTO findBooks(String search, Difficulty difficulty, Pageable pageable);

    List<BookFilterDTO> getFilters();
}
