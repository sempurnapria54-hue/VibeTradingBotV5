package com.example.tradingbot.persistence.specification;

import com.example.tradingbot.domain.model.search_params.InstrumentSearchParams;
import com.example.tradingbot.persistence.model.exchange.ExchangeEntity;
import com.example.tradingbot.persistence.model.instrument.InstrumentEntity;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
public class InstrumentSpecification {

    public static Specification<InstrumentEntity> bySearchParams(InstrumentSearchParams params) {
        return (root, query, criteriaBuilder) -> {
            query.distinct(true);

            List<Predicate> predicates = new ArrayList<>();

            addEqualsPredicate(predicates, criteriaBuilder, root.get("id"), params.getId());
            addEqualsPredicate(predicates, criteriaBuilder, root.get("internalId"), params.getInternalId());
            addEqualsPredicate(predicates, criteriaBuilder, root.get("exchangeId"), params.getExchangeId());
            addEqualsPredicate(predicates, criteriaBuilder, root.get("externalId"), params.getExternalId());
            addEqualsPredicate(predicates, criteriaBuilder, root.get("externalType"), params.getExternalType());
            addEqualsPredicate(predicates, criteriaBuilder, root.get("status"), params.getStatus());

            if (hasText(params.getExchangeInternalId())) {
                Subquery<Long> exchangeSubquery = query.subquery(Long.class);

                var exchangeRoot = exchangeSubquery.from(ExchangeEntity.class);

                exchangeSubquery.select(exchangeRoot.get("id"));
                exchangeSubquery.where(
                        criteriaBuilder.equal(
                                exchangeRoot.get("internalId"),
                                params.getExchangeInternalId()
                                      .trim()
                        )
                );

                predicates.add(root.get("exchangeId")
                                   .in(exchangeSubquery));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void addEqualsPredicate(List<Predicate> predicates,
                                           jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
                                           jakarta.persistence.criteria.Path<String> path,
                                           String value) {
        if (hasText(value)) {
            predicates.add(criteriaBuilder.equal(path, value.trim()));
        }
    }

    private static void addEqualsPredicate(List<Predicate> predicates,
                                           jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
                                           jakarta.persistence.criteria.Path<Long> path,
                                           Long value) {
        if (value != null) {
            predicates.add(criteriaBuilder.equal(path, value));
        }
    }

    private static boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
