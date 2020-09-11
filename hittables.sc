using import radlib.core-extensions

using import .materials
using import .utils

using import struct
using import enum
using import glm
using import Rc
using import Array

struct SphereH
    center : vec3
    radius : f32
    mat    : (Rc Material)

    fn hit? (self ray tmin tmax)
        let center radius = self.center self.radius
        oc := ray.origin - center
        a  := (length2 ray.direction)
        hb := (dot oc ray.direction)
        c  := (length2 oc) - (pow radius 2)

        discriminant := (pow hb 2) - (a * c)

        if (discriminant > 0)
            root := (sqrt discriminant)

            # first root
            t := (-hb - root) / a
            if ((t < tmax) and (t > tmin))
                let at = ('at ray t)
                out-normal := (at - self.center) / self.radius
                return (HitRecordOpt (HitRecord ray t out-normal (copy self.mat)))

            # second root
            t := (-hb + root) / a
            if ((t < tmax) and (t > tmin))
                let at = ('at ray t)
                out-normal := (at - self.center) / self.radius
                return (HitRecordOpt (HitRecord ray t out-normal (copy self.mat)))
            _ (HitRecordOpt none)
        else
            _ (HitRecordOpt none)

enum Hittable
    Sphere : SphereH

    let __typecall = enum-class-constructor

    inline... hit? (self ray tmin tmax)
        'apply self
            (T self) -> ('hit? self (va-tail *...))

HittableList := (Array Hittable)
typedef+ HittableList
    fn hit? (self ray tmin tmax)
        let closest record =
            fold (closest last-record = tmax (HitRecordOpt none)) for obj in self
                # we shrink max range every time we hit, to discard further objects
                let record =
                    'hit? obj ray tmin closest
                if record
                    let new-record = ('force-unwrap record)
                    _ (copy new-record.t) record
                else
                    _ closest last-record
        record

locals;
