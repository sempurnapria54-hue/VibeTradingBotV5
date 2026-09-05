package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.InstrumentEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Запросы по строке проекции инструмента. */
public interface InstrumentRepository extends JpaRepository<InstrumentEntity, Long> {

    Optional<InstrumentEntity> findByInternalId(String internalId);
}
