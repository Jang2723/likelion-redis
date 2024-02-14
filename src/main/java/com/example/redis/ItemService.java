package com.example.redis;

import com.example.redis.dto.ItemDto;
import com.example.redis.entity.Item;
import com.example.redis.repo.ItemRepository;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemService {
    private final SlowDataQuery repository;
    private final ItemRepository itemRepository;

    @Resource(name = "cacheRedisTemplate")
    private ValueOperations<Long, ItemDto> cacheOps;

    public ItemDto create(ItemDto dto) {
    Item item = itemRepository.save(Item.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .price(dto.getPrice())
                .stock(dto.getStock())
                .build());
        ItemDto newDto  = ItemDto.fromEntity(item);
        // 결과를 반환하기 전 캐시에 한번 저장한다. (write through)
        cacheOps.set(newDto.getId(), newDto, Duration.ofSeconds(60));
        return newDto;
    }

    @Cacheable(cacheNames = "itemAllCache", key = "#root.methodName")
    public List<ItemDto> readAll() {
        return repository.findAll()
                .stream()
                .map(ItemDto::fromEntity)
                .toList();
    }

    // cacheName : 캐시 규칙을 지정하기 위한 이름
    // key : 캐시를 저장할 떄 개발 데이터를 구분하기 위한 값
    @Cacheable(cacheNames = "itemCache", key = "#root.args[0]")
    public ItemDto readOne (Long id) {
        return repository.findById(id)
                .map(ItemDto::fromEntity)
                .orElseThrow(()->
                        new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

        public ItemDto readOneManual (Long id) {
        // Cache Aside를 RedisTemplate을 활용해 직접 구현해 보자.
        // 1. cacheOps에서 ItemDto를 찾아본다.
        // GET id
        ItemDto found = cacheOps.get(id);
        // 2. null일 경우 데이터베이스에서 조회한다.
        if (found == null) {
            // 2-1. 없으면 404, 캐시에 없으면 repository에 가서 찾아온다
            found = repository.findById(id)
                    .map(ItemDto::fromEntity)
                    .orElseThrow(()->
                            new ResponseStatusException(HttpStatus.NOT_FOUND));
            // 2-2. 있으면 캐시에 저장
            // 3번째 인자로 만료 시간 설정 가능
            cacheOps.set(id, found, Duration.ofSeconds(10));
        }
        // 3. 최종적으로 데이터를 반환한다.
        return found;
//        return repository.findById(id)
//                .map(ItemDto::fromEntity)
//                .orElseThrow(()->
//                        new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
