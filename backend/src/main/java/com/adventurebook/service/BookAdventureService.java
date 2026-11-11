package com.adventurebook.service;

import com.adventurebook.dto.BookDetailsDTO;
import com.adventurebook.dto.BookSectionDTO;

public interface BookAdventureService {

    BookDetailsDTO getBook(long bookId);

    BookSectionDTO getSection(long bookId, long sectionId);
}
