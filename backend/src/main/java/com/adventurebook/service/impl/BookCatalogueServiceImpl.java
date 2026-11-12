package com.adventurebook.service.impl;

import java.util.Arrays;
import java.util.List;

import com.adventurebook.dto.BookFilterDTO;
import com.adventurebook.dto.BookPageDTO;
import com.adventurebook.dto.BookSummaryDTO;
import com.adventurebook.enums.Difficulty;
import com.adventurebook.repository.BookRepository;
import com.adventurebook.service.BookCatalogueService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import static com.adventurebook.repository.specification.BookSpecifications.withFilters;

@Service
@RequiredArgsConstructor
public class BookCatalogueServiceImpl implements BookCatalogueService {

    private final BookRepository books;

    @Override
    public BookPageDTO findBooks(String search, Difficulty difficulty, Pageable pageable) {
        PageRequest page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("id"));
        Page<BookSummaryDTO> result = books.findAll(withFilters(normalize(search), difficulty), page)
                .map(book -> new BookSummaryDTO(book.getId(), book.getTitle(),
                        book.getAuthor(), book.getDifficulty()));
        return new BookPageDTO(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isLast());
    }

    @Override
    public List<BookFilterDTO> getFilters() {
        List<String> options = Arrays.stream(Difficulty.values()).map(Difficulty::name).toList();
        return List.of(new BookFilterDTO("difficulty", "Difficulty", "select", options));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
