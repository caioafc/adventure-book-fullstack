package com.adventurebook.dto;

import com.adventurebook.enums.Difficulty;

public record BookDetailsDTO(Long id, String title, String author, Difficulty difficulty,
                             Long beginningSectionId) {
}
