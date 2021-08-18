package com.avanza.astrix.intellij.query

import com.intellij.util.CommonProcessors.FindFirstProcessor
import com.intellij.util.Processor
import com.intellij.util.Processors
import com.intellij.util.Query

class MappingQuery<F, T>(
    private val original: Query<F>,
    private val mapper: (F) -> T
) : Query<T> {

    override fun findFirst() =
        FindFirstProcessor<T>().apply(::forEach).foundValue

    override fun forEach(consumer: Processor<in T>) =
        original.forEach(Processor { consumer.process(mapper(it)) })

    override fun forEachAsync(consumer: Processor<in T>) =
        original.forEachAsync { consumer.process(mapper(it)) }

    override fun findAll() =
        mutableListOf<T>().also { Processors.cancelableCollectProcessor(it).apply(::forEach) }

}