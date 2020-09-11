using import radlib.core-extensions
using import enum
using import struct
using import glm

using import .utils

struct LambertianM
    albedo : vec3

    fn scatter (self iray record)
        scatter-dir := record.normal + (random-unit-vector)
        scattered := (Ray record.p scatter-dir)
        attenuation := self.albedo
        _ true scattered attenuation

struct MetallicM
    albedo : vec3
    roughness : f32

    fn scatter (self iray record)
        reflected := (reflect (normalize iray.direction) record.normal)
        scattered := (Ray record.p (reflected + ((random-in-unit-sphere) * self.roughness)))
        attenuation := self.albedo
        _
            (dot scattered.direction record.normal) > 0
            scattered
            attenuation

enum Material
    Lambertian : LambertianM
    Metallic : MetallicM

    let __typecall = enum-class-constructor

    inline... scatter (self iray record)
        'apply self
            (T self) -> ('scatter self (va-tail *...))

locals;
