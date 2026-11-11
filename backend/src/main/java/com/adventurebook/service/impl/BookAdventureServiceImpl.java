package com.adventurebook.service.impl;

import java.util.List;

import com.adventurebook.dto.BookDetailsDTO;
import com.adventurebook.dto.BookSectionDTO;
import com.adventurebook.enums.SectionType;
import com.adventurebook.exception.AdventureContentNotFoundException;
import com.adventurebook.model.Book;
import com.adventurebook.model.BookOption;
import com.adventurebook.model.BookSection;
import com.adventurebook.repository.BookOptionRepository;
import com.adventurebook.repository.BookRepository;
import com.adventurebook.repository.BookSectionRepository;
import com.adventurebook.repository.BookSectionRepository.SectionIdView;
import com.adventurebook.service.BookAdventureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookAdventureServiceImpl implements BookAdventureService {

    private final BookRepository books;
    private final BookSectionRepository sections;
    private final BookOptionRepository options;

    @Override
    public BookDetailsDTO getBook(long bookId) {
        Book book = books.findById(bookId)
                .orElseThrow(() -> new AdventureContentNotFoundException("Book not found"));
        Long beginning = sections.findByBookIdAndType(bookId, SectionType.BEGIN)
                .map(SectionIdView::getId)
                .orElseThrow(() -> new AdventureContentNotFoundException("Beginning section not found"));
        return new BookDetailsDTO(book.getId(), book.getTitle(), book.getAuthor(), book.getDifficulty(), beginning);
    }

    @Override
    public BookSectionDTO getSection(long bookId, long sectionId) {
        BookSection section = sections.findByBookIdAndId(bookId, sectionId)
                .orElseThrow(() -> new AdventureContentNotFoundException("Section not found in this book"));
        List<BookSectionDTO.Option> choices = options.findAllBySectionIdOrderByPositionAsc(section.getId())
                .stream().map(this::toOption).toList();
        return new BookSectionDTO(section.getId(), section.getText(), section.getType(), choices);
    }

    private BookSectionDTO.Option toOption(BookOption option) {
        BookSectionDTO.Consequence consequence = option.getConsequenceType() == null ? null
                : new BookSectionDTO.Consequence(option.getConsequenceType(), option.getConsequenceValue(),
                        option.getConsequenceText());
        return new BookSectionDTO.Option(option.getDescription(), option.getDestination().getId(), consequence);
    }
}
