package com.adventurebook.controller;

import java.util.List;

import com.adventurebook.dto.BookDetailsDTO;
import com.adventurebook.dto.BookFilterDTO;
import com.adventurebook.dto.BookPageDTO;
import com.adventurebook.dto.BookSectionDTO;
import com.adventurebook.enums.ApiErrorCode;
import com.adventurebook.enums.Difficulty;
import com.adventurebook.exception.ApiException;
import com.adventurebook.service.BookAdventureService;
import com.adventurebook.service.BookCatalogueService;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookCatalogueService catalogue;
    private final BookAdventureService adventure;

    @GetMapping
    public BookPageDTO getBooks(@RequestParam(required = false) String search,
                                @RequestParam(required = false) String author,
                                @RequestParam(required = false) Difficulty difficulty,
                                @PageableDefault(size = 10) Pageable pageable) {
        return catalogue.findBooks(search, author, difficulty, pageable);
    }

    @GetMapping("/filters")
    public List<BookFilterDTO> getFilters() {
        return catalogue.getFilters();
    }

    @GetMapping("/{bookId}")
    public BookDetailsDTO getBook(@PathVariable @Positive long bookId) {
        return adventure.getBook(bookId);
    }

    @GetMapping("/{bookId}/sections/{sectionId}")
    public BookSectionDTO getSection(@PathVariable @Positive long bookId,
                                     @PathVariable @Pattern(regexp = "0*[1-9][0-9]*") String sectionId) {
        long id;
        try {
            id = Long.parseLong(sectionId);
        } catch (NumberFormatException exception) {
            throw new ApiException(ApiErrorCode.INVALID_SECTION_ID, HttpStatus.BAD_REQUEST);
        }
        return adventure.getSection(bookId, id);
    }
}
