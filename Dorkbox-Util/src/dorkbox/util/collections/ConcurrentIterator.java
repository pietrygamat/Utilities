/*
 * Copyright 2015 dorkbox, llc
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
package dorkbox.util.collections;


import com.esotericsoftware.kryo.util.IdentityMap;
import dorkbox.util.Property;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * @author dorkbox, llc
 */
public
class ConcurrentIterator<T> {
    /**
     * Specifies the load-factor for the IdentityMap used
     */
    @Property
    public static final float LOAD_FACTOR = 0.8F;

    private static final AtomicInteger ID_COUNTER = new AtomicInteger();
    private final int ID = ID_COUNTER.getAndIncrement();

    // This is only touched by a single thread, maintains a map of entries for FAST lookup during remove.
    private final IdentityMap<Object, ConcurrentEntry> entries = new IdentityMap<Object, ConcurrentEntry>(32, LOAD_FACTOR);

    // this is still inside the single-writer, and can use the same techniques as subscription manager (for thread safe publication)
    @SuppressWarnings("FieldCanBeLocal")
    private volatile ConcurrentEntry<T> head = null; // reference to the first element

    // Recommended for best performance while adhering to the "single writer principle". Must be static-final
    public static final AtomicReferenceFieldUpdater<ConcurrentIterator, ConcurrentEntry> headREF = AtomicReferenceFieldUpdater.newUpdater(
                    ConcurrentIterator.class,
                    ConcurrentEntry.class,
                    "head");

    public
    ConcurrentIterator() {
    }

    // called on shutdown for GC purposes
    public final
    void clear() {
        this.entries.clear();
        this.head = null;
    }

    /**
     * single writer principle!
     * called from within SYNCHRONIZE
     *
     * @param listener the object that will receive messages during publication
     */
    public
    void add(final Object listener) {
        ConcurrentEntry head = headREF.get(this);

        if (!entries.containsKey(listener)) {
            head = new ConcurrentEntry<Object>(listener, head);

            entries.put(listener, head);
            headREF.lazySet(this, head);
        }
    }

    /**
     * single writer principle!
     * called from within SYNCHRONIZE
     *
     * @param listener the object that will NO LONGER receive messages during publication
     */
    public
    void remove(final Object listener) {
        ConcurrentEntry concurrentEntry = entries.get(listener);

        if (concurrentEntry != null) {
            ConcurrentEntry head1 = headREF.get(this);

            if (concurrentEntry == head1) {
                // if it was second, now it's first
                head1 = head1.next();
                //oldHead.clear(); // optimize for GC not possible because of potentially running iterators
            }
            else {
                concurrentEntry.remove();
            }

            headREF.lazySet(this, head1);
            this.entries.remove(listener);
        }
    }

    @Override
    public final
    int hashCode() {
        return this.ID;
    }

    @Override
    public final
    boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ConcurrentIterator other = (ConcurrentIterator) obj;
        return this.ID == other.ID;
    }
}
