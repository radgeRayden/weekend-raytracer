using import struct
using import glm

using import .utils

struct Camera plain
    origin : vec3
    lower-left-corner : vec3
    horizontal : vec3
    vertical   : vec3

    inline __typecall (cls lookfrom lookat vup vfov aspect-ratio)
        h := (tan (vfov / 2))
        viewport-height := 2.0 * h
        viewport-width  := aspect-ratio * viewport-height
        focal-length    := 1.0

        origin := lookfrom
        w := (normalize (lookfrom - lookat)) # view direction
        u := (normalize (cross vup w)) # proj right
        v := (cross w u)  # proj up
        let horizontal vertical =
            u * viewport-width
            v * viewport-height

        lower-left-corner := origin - (horizontal / 2) - (vertical / 2) - (w * focal-length)

        super-type.__typecall cls
            origin = origin
            lower-left-corner = lower-left-corner
            horizontal = horizontal
            vertical = vertical

    fn ray (self uv)
        Ray self.origin
            self.lower-left-corner + (uv.x * self.horizontal) + (uv.y * self.vertical) - self.origin

do
    let Camera
    locals;
