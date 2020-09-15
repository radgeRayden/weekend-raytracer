using import radlib.core-extensions
using import enum
using import struct
using import glm

using import .utils

struct LambertianM
    albedo : vec3

    fn scatter (self iray record rng)
        scatter-dir := record.normal + (random-unit-vector rng)
        scattered := (Ray record.p scatter-dir)
        attenuation := self.albedo
        _ true scattered (deref attenuation)

struct MetallicM
    albedo : vec3
    roughness : f32

    fn scatter (self iray record rng)
        reflected := (reflect (normalize iray.direction) record.normal)
        scattered := (Ray record.p (reflected + ((random-in-unit-sphere rng) * self.roughness)))
        attenuation := self.albedo
        _
            (dot scattered.direction record.normal) > 0
            scattered
            deref attenuation

struct DielectricM
    refraction-index : f32

    fn scatter (self iray record rng)
        attenuation := (vec3 1) # never absorb
        let coef =
            # air -> dielectric or dielectric -> air
            ? record.front? (1 / self.refraction-index) self.refraction-index

        ndir := (normalize iray.direction)
        let cos-theta =
            min
                dot -ndir record.normal
                1.0
        let sin-theta = (sqrt (1.0 - cos-theta * cos-theta))
        if ((coef * sin-theta) > 1.0)
            reflected := (reflect ndir record.normal)
            _ true (Ray record.p reflected) attenuation
        # reflect on a probability based on Schlick's approximation
        elseif (('normalized rng) < (schlick cos-theta coef))
            reflected := (reflect ndir record.normal)
            _ true (Ray record.p reflected) attenuation
        else
            refracted := (refract ndir record.normal coef)
            _ true (Ray record.p refracted) attenuation

enum Material
    Lambertian : LambertianM
    Metallic : MetallicM
    Dielectric : DielectricM

    let __typecall = enum-class-constructor

    inline... scatter (self iray record rng)
        'apply self
            (T self) -> ('scatter self (va-tail *...))

do
    let LambertianM MetallicM DielectricM Material
    locals;
