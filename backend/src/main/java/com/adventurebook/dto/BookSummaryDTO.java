package com.adventurebook.dto;

import com.adventurebook.enums.Difficulty;

public record BookSummaryDTO(Long id, String title, String author, Difficulty difficulty) {
}
