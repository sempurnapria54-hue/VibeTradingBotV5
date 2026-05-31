package com.example.tradingbot.persistence.specification;

import com.example.tradingbot.domain.model.search_params.AlgoOrderSearchParams;
import com.example.tradingbot.persistence.model.deal.algo_order.AlgoOrderEntity;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
public class AlgoOrderSpecification {

    public static Specification<AlgoOrderEntity> bySearchParams(AlgoOrderSearchParams params) {
        return (root, query, criteriaBuilder) -> {
            query.distinct(true);

            List<Predicate> predicates = new ArrayList<>();

            Join<Object, Object> dealJoin = root.join("deal", JoinType.INNER);

            addEqualsPredicate(predicates, criteriaBuilder, dealJoin.get("internalId"), params.getDealInternalId());
            addEqualsPredicate(predicates, criteriaBuilder, root.get("internalId"), params.getInternalId());
            addEqualsPredicate(predicates, criteriaBuilder, root.get("externalId"), params.getExternalId());
            addEqualsPredicate(predicates, criteriaBuilder, root.get("externalType"), params.getExternalType());
            addEqualsPredicate(predicates, criteriaBuilder, root.get("status"), params.getStatus());
            addEqualsPredicate(predicates, criteriaBuilder, root.get("externalStatus"), params.getExternalStatus());

            boolean needInstrumentJoin = hasText(params.getInstrumentInternalId())
                    || hasText(params.getInstrumentExternalId())
                    || hasText(params.getInstrumentExternalType())
                    || hasText(params.getExchangeInternalId());

            if (needInstrumentJoin) {
                Join<Object, Object> instrumentJoin = dealJoin.join("instrument", JoinType.INNER);

                addEqualsPredicate(predicates, criteriaBuilder, instrumentJoin.get("internalId"),
                                   params.getInstrumentInternalId());
                addEqualsPredicate(predicates, criteriaBuilder, instrumentJoin.get("externalId"),
                                   params.getInstrumentExternalId());
                addEqualsPredicate(predicates, criteriaBuilder, instrumentJoin.get("externalType"),
                                   params.getInstrumentExternalType());

                if (hasText(params.getExchangeInternalId())) {
                    Join<Object, Object> exchangeJoin = instrumentJoin.join("exchange", JoinType.INNER);
                    addEqualsPredicate(predicates, criteriaBuilder, exchangeJoin.get("internalId"),
                                       params.getExchangeInternalId());
                }
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

    private static boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
