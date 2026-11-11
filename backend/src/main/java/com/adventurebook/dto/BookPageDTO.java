package com.adventurebook.dto;

import java.util.List;

public record BookPageDTO(List<BookSummaryDTO> content, int page, int size,
                          long totalElements, int totalPages, boolean last) {
}
