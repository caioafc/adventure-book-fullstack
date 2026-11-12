package com.adventurebook.dto;

import java.util.List;

public record BookFilterDTO(String key, String label, String type, List<String> options) {
}
