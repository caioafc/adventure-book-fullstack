package com.adventurebook.repository;

import java.util.Optional;

import com.adventurebook.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    Optional<Book> findByContentHash(String contentHash);

    boolean existsByContentHash(String contentHash);
}
