using import glm
using import struct
using import Rc
using import enum
using import Option
import .raydEngine.use
import PRNG

struct Ray plain
    origin    : vec3
    direction : vec3

    fn at (self t)
        self.origin + (self.direction * t)

enum Material
struct HitRecord
    p      : vec3
    normal : vec3
    t      : f32
    front? : bool
    mat    : (Rc Material)

    inline __typecall (cls ray t outward-normal material)
        front? := (dot ray.direction outward-normal) < 0
        super-type.__typecall cls
            p = ('at ray t)
            front? = front?
            normal = (? front? outward-normal -outward-normal)
            mat = material
            t = t

HitRecordOpt := (Option HitRecord)


global rng : PRNG.random.Xoshiro256+ 0

inline length2 (v)
    dot v v

inline reflect (v n)
    len := (dot v n)
    v - (2 * len * n)


fn random-in-unit-sphere ()
    loop ()
        let p =
            vec3
                (('normalized rng) * 2) - 1.0
                (('normalized rng) * 2) - 1.0
                (('normalized rng) * 2) - 1.0
        if ((length2 p) < 1)
            break p

fn random-unit-vector ()
    a := ('normalized rng) * 2 * pi
    z := (('normalized rng) * 2) - 1
    r := (sqrt (1 - (* z z)))
    vec3 (r * (cos a)) (r * (sin a)) z

locals;
