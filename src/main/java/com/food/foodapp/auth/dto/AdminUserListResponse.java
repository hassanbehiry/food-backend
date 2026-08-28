package com.food.foodapp.auth.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Pagination envelope for the admin users list — mirrors {@code AdminRestaurantListResponse}'s shape. */
@Getter
@Builder
public class AdminUserListResponse {

    private List<AdminUserResponse> users;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
