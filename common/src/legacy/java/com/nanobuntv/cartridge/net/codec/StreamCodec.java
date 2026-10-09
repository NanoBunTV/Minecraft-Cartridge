package com.nanobuntv.cartridge.net.codec;

import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import com.mojang.datafixers.util.Function6;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Minecraft's {@code StreamCodec} (1.20.5), for the versions before it (Porting.md, "Networking before 1.20.5"): the
 * part of its API the mod uses, so the payloads' code is the same on every version. The build swaps the imports.
 */
public interface StreamCodec<B, V> {
    V decode(B buf);

    void encode(B buf, V value);

    interface Encoder<B, V> {
        void encode(B buf, V value);
    }

    interface Decoder<B, V> {
        V decode(B buf);
    }

    /** An encoder that is the value's own method: {@code value.write(buf)}. */
    interface MemberEncoder<B, V> {
        void encode(V value, B buf);
    }

    interface CodecOperation<B, S, T> {
        StreamCodec<B, T> apply(StreamCodec<B, S> codec);
    }

    static <B, V> StreamCodec<B, V> of(Encoder<B, V> encoder, Decoder<B, V> decoder) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buf) {
                return decoder.decode(buf);
            }

            @Override
            public void encode(B buf, V value) {
                encoder.encode(buf, value);
            }
        };
    }

    static <B, V> StreamCodec<B, V> ofMember(MemberEncoder<B, V> encoder, Decoder<B, V> decoder) {
        return of((buf, value) -> encoder.encode(value, buf), decoder);
    }

    static <B, V> StreamCodec<B, V> unit(V instance) {
        return of((buf, value) -> {}, buf -> instance);
    }

    default <O> StreamCodec<B, O> map(Function<? super V, ? extends O> to, Function<? super O, ? extends V> from) {
        StreamCodec<B, V> self = this;
        return of((buf, value) -> self.encode(buf, from.apply(value)), buf -> to.apply(self.decode(buf)));
    }

    default <O> StreamCodec<B, O> apply(CodecOperation<B, V, O> operation) {
        return operation.apply(this);
    }

    @SuppressWarnings("unchecked")
    default <S extends B> StreamCodec<S, V> cast() {
        return (StreamCodec<S, V>) this;
    }

    static <B, C, T1> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            Function<T1, C> factory) {
        return of((buf, v) -> c1.encode(buf, g1.apply(v)), buf -> factory.apply(c1.decode(buf)));
    }

    static <B, C, T1, T2> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2, BiFunction<T1, T2, C> factory) {
        return of((buf, v) -> {
            c1.encode(buf, g1.apply(v));
            c2.encode(buf, g2.apply(v));
        }, buf -> factory.apply(c1.decode(buf), c2.decode(buf)));
    }

    static <B, C, T1, T2, T3> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2, StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            Function3<T1, T2, T3, C> factory) {
        return of((buf, v) -> {
            c1.encode(buf, g1.apply(v));
            c2.encode(buf, g2.apply(v));
            c3.encode(buf, g3.apply(v));
        }, buf -> {
            T1 t1 = c1.decode(buf);
            T2 t2 = c2.decode(buf);
            return factory.apply(t1, t2, c3.decode(buf));
        });
    }

    static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2, StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4, Function4<T1, T2, T3, T4, C> factory) {
        return of((buf, v) -> {
            c1.encode(buf, g1.apply(v));
            c2.encode(buf, g2.apply(v));
            c3.encode(buf, g3.apply(v));
            c4.encode(buf, g4.apply(v));
        }, buf -> {
            T1 t1 = c1.decode(buf);
            T2 t2 = c2.decode(buf);
            T3 t3 = c3.decode(buf);
            return factory.apply(t1, t2, t3, c4.decode(buf));
        });
    }

    static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2, StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4, StreamCodec<? super B, T5> c5, Function<C, T5> g5,
            Function5<T1, T2, T3, T4, T5, C> factory) {
        return of((buf, v) -> {
            c1.encode(buf, g1.apply(v));
            c2.encode(buf, g2.apply(v));
            c3.encode(buf, g3.apply(v));
            c4.encode(buf, g4.apply(v));
            c5.encode(buf, g5.apply(v));
        }, buf -> {
            T1 t1 = c1.decode(buf);
            T2 t2 = c2.decode(buf);
            T3 t3 = c3.decode(buf);
            T4 t4 = c4.decode(buf);
            return factory.apply(t1, t2, t3, t4, c5.decode(buf));
        });
    }

    static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1,
            Function<C, T1> g1, StreamCodec<? super B, T2> c2, Function<C, T2> g2, StreamCodec<? super B, T3> c3,
            Function<C, T3> g3, StreamCodec<? super B, T4> c4, Function<C, T4> g4, StreamCodec<? super B, T5> c5,
            Function<C, T5> g5, StreamCodec<? super B, T6> c6, Function<C, T6> g6,
            Function6<T1, T2, T3, T4, T5, T6, C> factory) {
        return of((buf, v) -> {
            c1.encode(buf, g1.apply(v));
            c2.encode(buf, g2.apply(v));
            c3.encode(buf, g3.apply(v));
            c4.encode(buf, g4.apply(v));
            c5.encode(buf, g5.apply(v));
            c6.encode(buf, g6.apply(v));
        }, buf -> {
            T1 t1 = c1.decode(buf);
            T2 t2 = c2.decode(buf);
            T3 t3 = c3.decode(buf);
            T4 t4 = c4.decode(buf);
            T5 t5 = c5.decode(buf);
            return factory.apply(t1, t2, t3, t4, t5, c6.decode(buf));
        });
    }
}
