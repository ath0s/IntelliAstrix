/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.avanza.astrix.intellij.query;

import com.intellij.concurrency.AsyncFuture;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public class MappingQuery<F, T> implements Query<T> {
    private final Query<F> myOriginal;
    private final Function<F, T> myMapper;

    public MappingQuery(@NotNull Query<F> original, @NotNull Function<F, T> mapper) {
        this.myOriginal = original;
        this.myMapper = mapper;
    }

    @Override
    public T findFirst() {
        final CommonProcessors.FindFirstProcessor<T> processor = new CommonProcessors.FindFirstProcessor<>();
        forEach(processor);
        return processor.getFoundValue();
    }

    @Override
    public boolean forEach(@NotNull final Processor<? super T> consumer) {
        myOriginal.forEach(new MyProcessor(consumer));
        return true;
    }

    @NotNull
    @Override
    public AsyncFuture<Boolean> forEachAsync(@NotNull Processor<? super T> consumer) {
        return myOriginal.forEachAsync(new MyProcessor(consumer));
    }

    @Override
    @NotNull
    public Collection<T> findAll() {
        List<T> result = new LinkedList<>();
        Processor<T> processor = Processors.cancelableCollectProcessor(result);
        forEach(processor);
        return result;
    }


    private class MyProcessor implements Processor<F> {
        private final Processor<? super T> myConsumer;

        MyProcessor(@NotNull Processor<? super T> consumer) {
            myConsumer = consumer;
        }

        @Override
        public boolean process(final F f) {
            return myConsumer.process(myMapper.apply(f));
        }
    }
}
