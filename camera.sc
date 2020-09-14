using import struct
using import glm

using import .utils

struct Camera plain
    origin : vec3
    lower-left-corner : vec3
    horizontal  : vec3
    vertical    : vec3
    uvw         : mat3
    lens-radius : f32

    inline __typecall (cls lookfrom lookat vup vfov aspect-ratio aperture focus-distance)
        h := (tan (vfov / 2))
        viewport-height := 2.0 * h
        viewport-width  := aspect-ratio * viewport-height
        focal-length    := 1.0

        origin := lookfrom
        w := (normalize (lookfrom - lookat)) # view direction
        u := (normalize (cross vup w)) # proj right
        v := (cross w u)  # proj up
        let horizontal vertical =
            u * viewport-width * focus-distance
            v * viewport-height * focus-distance

        lower-left-corner := origin - (horizontal / 2) - (vertical / 2) - (w * focus-distance)
        lens-radius := aperture / 2

        super-type.__typecall cls
            origin = origin
            lower-left-corner = lower-left-corner
            horizontal = horizontal
            vertical = vertical
            uvw = (mat3 u v w)
            lens-radius = lens-radius

    fn ray (self uv)
        let u v w = (unpack self.uvw)
        rd     := self.lens-radius * (random-in-unit-disk)
        offset :=  (u * rd.x) + (v * rd.y)
        Ray (self.origin + offset)
            + self.lower-left-corner
                uv.x * self.horizontal
                uv.y * self.vertical
                -self.origin
                -offset

do
    let Camera
    locals;
