package com.adventurebook.repository.specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.adventurebook.enums.Difficulty;
import com.adventurebook.model.Book;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

public final class BookSpecifications {

    private BookSpecifications() {
    }

    public static Specification<Book> withFilters(String search, Difficulty difficulty) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null) {
                String text = search.toLowerCase(Locale.ROOT);
                predicates.add(builder.or(
                        builder.greaterThan(builder.locate(builder.lower(root.get("title")), text), 0),
                        builder.greaterThan(builder.locate(builder.lower(root.get("author")), text), 0)));
            }
            if (difficulty != null) {
                predicates.add(builder.equal(root.get("difficulty"), difficulty));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
