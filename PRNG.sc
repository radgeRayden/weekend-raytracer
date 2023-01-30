using import struct

inline rotl(x amount)
    imply x u64
    imply amount i32
    (x << amount) | (x >> (64 - amount))

inline seed256 (seederT seed)
    # as per the xoshiro readme recommendation
    local seeder = (seederT seed)

    local state =
        arrayof u64
            'next seeder
            'next seeder
            'next seeder
            'next seeder

vvv bind random
do
    # http://prng.di.unimi.it/
    # "Generating uniform doubles in the unit interval"
    inline normalize (value)
        imply value u64
        ((value >> 11) as f64) * 0x1.0p-53:f64

    # adapted from http://www.pcg-random.org/posts/bounded-rands.html
    # Lemire's debiased integer multiplication method with modifications
    # Original C++ version is Copyright (c) 2018 Melissa E. O'Neill, MIT License.
    # Description: returns a random integer [0,_range) or [range-start, range-end)
    # input is assumed to be > 0.
    fn bounded-random (rng _range)
        u128 := (integer 128)
        x := (rng)
        m := ((x as u128) * (_range as u128))
        l := (m as u64)

        let m =
            if (l < _range)
                threshold := -_range
                let threshold =
                    if (threshold >= _range)
                        threshold := (threshold - _range)
                        if (threshold >= _range)
                            threshold % _range
                        else threshold
                    else
                        threshold
                loop (x m l = x m l)
                    if (l < threshold)
                        break m
                    # rejected, roll again
                    let x = (rng)
                    let m = ((x as u128) * (_range as u128))
                    let l = (m as u64)
                    _ x m l
            else
                m
        # there's no need to worry about overflow here because we can't output a result
        # that is bigger than range.
        (m >> 64) as u64

    typedef RandomNumberGenerator < CStruct
        fn normalized (self)
            normalize (self)

        fn... __call
        case (self)
            'next self
        # in cases with range params, for ease of use we're gonna output the same type as inputs.
        # In the case of floats, the fractional part is discarded via casting.
        case (self, upper-bound,)
            assert (upper-bound > 0)
            let result =
                bounded-random self (upper-bound as u64)
            result as (typeof upper-bound)
        case (self, lower-bound, upper-bound,)
            inputT := (typeof lower-bound)
            # guarantee both inputs are of same type or equivalent
            imply upper-bound inputT
            assert (upper-bound > lower-bound)

            # lose fractional part if present, use signed because bounds can be negative
            lower-bound as:= i64
            upper-bound as:= i64
            # offset the range to zero - diff is always positive
            let diff = (upper-bound - lower-bound)
            let result =
                this-function self (diff as u64)
            # restore original range
            (lower-bound + (result as i64)) as inputT

        spice choose (self ...)
            let argc = ('argcount ...)
            verify-count argc 2 -1
            let sw = (sc_switch_new `(self [argc]))
            for i in (range argc)
                sc_switch_append_case sw `i (sc_getarg ... i)
            sc_switch_append_default sw
                `(unreachable)
            sw

    # http://prng.di.unimi.it/splitmix64.c
    # used here to generate xoshiro256** state from a single 64-bit seed
    struct Splitmix64 < RandomNumberGenerator
        _state : u64
        inline __typecall (cls seed)
            super-type.__typecall cls
                _state = seed

        fn next (self)
            x := self._state
            x += 0x9e3779b97f4a7c15
            let z = (deref x)
            z := ((z ^ (z >> 30)) * 0xbf58476d1ce4e5b9)
            z := ((z ^ (z >> 27)) * 0x94d049bb133111eb)
            z ^ (z >> 31)

    # http://xoshiro.di.unimi.it/xoshiro256starstar.c
    struct Xoshiro256** < RandomNumberGenerator
        _state : (array u64 4)
        inline __typecall (cls seed)
            super-type.__typecall cls
                _state = (seed256 Splitmix64 seed)

        fn next (self)
            let result = ((rotl ((self._state @ 1) * 5) 7) * 9)

            # now advance to the next state on the generator
            s := self._state
            t := ((s @ 1) << 17)
            s @ 2 ^= (s @ 0)
            s @ 3 ^= (s @ 1)
            s @ 1 ^= (s @ 2)
            s @ 0 ^= (s @ 3)

            s @ 2 ^= t

            s @ 3 = (rotl (s @ 3) 45)

            result

    # http://prng.di.unimi.it/xoshiro256plus.c
    # More well suited for FP number generation.
    struct Xoshiro256+ < RandomNumberGenerator
        _state : (array u64 4)
        inline __typecall (cls seed)
            super-type.__typecall cls
                _state = (seed256 Splitmix64 seed)

        fn next (self)
            s := self._state
            result := (s @ 0) + (s @ 3)

            # now advance to the next state on the generator
            t := ((s @ 1) << 17)
            s @ 2 ^= (s @ 0)
            s @ 3 ^= (s @ 1)
            s @ 1 ^= (s @ 2)
            s @ 0 ^= (s @ 3)

            s @ 2 ^= t

            s @ 3 = (rotl (s @ 3) 45)

            result

    # default generator is xoshiro256**
    let RNG = Xoshiro256**
    locals;

do
    let
        random
        # noise
    locals;
