package com.example.tradingbot.persistence.specification;

import com.example.tradingbot.domain.model.search_params.OrderSearchParams;
import com.example.tradingbot.persistence.model.DealEntity;
import com.example.tradingbot.persistence.model.ExchangeEntity;
import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.persistence.model.OrderEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
public class OrderSpecification {

    public static Specification<OrderEntity> bySearchParams(OrderSearchParams params) {
        return (root, query, criteriaBuilder) -> {
            query.distinct(true);

            List<Predicate> predicates = new ArrayList<>();

            Join<OrderEntity, DealEntity> dealJoin = root.join("deal", JoinType.INNER);

            addEqualsPredicate(predicates, criteriaBuilder, root.get("internalId"), params.getInternalId());
            addEqualsPredicate(predicates, criteriaBuilder, root.get("externalId"), params.getExternalId());
            addEqualsPredicate(predicates, criteriaBuilder, root.get("status"), params.getStatus());
            addEqualsPredicate(predicates, criteriaBuilder, root.get("externalStatus"), params.getExternalStatus());
            addEqualsPredicate(predicates, criteriaBuilder, root.get("type"), params.getType());
            addEqualsPredicate(predicates, criteriaBuilder, root.get("side"), params.getSide());
            addEqualsPredicate(predicates, criteriaBuilder, dealJoin.get("internalId"), params.getInternalDealId());

            if (needInstrumentFiltering(params)) {
                predicates.add(buildInstrumentPredicate(params, query, criteriaBuilder, dealJoin));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate buildInstrumentPredicate(OrderSearchParams params,
                                                      CriteriaQuery<?> query,
                                                      CriteriaBuilder criteriaBuilder,
                                                      Join<OrderEntity, DealEntity> dealJoin) {
        Subquery<Long> instrumentSubquery = query.subquery(Long.class);
        Root<InstrumentEntity> instrumentRoot = instrumentSubquery.from(InstrumentEntity.class);

        List<Predicate> instrumentPredicates = new ArrayList<>();

        instrumentSubquery.select(instrumentRoot.get("id"));

        addEqualsPredicate(
                instrumentPredicates,
                criteriaBuilder,
                instrumentRoot.get("internalId"),
                params.getInternalInstrumentId()
        );
        addEqualsPredicate(
                instrumentPredicates,
                criteriaBuilder,
                instrumentRoot.get("externalId"),
                params.getExternalInstrumentId()
        );
        addEqualsPredicate(
                instrumentPredicates,
                criteriaBuilder,
                instrumentRoot.get("externalType"),
                params.getExternalInstrumentType()
        );

        if (hasText(params.getInternalExchangeId())) {
            Subquery<Long> exchangeSubquery = query.subquery(Long.class);
            Root<ExchangeEntity> exchangeRoot = exchangeSubquery.from(ExchangeEntity.class);

            exchangeSubquery.select(exchangeRoot.get("id"));
            exchangeSubquery.where(
                    criteriaBuilder.equal(
                            exchangeRoot.get("internalId"),
                            params.getInternalExchangeId()
                                  .trim()
                    )
            );

            instrumentPredicates.add(instrumentRoot.get("exchangeId")
                                                   .in(exchangeSubquery));
        }

        instrumentSubquery.where(instrumentPredicates.toArray(new Predicate[0]));

        return dealJoin.get("instrumentId")
                       .in(instrumentSubquery);
    }

    private static boolean needInstrumentFiltering(OrderSearchParams params) {
        return hasText(params.getInternalInstrumentId())
                || hasText(params.getExternalInstrumentId())
                || hasText(params.getExternalInstrumentType())
                || hasText(params.getInternalExchangeId());
    }

    private static void addEqualsPredicate(List<Predicate> predicates,
                                           CriteriaBuilder criteriaBuilder,
                                           Path<String> path,
                                           String value) {
        if (hasText(value)) {
            predicates.add(criteriaBuilder.equal(path, value.trim()));
        }
    }

    private static boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
