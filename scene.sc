using import Array
using import Map
using import struct

using import .hittables
using import .materials

struct Scene
    objects   : HittableList
    materials : (Array Material)

    inline add-material (self material)
        'emplace-append self.materials material
        (countof self.materials) - 1
    inline material (self index)
        self.materials @ index

do
    let Scene
    locals;
