package com.enterprise.api.dto;

import com.enterprise.api.entity.Item;

public record ItemResponse(Long id, String name, String description) {

    public static ItemResponse from(Item item) {
        return new ItemResponse(item.getId(), item.getName(), item.getDescription());
    }
}
