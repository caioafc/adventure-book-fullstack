package com.adventurebook.service;

import com.adventurebook.dto.BookImportDTO;
import com.adventurebook.dto.BookImportResultDTO;

public interface BookImportService {

    BookImportDTO parse(byte[] content);

    /** Imports a book atomically; database failures propagate to the caller. */
    BookImportResultDTO importBook(String sourceFilename, byte[] content);
}
