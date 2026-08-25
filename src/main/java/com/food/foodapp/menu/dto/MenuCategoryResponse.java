package com.food.foodapp.menu.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MenuCategoryResponse {

    private Long id;
    private String name;
    private int displayOrder;
    private boolean active;
}
