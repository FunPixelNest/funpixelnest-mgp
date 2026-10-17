using System;
using System.Collections.Generic;

namespace FunPixelNest
{
    /// <summary>
    /// Deterministic PRNG: xoshiro256** seeded through SplitMix64 from a 64-bit seed.
    /// Pure integer math, so the same seed yields the same sequence on every platform, runtime
    /// (Mono / IL2CPP / WebAssembly) and Unity version. Use it for EVERYTHING that affects the
    /// board/deck. Never use System.Random, UnityEngine.Random or time for that.
    ///
    /// Exact algorithm (so a server or other client can reproduce it):
    ///  - Seeding: x = (ulong)seed; s0..s3 = four successive SplitMix64(x) outputs.
    ///  - NextULong: standard xoshiro256** (result = rotl(s1*5, 7)*9).
    ///  - Next(n), n &gt;= 2: rejection sampling. threshold = 2^64 mod n; draw r until r &gt;= threshold; return r mod n.
    ///    n of 0 or 1 returns 0 and consumes nothing.
    ///  - Next(min, max): min + Next over the range (max - min) using the same rejection rule on 64-bit values.
    ///  - NextFloat: (NextULong() &gt;&gt; 40) * 2^-24.   NextDouble: (NextULong() &gt;&gt; 11) * 2^-53.
    ///  - Shuffle: Fisher-Yates from the end: for i = n-1 down to 1: j = Next(i + 1); swap(i, j).
    ///
    /// Not thread-safe; one instance per consumer.
    /// </summary>
    public sealed class FpnRandom
    {
        private ulong _s0, _s1, _s2, _s3;

        /// <summary>The seed this generator was created with.</summary>
        public long Seed { get; }

        public FpnRandom(long seed)
        {
            Seed = seed;
            ulong x = unchecked((ulong)seed);
            _s0 = SplitMix64(ref x);
            _s1 = SplitMix64(ref x);
            _s2 = SplitMix64(ref x);
            _s3 = SplitMix64(ref x);
            // SplitMix64 outputs are never all zero for consecutive states, so xoshiro's state is valid.
        }

        /// <summary>Generator seeded from the current match: <c>FpnPlatform.LaunchData.Seed</c>.</summary>
        public static FpnRandom FromLaunch()
        {
            return new FpnRandom(FpnPlatform.LaunchData.Seed);
        }

        private static ulong SplitMix64(ref ulong x)
        {
            unchecked
            {
                x += 0x9E3779B97F4A7C15UL;
                ulong z = x;
                z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9UL;
                z = (z ^ (z >> 27)) * 0x94D049BB133111EBUL;
                return z ^ (z >> 31);
            }
        }

        private static ulong RotL(ulong x, int k)
        {
            return (x << k) | (x >> (64 - k));
        }

        /// <summary>Next raw 64-bit value (xoshiro256**).</summary>
        public ulong NextULong()
        {
            unchecked
            {
                ulong result = RotL(_s1 * 5UL, 7) * 9UL;
                ulong t = _s1 << 17;
                _s2 ^= _s0;
                _s3 ^= _s1;
                _s1 ^= _s2;
                _s0 ^= _s3;
                _s2 ^= t;
                _s3 = RotL(_s3, 45);
                return result;
            }
        }

        /// <summary>Unbiased value in [0, bound). bound must be &gt;= 2.</summary>
        private ulong NextBounded(ulong bound)
        {
            unchecked
            {
                // 2^64 mod bound, computed without overflow.
                ulong threshold = (0UL - bound) % bound;
                while (true)
                {
                    ulong r = NextULong();
                    if (r >= threshold) return r % bound;
                }
            }
        }

        /// <summary>Uniform int in [0, maxExclusive). Returns 0 for maxExclusive of 0 or 1.</summary>
        /// <exception cref="ArgumentOutOfRangeException">maxExclusive is negative.</exception>
        public int Next(int maxExclusive)
        {
            if (maxExclusive < 0) throw new ArgumentOutOfRangeException("maxExclusive", "must be >= 0");
            if (maxExclusive <= 1) return 0;
            return (int)NextBounded((ulong)maxExclusive);
        }

        /// <summary>Uniform int in [minInclusive, maxExclusive). Returns minInclusive when they are equal.</summary>
        /// <exception cref="ArgumentOutOfRangeException">minInclusive &gt; maxExclusive.</exception>
        public int Next(int minInclusive, int maxExclusive)
        {
            if (minInclusive > maxExclusive) throw new ArgumentOutOfRangeException("minInclusive", "must be <= maxExclusive");
            ulong range = (ulong)((long)maxExclusive - minInclusive);
            if (range <= 1) return minInclusive;
            return (int)(minInclusive + (long)NextBounded(range));
        }

        /// <summary>Uniform float in [0, 1) with 24 bits of precision.</summary>
        public float NextFloat()
        {
            return (NextULong() >> 40) * (1.0f / 16777216.0f);
        }

        /// <summary>Uniform double in [0, 1) with 53 bits of precision.</summary>
        public double NextDouble()
        {
            return (NextULong() >> 11) * (1.0 / 9007199254740992.0);
        }

        /// <summary>Fair coin flip (top bit of the next value).</summary>
        public bool NextBool()
        {
            return (NextULong() >> 63) != 0;
        }

        /// <summary>In-place Fisher-Yates shuffle (see class docs for the exact order of draws).</summary>
        /// <exception cref="ArgumentNullException">list is null.</exception>
        public void Shuffle<T>(IList<T> list)
        {
            if (list == null) throw new ArgumentNullException("list");
            for (int i = list.Count - 1; i > 0; i--)
            {
                int j = Next(i + 1);
                T tmp = list[i];
                list[i] = list[j];
                list[j] = tmp;
            }
        }
    }
}
