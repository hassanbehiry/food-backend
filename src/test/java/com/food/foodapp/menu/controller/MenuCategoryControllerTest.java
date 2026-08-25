package com.food.foodapp.menu.controller;

import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.menu.dto.MenuCategoryResponse;
import com.food.foodapp.menu.service.MenuCategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MenuCategoryController.class)
class MenuCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MenuCategoryService menuCategoryService;

    @Test
    void list_returnsActiveCategoriesInOrder() throws Exception {
        when(menuCategoryService.listVisibleCategories(1L)).thenReturn(List.of(
                MenuCategoryResponse.builder().id(10L).name("بيتزا").displayOrder(0).active(true).build(),
                MenuCategoryResponse.builder().id(11L).name("مشروبات").displayOrder(1).active(true).build()));

        mockMvc.perform(get("/api/v1/restaurants/1/menu/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("بيتزا"))
                .andExpect(jsonPath("$[1].name").value("مشروبات"));
    }

    @Test
    void list_returns404_whenRestaurantNotVisible() throws Exception {
        when(menuCategoryService.listVisibleCategories(99L))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        mockMvc.perform(get("/api/v1/restaurants/99/menu/categories"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void list_returns400_whenRestaurantIdIsNotNumeric() throws Exception {
        mockMvc.perform(get("/api/v1/restaurants/abc/menu/categories"))
                .andExpect(status().isBadRequest());
    }
}
