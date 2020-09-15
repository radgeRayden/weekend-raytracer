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
    mat    : usize

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
                return true
                    HitRecord ray t out-normal self.mat

            # second root
            t := (-hb + root) / a
            if ((t < tmax) and (t > tmin))
                let at = ('at ray t)
                out-normal := (at - self.center) / self.radius
                return true
                    HitRecord ray t out-normal self.mat
            _ false (undef HitRecord)
        else
            _ false (undef HitRecord)

enum Hittable
    Sphere : SphereH

    let __typecall = enum-class-constructor

    inline... hit? (self ray tmin tmax)
        'apply self
            (T self) -> ('hit? self (va-tail *...))

HittableList := (Array Hittable)
typedef+ HittableList
    fn hit? (self ray tmin tmax)
        let hit? closest record =
            fold (hit-any? closest last-record = false tmax (undef HitRecord)) for obj in self
                # we shrink max range every time we hit, to discard further objects
                let hit? record =
                    'hit? obj ray tmin closest
                if hit?
                    _ true record.t record
                else
                    _ hit-any? closest last-record
        _ hit? record

do
    let SphereH Hittable HittableList
    locals;
