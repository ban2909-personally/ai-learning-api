package com.ailearning.platform.commerce.adapter.out.persistence;

import com.ailearning.platform.commerce.adapter.out.persistence.jpa.repository.CourseOrderJpaRepository;
import com.ailearning.platform.commerce.adapter.out.persistence.mapper.CourseOrderPersistenceMapper;
import com.ailearning.platform.commerce.application.port.out.CourseOrderStore;
import com.ailearning.platform.commerce.domain.model.CourseOrder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class CourseOrderPersistenceAdapter implements CourseOrderStore {
    private final CourseOrderJpaRepository repository;

    public CourseOrderPersistenceAdapter(CourseOrderJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CourseOrder> findByIdempotencyKey(UUID userId, UUID idempotencyKey) {
        return repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .map(CourseOrderPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public CourseOrder insertOrGet(CourseOrder order) {
        repository.insertIfAbsent(
                order.id(),
                order.userId(),
                order.courseId(),
                order.courseSlug(),
                order.courseTitle(),
                order.total().amount(),
                order.total().currency(),
                order.idempotencyKey(),
                order.createdAt(),
                order.expiresAt()
        );
        return repository.findByUserIdAndIdempotencyKey(order.userId(), order.idempotencyKey())
                .map(CourseOrderPersistenceMapper::toDomain)
                .orElseThrow(() -> new IllegalStateException("Course order insert completed without a readable row"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseOrder> findRecentByUser(UUID userId, int limit) {
        return repository.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, limit))
                .stream()
                .map(CourseOrderPersistenceMapper::toDomain)
                .toList();
    }
}
