package com.adventurebook.service;

import com.adventurebook.dto.BookImportDTO;

public interface BookImportService {

    BookImportDTO parse(byte[] content);
}
