package com.enterprise.api.controller;

import com.enterprise.api.entity.Item;
import com.enterprise.api.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ItemControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ItemRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void findAll_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/items")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void findAll_returnsItems() throws Exception {
        repository.save(new Item("Test Item", "Description"));

        mockMvc.perform(get("/items")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Test Item"))
            .andExpect(jsonPath("$[0].id").exists());
    }

    @Test
    void create_returnsCreatedWithLocation() throws Exception {
        mockMvc.perform(post("/items")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "New Item", "description": "Desc"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("New Item"));
    }

    @Test
    void create_forbiddenForUser() throws Exception {
        mockMvc.perform(post("/items")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "New Item"}
                        """))
            .andExpect(status().isForbidden());
    }

    @Test
    void create_rejectsMissingName() throws Exception {
        mockMvc.perform(post("/items")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"description": "sans nom"}
                        """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void update_modifiesExistingItem() throws Exception {
        Item saved = repository.save(new Item("Original", "Desc"));

        mockMvc.perform(put("/items/" + saved.getId())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Updated", "description": "New Desc"}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    void update_returns404ForUnknownId() throws Exception {
        mockMvc.perform(put("/items/999999")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Updated"}
                        """))
            .andExpect(status().isNotFound());
    }

    @Test
    void delete_removesItem() throws Exception {
        Item saved = repository.save(new Item("To Delete", null));

        mockMvc.perform(delete("/items/" + saved.getId())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isNoContent());

        assertFalse(repository.existsById(saved.getId()));
    }

    @Test
    void delete_returns404ForUnknownId() throws Exception {
        mockMvc.perform(delete("/items/999999")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isNotFound());
    }
}
