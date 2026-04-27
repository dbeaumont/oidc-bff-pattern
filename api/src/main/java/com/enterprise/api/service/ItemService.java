package com.enterprise.api.service;

import com.enterprise.api.dto.ItemRequest;
import com.enterprise.api.dto.ItemResponse;
import com.enterprise.api.entity.Item;
import com.enterprise.api.repository.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemService.class);

    private final ItemRepository itemRepository;

    public ItemService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    public List<ItemResponse> findAll() {
        log.info("[FIND_ALL] entity=Item début");
        List<ItemResponse> items = itemRepository.findAll().stream().map(ItemResponse::from).toList();
        log.info("[FIND_ALL] entity=Item résultat count={}", items.size());
        return items;
    }

    public ItemResponse findById(Long id) {
        log.info("[FIND_BY_ID] entity=Item début id={}", id);
        return itemRepository.findById(id)
            .map(item -> {
                log.info("[FIND_BY_ID] entity=Item résultat id={}", id);
                return ItemResponse.from(item);
            })
            .orElseThrow(() -> {
                log.warn("[FIND_BY_ID] entity=Item NOT_FOUND id={}", id);
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
            });
    }

    public ItemResponse create(ItemRequest request) {
        log.info("[CREATE] entity=Item début name={}", request.name());
        Item saved = itemRepository.save(new Item(request.name(), request.description()));
        log.info("[CREATE] entity=Item résultat id={}", saved.getId());
        return ItemResponse.from(saved);
    }

    public ItemResponse update(Long id, ItemRequest request) {
        log.info("[UPDATE] entity=Item début id={}", id);
        Item existing = itemRepository.findById(id).orElseThrow(() -> {
            log.warn("[UPDATE] entity=Item NOT_FOUND id={}", id);
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
        });
        existing.setName(request.name());
        existing.setDescription(request.description());
        ItemResponse response = ItemResponse.from(itemRepository.save(existing));
        log.info("[UPDATE] entity=Item résultat id={}", id);
        return response;
    }

    public void delete(Long id) {
        log.info("[DELETE] entity=Item début id={}", id);
        if (!itemRepository.existsById(id)) {
            log.warn("[DELETE] entity=Item NOT_FOUND id={}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
        }
        itemRepository.deleteById(id);
        log.info("[DELETE] entity=Item résultat id={}", id);
    }
}
