package com.avanza.astrix.intellij.query

import com.intellij.util.FilteredQuery
import com.intellij.util.InstanceofQuery
import com.intellij.util.Query

internal inline fun <reified T> Query<*>.instanceOf(): Query<T> =
    InstanceofQuery(this, T::class.java)

internal fun <T> Query<T>.filter(condition: (T) -> Boolean): Query<T> =
    FilteredQuery(this, condition)

internal fun <F, T> Query<F>.map(mapper: (F) -> T): Query<T> =
    MappingQuery(this, mapper)