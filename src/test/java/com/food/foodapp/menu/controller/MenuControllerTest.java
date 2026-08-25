package com.food.foodapp.menu.controller;

import com.food.foodapp.common.exception.MenuCategoryNotFoundException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.menu.dto.MenuItemResponse;
import com.food.foodapp.menu.dto.RestaurantMenuResponse;
import com.food.foodapp.menu.service.MenuItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MenuController.class)
class MenuControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MenuItemService menuItemService;

    @Test
    void getMenu_returnsTabsAndItems() throws Exception {
        RestaurantMenuResponse menu = RestaurantMenuResponse.builder()
                .tabs(List.of("بيتزا", "مشروبات"))
                .items(List.of(
                        MenuItemResponse.builder().id(1L).name("مارجريتا").price(BigDecimal.valueOf(50))
                                .available(true).tab("بيتزا").build()))
                .build();
        when(menuItemService.getMenu(1L)).thenReturn(menu);

        mockMvc.perform(get("/api/v1/restaurants/1/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tabs[0]").value("بيتزا"))
                .andExpect(jsonPath("$.items[0].name").value("مارجريتا"))
                .andExpect(jsonPath("$.items[0].tab").value("بيتزا"));
    }

    @Test
    void getMenu_returns404_whenRestaurantNotVisible() throws Exception {
        when(menuItemService.getMenu(99L)).thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        mockMvc.perform(get("/api/v1/restaurants/99/menu"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listItems_returnsAllVisibleItems_whenNoCategoryGiven() throws Exception {
        when(menuItemService.listVisibleItems(eq(1L), isNull())).thenReturn(List.of(
                MenuItemResponse.builder().id(1L).name("مارجريتا").price(BigDecimal.valueOf(50))
                        .available(true).tab("بيتزا").build()));

        mockMvc.perform(get("/api/v1/restaurants/1/menu/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("مارجريتا"));
    }

    @Test
    void listItems_filtersByCategoryId() throws Exception {
        when(menuItemService.listVisibleItems(eq(1L), eq(10L))).thenReturn(List.of(
                MenuItemResponse.builder().id(1L).name("مارجريتا").price(BigDecimal.valueOf(50))
                        .available(true).tab("بيتزا").build()));

        mockMvc.perform(get("/api/v1/restaurants/1/menu/items").param("categoryId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tab").value("بيتزا"));
    }

    @Test
    void listItems_returns404_whenCategoryNotVisible() throws Exception {
        when(menuItemService.listVisibleItems(eq(1L), eq(99L)))
                .thenThrow(new MenuCategoryNotFoundException("Menu category 99 not found for restaurant 1"));

        mockMvc.perform(get("/api/v1/restaurants/1/menu/items").param("categoryId", "99"))
                .andExpect(status().isNotFound());
    }
}
