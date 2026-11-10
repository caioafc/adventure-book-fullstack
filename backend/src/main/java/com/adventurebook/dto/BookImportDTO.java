package com.adventurebook.dto;

import java.util.List;

import com.adventurebook.enums.ConsequenceType;
import com.adventurebook.enums.Difficulty;
import com.adventurebook.enums.SectionType;
import com.adventurebook.serialization.ConsequenceValueDeserializer;
import com.adventurebook.serialization.SectionIdDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BookImportDTO(
        @NotBlank @Size(max = 512) String title,
        @NotBlank @Size(max = 255) String author,
        @NotNull Difficulty difficulty,
        @NotEmpty List<@NotNull @Valid Section> sections) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Section(
            @JsonDeserialize(using = SectionIdDeserializer.class)
            @NotBlank @Size(max = 255) String id,
            @NotBlank String text,
            @NotNull SectionType type,
            List<@NotNull @Valid Option> options) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(
            @NotBlank @Size(max = 255) String description,
            @JsonDeserialize(using = SectionIdDeserializer.class)
            @NotBlank @Size(max = 255) String gotoId,
            @Valid Consequence consequence) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Consequence(
            @NotNull ConsequenceType type,
            @JsonDeserialize(using = ConsequenceValueDeserializer.class)
            @NotNull @Positive Integer value,
            @NotBlank @Size(max = 255) String text) {
    }
}
