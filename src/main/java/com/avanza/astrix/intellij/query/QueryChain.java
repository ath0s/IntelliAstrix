package com.avanza.astrix.intellij.query;

import com.intellij.openapi.util.Condition;
import com.intellij.util.FilteredQuery;
import com.intellij.util.InstanceofQuery;
import com.intellij.util.Query;

import java.util.function.Function;

public class QueryChain<Result> {
    private Query<?> query;

    public QueryChain(Query<Result> query) {
        this.query = query;
    }

    @SuppressWarnings("unchecked")
    public <T> QueryChain<T> instanceOf(Class<T> type) {
        query = new InstanceofQuery<>(query, type);
        return (QueryChain<T>) this;
    }

    @SuppressWarnings("unchecked")
    public QueryChain<Result> filter(Condition<Result> condition) {
        query = new FilteredQuery<>((Query<Result>) query, condition);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> QueryChain<T> map(Function<Result, T> mapper) {
        query = new MappingQuery<>((Query<Result>) query, mapper::apply);
        return (QueryChain<T>) this;
    }

    @SuppressWarnings("unchecked")
    public Query<Result> query() {
        return (Query<Result>) query;
    }
}
