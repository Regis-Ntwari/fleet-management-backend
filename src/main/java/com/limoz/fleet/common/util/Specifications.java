package com.limoz.fleet.common.util;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Small helpers for building optional-filter specifications without null checks in every repository.
 */
public final class Specifications {

    private Specifications() {}

    public static <T> Specification<T> none() {
        return (root, query, cb) -> cb.conjunction();
    }

    @SafeVarargs
    public static <T> Specification<T> and(Specification<T>... specs) {
        Specification<T> result = none();
        for (Specification<T> spec : specs) {
            if (spec != null) {
                result = result.and(spec);
            }
        }
        return result;
    }

    public static <T> Specification<T> equal(String attribute, Object value) {
        if (value == null) return null;
        return (root, query, cb) -> cb.equal(path(root, attribute), value);
    }

    public static <T> Specification<T> isFalse(String attribute) {
        return (root, query, cb) -> cb.isFalse(path(root, attribute));
    }

    public static <T> Specification<T> in(String attribute, List<?> values) {
        if (values == null || values.isEmpty()) return null;
        return (root, query, cb) -> path(root, attribute).in(values);
    }

    public static <T> Specification<T> likeAny(String term, String... attributes) {
        if (term == null || term.isBlank()) return null;
        String pattern = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (String attribute : attributes) {
                predicates.add(cb.like(cb.lower(path(root, attribute).as(String.class)), pattern));
            }
            return cb.or(predicates.toArray(Predicate[]::new));
        };
    }

    public static <T> Specification<T> instantBetween(String attribute, Instant from, Instant to) {
        if (from == null && to == null) return null;
        return (root, query, cb) -> {
            Path<Instant> p = path(root, attribute);
            if (from != null && to != null) return cb.between(p, from, to);
            if (from != null) return cb.greaterThanOrEqualTo(p, from);
            return cb.lessThanOrEqualTo(p, to);
        };
    }

    public static <T> Specification<T> dateBetween(String attribute, LocalDate from, LocalDate to) {
        if (from == null && to == null) return null;
        return (root, query, cb) -> {
            Path<LocalDate> p = path(root, attribute);
            if (from != null && to != null) return cb.between(p, from, to);
            if (from != null) return cb.greaterThanOrEqualTo(p, from);
            return cb.lessThanOrEqualTo(p, to);
        };
    }

    @SuppressWarnings("unchecked")
    public static <X> Path<X> path(Path<?> root, String attribute) {
        Path<?> p = root;
        for (String part : attribute.split("\\.")) {
            p = p.get(part);
        }
        return (Path<X>) p;
    }
}
