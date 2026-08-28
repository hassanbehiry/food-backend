package com.food.foodapp.menu.controller;

import com.food.foodapp.common.exception.DuplicateMenuCategoryException;
import com.food.foodapp.common.exception.MenuCategoryNotFoundException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.menu.dto.MenuCategoryResponse;
import com.food.foodapp.menu.service.MenuCategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OwnerMenuCategoryController.class)
class OwnerMenuCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MenuCategoryService menuCategoryService;

    @Test
    void create_returns201_withCreatedCategory() throws Exception {
        when(menuCategoryService.createCategory(eq(1L), any())).thenReturn(
                MenuCategoryResponse.builder().id(10L).name("باستا").displayOrder(0).active(true).build());

        mockMvc.perform(post("/api/v1/owner/restaurants/1/menu/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"باستا\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("باستا"));
    }

    @Test
    void create_returns400_whenNameBlank() throws Exception {
        mockMvc.perform(post("/api/v1/owner/restaurants/1/menu/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns404_whenRestaurantMissing() throws Exception {
        when(menuCategoryService.createCategory(eq(99L), any()))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        mockMvc.perform(post("/api/v1/owner/restaurants/99/menu/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"باستا\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_returns409_whenNameDuplicate() throws Exception {
        when(menuCategoryService.createCategory(eq(1L), any()))
                .thenThrow(new DuplicateMenuCategoryException("Menu category 'بيتزا' already exists for restaurant 1"));

        mockMvc.perform(post("/api/v1/owner/restaurants/1/menu/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"بيتزا\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void update_returnsUpdatedCategory() throws Exception {
        when(menuCategoryService.updateCategory(eq(1L), eq(10L), any())).thenReturn(
                MenuCategoryResponse.builder().id(10L).name("بيتزا مميزة").displayOrder(0).active(false).build());

        mockMvc.perform(put("/api/v1/owner/restaurants/1/menu/categories/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"بيتزا مميزة\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void update_returns404_whenCategoryBelongsToAnotherRestaurant() throws Exception {
        when(menuCategoryService.updateCategory(eq(2L), eq(10L), any()))
                .thenThrow(new MenuCategoryNotFoundException("Menu category 10 not found for restaurant 2"));

        mockMvc.perform(put("/api/v1/owner/restaurants/2/menu/categories/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"بيتزا\",\"active\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/owner/restaurants/1/menu/categories/10"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns404_whenCategoryBelongsToAnotherRestaurant() throws Exception {
        org.mockito.Mockito.doThrow(new MenuCategoryNotFoundException("Menu category 10 not found for restaurant 2"))
                .when(menuCategoryService).deleteCategory(2L, 10L);

        mockMvc.perform(delete("/api/v1/owner/restaurants/2/menu/categories/10"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reorder_returnsReorderedList() throws Exception {
        when(menuCategoryService.reorderCategories(eq(1L), any())).thenReturn(List.of(
                MenuCategoryResponse.builder().id(11L).name("مشروبات").displayOrder(0).active(true).build(),
                MenuCategoryResponse.builder().id(10L).name("بيتزا").displayOrder(1).active(true).build()));

        mockMvc.perform(put("/api/v1/owner/restaurants/1/menu/categories/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryIds\":[11,10]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(11))
                .andExpect(jsonPath("$[1].id").value(10));
    }
}
