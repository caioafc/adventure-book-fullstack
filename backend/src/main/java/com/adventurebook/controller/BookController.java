package com.adventurebook.controller;

import com.adventurebook.dto.BookPageDTO;
import com.adventurebook.enums.Difficulty;
import com.adventurebook.service.BookCatalogueService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookCatalogueService catalogue;

    @GetMapping
    public BookPageDTO getBooks(@RequestParam(required = false) String search,
                                @RequestParam(required = false) String author,
                                @RequestParam(required = false) Difficulty difficulty,
                                @PageableDefault(size = 10) Pageable pageable) {
        return catalogue.findBooks(search, author, difficulty, pageable);
    }
}
