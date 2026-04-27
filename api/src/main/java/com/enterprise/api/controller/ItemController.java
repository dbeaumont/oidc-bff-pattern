package com.enterprise.api.controller;

import com.enterprise.api.dto.ItemRequest;
import com.enterprise.api.dto.ItemResponse;
import com.enterprise.api.entity.Item;
import com.enterprise.api.repository.ItemRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/items")
public class ItemController {

    private final ItemRepository itemRepository;

    public ItemController(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public List<ItemResponse> findAll() {
        return itemRepository.findAll().stream().map(ItemResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ItemResponse> findById(@PathVariable Long id) {
        return itemRepository.findById(id)
            .map(ItemResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ItemResponse> create(@Valid @RequestBody ItemRequest request) {
        Item saved = itemRepository.save(new Item(request.name(), request.description()));
        return ResponseEntity.created(URI.create("/items/" + saved.getId()))
            .body(ItemResponse.from(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ItemResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody ItemRequest request) {
        return itemRepository.findById(id)
            .map(existing -> {
                existing.setName(request.name());
                existing.setDescription(request.description());
                return ResponseEntity.ok(ItemResponse.from(itemRepository.save(existing)));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!itemRepository.existsById(id)) return ResponseEntity.notFound().build();
        itemRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
