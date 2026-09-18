package com.food.foodapp.menu.controller;

import com.food.foodapp.common.exception.MenuCategoryNotFoundException;
import com.food.foodapp.common.exception.MenuItemNotFoundException;
import com.food.foodapp.common.exception.OwnerAccessDeniedException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.menu.dto.OwnerMenuItemResponse;
import com.food.foodapp.menu.service.MenuItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(OwnerMenuItemController.class)
class OwnerMenuItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MenuItemService menuItemService;

    @Test
    void ownerEndpoint_returns403_whenCallerDoesNotOwnTheRestaurant() throws Exception {
        when(menuItemService.listItemsForOwner(2L)).thenThrow(new OwnerAccessDeniedException("nope"));

        mockMvc.perform(get("/api/v1/owner/restaurants/2/items"))
                .andExpect(status().isForbidden());
    }


    @Test
    void list_returnsAllItems() throws Exception {
        when(menuItemService.listItemsForOwner(1L)).thenReturn(List.of(
                OwnerMenuItemResponse.builder().id(1L).categoryId(10L).name("مارجريتا")
                        .price(BigDecimal.valueOf(50)).available(true).displayOrder(0).build()));

        mockMvc.perform(get("/api/v1/owner/restaurants/1/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("مارجريتا"))
                .andExpect(jsonPath("$[0].categoryId").value(10));
    }

    @Test
    void create_returns201_withCreatedItem() throws Exception {
        when(menuItemService.createItem(eq(1L), any())).thenReturn(
                OwnerMenuItemResponse.builder().id(1L).categoryId(10L).name("مارجريتا")
                        .price(BigDecimal.valueOf(50)).available(true).displayOrder(0).build());

        mockMvc.perform(post("/api/v1/owner/restaurants/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":10,\"name\":\"مارجريتا\",\"price\":50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("مارجريتا"));
    }

    @Test
    void create_returns400_whenPriceMissing() throws Exception {
        mockMvc.perform(post("/api/v1/owner/restaurants/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":10,\"name\":\"مارجريتا\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns400_whenPriceNegative() throws Exception {
        mockMvc.perform(post("/api/v1/owner/restaurants/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":10,\"name\":\"مارجريتا\",\"price\":-5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns404_whenCategoryBelongsToAnotherRestaurant() throws Exception {
        when(menuItemService.createItem(eq(1L), any()))
                .thenThrow(new MenuCategoryNotFoundException("Menu category 10 not found for restaurant 1"));

        mockMvc.perform(post("/api/v1/owner/restaurants/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":10,\"name\":\"مارجريتا\",\"price\":50}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_returns404_whenRestaurantMissing() throws Exception {
        when(menuItemService.createItem(eq(99L), any()))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        mockMvc.perform(post("/api/v1/owner/restaurants/99/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":10,\"name\":\"مارجريتا\",\"price\":50}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_returnsUpdatedItem() throws Exception {
        when(menuItemService.updateItem(eq(1L), eq(100L), any())).thenReturn(
                OwnerMenuItemResponse.builder().id(100L).categoryId(10L).name("مارجريتا سبيشل")
                        .price(BigDecimal.valueOf(60)).available(true).displayOrder(0).build());

        mockMvc.perform(put("/api/v1/owner/restaurants/1/items/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":10,\"name\":\"مارجريتا سبيشل\",\"price\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("مارجريتا سبيشل"));
    }

    @Test
    void update_returns404_whenItemBelongsToAnotherRestaurant() throws Exception {
        when(menuItemService.updateItem(eq(2L), eq(100L), any()))
                .thenThrow(new MenuItemNotFoundException("Menu item 100 not found for restaurant 2"));

        mockMvc.perform(put("/api/v1/owner/restaurants/2/items/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":10,\"name\":\"مارجريتا\",\"price\":50}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/owner/restaurants/1/items/100"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns404_whenItemBelongsToAnotherRestaurant() throws Exception {
        org.mockito.Mockito.doThrow(new MenuItemNotFoundException("Menu item 100 not found for restaurant 2"))
                .when(menuItemService).deleteItem(2L, 100L);

        mockMvc.perform(delete("/api/v1/owner/restaurants/2/items/100"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAvailability_returnsUpdatedItem() throws Exception {
        when(menuItemService.updateAvailability(eq(1L), eq(100L), any())).thenReturn(
                OwnerMenuItemResponse.builder().id(100L).categoryId(10L).name("مارجريتا")
                        .price(BigDecimal.valueOf(50)).available(false).displayOrder(0).build());

        mockMvc.perform(patch("/api/v1/owner/restaurants/1/items/100/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void updateAvailability_returns400_whenFlagMissing() throws Exception {
        mockMvc.perform(patch("/api/v1/owner/restaurants/1/items/100/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
