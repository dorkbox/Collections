/*
 * Copyright 2018 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dorkbox.collections;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * This class uses the "single-writer-principle" for lock-free publication.
 *
 * Since there are only 2 methods to guarantee that modifications can only be called one-at-a-time (either it is only called by
 * one thread, or only one thread can access it at a time) -- we chose the 2nd option -- and use 'synchronized' to make sure that only
 * one thread can access this modification methods at a time. Getting or checking the presence of values can then happen in a lock-free
 * manner.
 *
 * According to my benchmarks, this is approximately 25% faster than ConcurrentHashMap for (all types of) reads, and a lot slower for
 * contended writes.
 *
 * This data structure is for many-read/few-write scenarios
 */
public final
class LockFreeArrayList<E> implements List<E>, RandomAccess, Cloneable, Serializable {
    public static final String version = Collections.version;

    // Recommended for best performance while adhering to the "single writer principle". Must be static-final
    private static final AtomicReferenceFieldUpdater<LockFreeArrayList, ArrayList> listRef =
            AtomicReferenceFieldUpdater.newUpdater(LockFreeArrayList.class,
                                                   ArrayList.class, "arrayList");
    private volatile ArrayList<E> arrayList = new ArrayList<>();


    public
    LockFreeArrayList(){}


    public
    LockFreeArrayList(Collection<E> elements) {
        arrayList.addAll(elements);
    }

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    public synchronized
    void clear() {
        arrayList.clear();
    }

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    public synchronized
    boolean add(final E element) {
        return arrayList.add(element);
    }

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    public synchronized
    boolean addAll(final Collection<? extends E> elements) {
        return arrayList.addAll(elements);
    }

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    @Override
    public synchronized
    boolean addAll(final int i, final Collection<? extends E> collection) {
        return arrayList.addAll(i, collection);
    }

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    @Override
    public
    boolean removeAll(final Collection<?> collection) {
        return arrayList.removeAll(collection);
    }

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    @Override
    public synchronized
    boolean retainAll(final Collection<?> collection) {
        return retainAll(collection);
    }


    @SuppressWarnings("unchecked")
    public
    E get(int index) {
        return (E) listRef.get(this).get(index);
    }

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    @Override
    public synchronized
    E set(final int index, final E element) {
        return arrayList.set(index, element);
    }

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    @Override
    public synchronized
    void add(final int index, final E element) {
        arrayList.add(index, element);
    }

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    @Override
    public synchronized
    E remove(final int index) {
        return arrayList.remove(index);
    }

    // lock-free get
    @Override
    public
    int indexOf(final Object object) {
        return listRef.get(this).indexOf(object);
    }

    // lock-free get
    @Override
    public
    int lastIndexOf(final Object object) {
        return listRef.get(this).lastIndexOf(object);
    }


    // lock-free get
    @SuppressWarnings("unchecked")
    @Override
    public
    ListIterator<E> listIterator() {
        return listRef.get(this).listIterator();
    }

    // lock-free get
    @SuppressWarnings("unchecked")
    @Override
    public
    ListIterator<E> listIterator(final int index) {
        return listRef.get(this).listIterator(index);
    }

    // lock-free get
    @SuppressWarnings("unchecked")
    @Override
    public
    List<E> subList(final int startIndex, final int endIndex) {
        return listRef.get(this).subList(startIndex, endIndex);
    }

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    public synchronized
    boolean remove(final Object element) {
        return arrayList.remove(element);
    }

    // lock-free get
    @SuppressWarnings("unchecked")
    @Override
    public
    boolean containsAll(final Collection<?> collection) {
        return listRef.get(this).containsAll(collection);
    }

    // lock-free get
    public
    int size() {
        return listRef.get(this).size();
    }

    // lock-free get
    @Override
    public
    boolean isEmpty() {
        return listRef.get(this).isEmpty();
    }

    // lock-free get
    public
    boolean contains(final Object element) {
        // use the SWP to get the value
        return listRef.get(this).contains(element);
    }

    // lock-free get
    @SuppressWarnings("unchecked")
    @Override
    public
    Iterator<E> iterator() {
        return listRef.get(this).iterator();
    }

    // lock-free get
    @Override
    public
    Object[] toArray() {
        return listRef.get(this).toArray();
    }

    // lock-free get
    @SuppressWarnings("unchecked")
    @Override
    public
    <T> T[] toArray(final T[] targetArray) {
        return (T[]) listRef.get(this).toArray(targetArray);
    }

    // lock-free get
    @SuppressWarnings("unchecked")
    public
    ArrayList<E> elements() {
        return listRef.get(this);
    }
}
