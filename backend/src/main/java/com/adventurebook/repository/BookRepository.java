package com.adventurebook.repository;

import com.adventurebook.model.Book;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, Long> {

    Optional<Book> findByContentHash(String contentHash);

    boolean existsByContentHash(String contentHash);
}
