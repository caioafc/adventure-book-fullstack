package com.adventurebook.dto;

import java.util.List;

import com.adventurebook.enums.ConsequenceType;
import com.adventurebook.enums.SectionType;

public record BookSectionDTO(Long id, String text, SectionType type, List<Option> options) {

    public record Option(String description, Long gotoId, Consequence consequence) {
    }

    public record Consequence(ConsequenceType type, Integer value, String text) {
    }
}
