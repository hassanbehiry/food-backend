package com.food.foodapp.category.controller;

import com.food.foodapp.category.dto.CategoryResponse;
import com.food.foodapp.category.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void list_returnsCategoriesFromService() throws Exception {
        CategoryResponse pizza = CategoryResponse.builder()
                .id(1L).name("بيتزا").slug("pizza").icon("fa-pizza-slice").build();
        when(categoryService.listCategories()).thenReturn(List.of(pizza));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("بيتزا"))
                .andExpect(jsonPath("$[0].slug").value("pizza"))
                .andExpect(jsonPath("$[0].icon").value("fa-pizza-slice"));
    }
}
