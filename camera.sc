using import struct
using import glm

using import .utils

struct Camera plain
    origin : vec3
    lower-left-corner : vec3
    viewport : vec3

    inline __typecall (cls vfov aspect-ratio)
        h := (tan (vfov / 2))
        viewport-height := 2.0 * h
        viewport-width  := aspect-ratio * viewport-height
        focal-length    := 1.0

        origin := (vec3)
        let viewport =
            vec3 viewport-width viewport-height focal-length

        # -Z goes towards the screen; so this puts us at the lower left corner
        # of the projection plane.
        lower-left-corner := origin - (vec3 (viewport.xy / 2) viewport.z)

        super-type.__typecall cls
            origin = origin
            lower-left-corner = lower-left-corner
            viewport = viewport

    fn ray (self uv)
        Ray self.origin
            self.lower-left-corner + (vec3 (uv * self.viewport.xy) 0) - self.origin

do
    let Camera
    locals;
