using import radlib.core-extensions

using import Option
using import Array
using import glm
using import struct
using import enum
using import Rc

import .raydEngine.use
import app
let gfx = (import gfx.webgpu.backend)
let wgpu = (import gfx.webgpu.wrapper)
import HID
import PRNG
import timer

FB_WIDTH        := 640:u32
FB_HEIGHT       := 480:u32
# change this to average the scene over time
TOTAL_FRAMES    := 1
RT_SAMPLE_COUNT := 100

aspect-ratio    := FB_WIDTH / FB_HEIGHT
viewport-height := 2.0
viewport-width  := aspect-ratio * viewport-height
focal-length    := 1.0
origin          := (vec3)

let viewport =
    vec3 viewport-width viewport-height focal-length

# -Z goes towards the screen; so this puts us at the lower left corner
# of the projection plane.
lower-left-corner := origin - (vec3 (viewport.xy / 2) viewport.z)
run-stage;

inline length2 (v)
    dot v v

inline reflect (v n)
    len := (dot v n)
    v - (2 * len * n)

global rng : PRNG.random.Xoshiro256+ 0
fn random-unit-vector ()
    a := ('normalized rng) * 2 * pi
    z := (('normalized rng) * 2) - 1
    r := (sqrt (1 - (* z z)))
    vec3 (r * (cos a)) (r * (sin a)) z

struct Ray plain
    origin    : vec3
    direction : vec3

    fn at (self t)
        self.origin + (self.direction * t)

struct LambertianM
    albedo : vec3

    fn scatter (self iray record)
        scatter-dir := record.normal + (random-unit-vector)
        scattered := (Ray record.p scatter-dir)
        attenuation := self.albedo
        _ true scattered attenuation

struct MetallicM
    albedo : vec3

    fn scatter (self iray record)
        reflected := (reflect (normalize iray.direction) record.normal)
        scattered := (Ray record.p reflected)
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

global scene : HittableList
global mat-ground : (Rc Material) (LambertianM (albedo = (vec3 0.8 0.8 0)))
global mat-center : (Rc Material) (LambertianM (albedo = (vec3 0.7 0.3 0.3)))
global mat-left   : (Rc Material) (MetallicM (albedo = (vec3 0.8 0.8 0.8)))
global mat-right  : (Rc Material) (MetallicM (albedo = (vec3 0.8 0.6 0.2)))

'emplace-append scene
    SphereH (center = (vec3 0 -100.5 -1)) (radius = 100)
        copy mat-ground
'emplace-append scene
    SphereH (center = (vec3 0 0 -1)) (radius = 0.5)
        copy mat-center
'emplace-append scene
    SphereH (center = (vec3 -1.0 0 -1)) (radius = 0.5)
        copy mat-left
'emplace-append scene
    SphereH (center = (vec3 1.0 0 -1)) (radius = 0.5)
        copy mat-right

fn ray-color (r depth)
    if (depth >= unroll-limit)
        return (vec3)

    let record = ('hit? scene r 0.001 Inf)
    if record
        let record = ('force-unwrap record)
        mat := record.mat
        let scattered? sray attenuation = ('scatter mat r record)
        if scattered?
            copy
                attenuation * (this-function sray (depth + 1))
        else
            (vec3)
    else
        n := (normalize r.direction)
        t := 0.5 * (n.y + 1)
        mix (vec3 1) (vec3 0.5 0.7 1) t

fn color (uv)
    # ray from origin (camera/eye) towards projection plane at remapped UV
    dir := (lower-left-corner + (vec3 (uv * viewport.xy) 0) - origin)
    vec4 (ray-color (Ray origin dir) 0) 1

struct Pixel plain
    r : u8
    g : u8
    b : u8
    a : u8

    inline __+ (lhs rhs)
        static-if (imply? lhs rhs)
            inline (self other)
                this-type
                    self.r + other.r
                    self.g + other.g
                    self.b + other.b
                    self.a + other.a

struct Texture
    id : wgpu.TextureId
    view : wgpu.TextureViewId
    width : u32
    height : u32

    fn update (self data)
        expected-bytes := self.width * self.height * 4
        assert ((Array-sizeof data) == expected-bytes)

        queue := ('force-unwrap gfx.backend) . queue
        wgpu.queue_write_texture queue
            &local wgpu.TextureCopyView
                texture = self.id
                mip_level = 0
                origin = wgpu.Origin3d_ZERO
            (imply data pointer) as (pointer u8)
            (Array-sizeof data)
            &local wgpu.TextureDataLayout
                offset = 0
                bytes_per_row = (self.width * 4)
                rows_per_image = self.height
            &local (wgpu.Extent3d self.width self.height 1)

struct RenderingState
    raytracing-buffer : (Array Pixel (FB_WIDTH * FB_HEIGHT))
    raytracing-target : Texture
    sampler           : wgpu.SamplerId
    pipeline          : wgpu.RenderPipelineId
    # tbinding-layout   : wgpu.BindGroupLayout
    texture-binding   : wgpu.BindGroupId

global state : (Option RenderingState)
global clock : timer.Timer

fn init ()
    HID.window.set-size FB_WIDTH FB_HEIGHT
    HID.window.set-title "my little raytracer"
    gfx.set-clear-color (vec4 1)

    inline make-sampler (filter-mode)
        let device = (('force-unwrap gfx.backend) . device)
        wgpu.device_create_sampler device
            &local wgpu.SamplerDescriptor
                address_mode_u = wgpu.AddressMode.ClampToEdge
                address_mode_v = wgpu.AddressMode.ClampToEdge
                address_mode_w = wgpu.AddressMode.ClampToEdge
                mag_filter = wgpu.FilterMode.Linear
                min_filter = wgpu.FilterMode.Linear
                mipmap_filter = wgpu.FilterMode.Linear

    fn make-texture (x y format)
        let device = (('force-unwrap gfx.backend) . device)
        let tex =
            wgpu.device_create_texture device
                &local wgpu.TextureDescriptor
                    size = (wgpu.Extent3d x y 1)
                    mip_level_count = 1
                    sample_count = 1
                    dimension = wgpu.TextureDimension.D2
                    format = format
                    usage = (wgpu.TextureUsage_COPY_DST | wgpu.TextureUsage_SAMPLED)
        let tview =
            wgpu.texture_create_view tex
                &local wgpu.TextureViewDescriptor
                    format = format
                    dimension = wgpu.TextureViewDimension.D2
                    aspect = wgpu.TextureAspect.All
                    base_mip_level = 0
                    level_count = 1
                    base_array_layer = 0
                    array_layer_count = 1

        Struct.__typecall Texture
            id = tex
            view = tview
            width = x
            height = y

    inline make-shader (fun stage)
        let device = (('force-unwrap gfx.backend) . device)
        let code = (static-compile-spirv 0x10000 stage (static-typify fun))
        let clen = ((countof code) // 4)

        wgpu.device_create_shader_module device
            &local wgpu.ShaderModuleDescriptor
                code =
                    typeinit
                        bytes = (code as rawstring as (pointer u32))
                        length = clen

    fn make-texture-binding-layout ()
        let device = (('force-unwrap gfx.backend) . device)
        wgpu.device_create_bind_group_layout device
            &local wgpu.BindGroupLayoutDescriptor
                label = "diffuse texture"
                entries =
                    &local
                        arrayof wgpu.BindGroupLayoutEntry
                            typeinit
                                binding = 0
                                visibility = wgpu.ShaderStage_FRAGMENT
                                ty = wgpu.BindingType.SampledTexture
                                view_dimension = wgpu.TextureViewDimension.D2
                                texture_component_type =
                                    wgpu.TextureComponentType.Uint
                            typeinit
                                binding = 1
                                visibility = wgpu.ShaderStage_FRAGMENT
                                ty = wgpu.BindingType.Sampler
                entries_length = 2

    fn make-pipeline (bgroup-layout)
        let device = (('force-unwrap gfx.backend) . device)

        using import glm
        using import glsl
        fn vertex ()
            local vertices =
                arrayof vec2
                    vec2 -1  1 # top left
                    vec2 -1 -1 # bottom left
                    vec2  1 -1 # bottom right
                    vec2  1 -1 # bottom right
                    vec2  1  1 # top right
                    vec2 -1  1 # top left

            local texcoords =
                arrayof vec2
                    vec2 0 1 # top left
                    vec2 0 0 # bottom left
                    vec2 1 0 # bottom right
                    vec2 1 0 # bottom right
                    vec2 1 1 # top right
                    vec2 0 1 # top left

            out vtexcoord : vec2
                location = 0

            gl_Position = (vec4 (vertices @ gl_VertexIndex) 0 1)
            vtexcoord = texcoords @ gl_VertexIndex
        fn fragment ()
            uniform diffuse-t : texture2D
                set = 0
                binding = 0
            uniform diffuse-s : sampler
                set = 0
                binding = 1

            in vtexcoord : vec2
                location = 0
            out fcolor : vec4
                location = 0

            fcolor = (texture (sampler2D diffuse-t diffuse-s) vtexcoord)

        let vshader fshader = (make-shader vertex 'vertex) (make-shader fragment 'fragment)
        let texture-bgroup-layout = (make-texture-binding-layout)
        let pip-layout =
            wgpu.device_create_pipeline_layout device
                &local wgpu.PipelineLayoutDescriptor
                    bind_group_layouts = (&local (imply bgroup-layout u64))
                    bind_group_layouts_length = 1

        wgpu.device_create_render_pipeline device
            &local wgpu.RenderPipelineDescriptor
                layout = pip-layout
                vertex_stage =
                    wgpu.ProgrammableStageDescriptor
                        module = vshader
                        entry_point = "main"
                fragment_stage =
                    &local wgpu.ProgrammableStageDescriptor
                        module = fshader
                        entry_point = "main"
                primitive_topology = wgpu.PrimitiveTopology.TriangleList
                color_states =
                    &local wgpu.ColorStateDescriptor
                        format = wgpu.TextureFormat.Bgra8UnormSrgb
                        alpha_blend =
                            wgpu.BlendDescriptor
                                src_factor = wgpu.BlendFactor.SrcAlpha
                                dst_factor = wgpu.BlendFactor.OneMinusSrcAlpha
                                operation = wgpu.BlendOperation.Add
                        color_blend =
                            wgpu.BlendDescriptor
                                src_factor = wgpu.BlendFactor.SrcAlpha
                                dst_factor = wgpu.BlendFactor.OneMinusSrcAlpha
                                operation = wgpu.BlendOperation.Add
                        write_mask = wgpu.ColorWrite_ALL
                color_states_length = 1
                sample_count = 1
                sample_mask = 0xffffffff

    fn make-texture-bindgroup (layout tview sampler)
        let device = (('force-unwrap gfx.backend) . device)
        wgpu.device_create_bind_group device
            &local wgpu.BindGroupDescriptor
                layout = layout
                entries =
                    &local
                        arrayof wgpu.BindGroupEntry
                            typeinit
                                binding = 0
                                texture_view = tview
                            typeinit
                                binding = 1
                                sampler = sampler
                entries_length = 2

    let tex = (make-texture FB_WIDTH FB_HEIGHT wgpu.TextureFormat.Rgba8UnormSrgb)
    let sampler = (make-sampler wgpu.FilterMode.Linear)
    let bgroup-layout = (make-texture-binding-layout)
    state =
        RenderingState
            raytracing-target = tex
            sampler = sampler
            pipeline = (make-pipeline bgroup-layout)
            texture-binding = (make-texture-bindgroup bgroup-layout tex.view sampler)
    buf := ('force-unwrap state) . raytracing-buffer
    tex := ('force-unwrap state) . raytracing-target
    'resize buf ('capacity buf)
    clock = (timer.Timer) # start counting from now
    ;

global color-buffer : (Array vec4 (FB_WIDTH * FB_HEIGHT))
'resize color-buffer ('capacity color-buffer)

fn update-scene (t)
    let y = (mix 0.0 0.2 (t ** (1 - t)))
    # 'apply (scene @ 0)
    #     (T s) -> (s.center = (vec3 0 y -1))
    ;

fn update (dt)
    global frame-counter : i32 0
    global y : u32
    if (frame-counter >= TOTAL_FRAMES)
        return;

    update-scene (frame-counter / TOTAL_FRAMES)

    # generate the image and average with previous frames
    scale := 1.0 / RT_SAMPLE_COUNT

    using import itertools
    using import glm

    tex := ('force-unwrap state) . raytracing-target

    for x in (range FB_WIDTH)
        vvv bind color-result
        fold (color-result = (vec4)) for i in (range RT_SAMPLE_COUNT)
            let uv =
                /
                    (vec2 x y) + (vec2 ('normalized rng) ('normalized rng))
                    (vec2 (FB_WIDTH - 1) (FB_HEIGHT - 1))
            color-result + (color uv)

        color-result := color-result * scale
        idx := y * FB_WIDTH + x
        pixel := color-buffer @ idx
        frame-counter as:= f32
        pixel = ((frame-counter * (color-buffer @ idx) + color-result) / (frame-counter + 1))

    # copy to texture
    buf := ('force-unwrap state) . raytracing-buffer
    for i in (range (countof color-buffer))
        buf @ i =
            typeinit
                va-map
                    (x) -> ((clamp (x * 255) 0. 255.) as u8)
                    unpack (sqrt (color-buffer @ i))

    if (y >= (FB_HEIGHT - 1))
        print "sample" (deref frame-counter) "done"
        frame-counter += 1
        if (frame-counter == TOTAL_FRAMES)
            print "render done in" ('run-time-real clock) "seconds."
        y = 0
    else
        y += 1

    'update tex buf
    ;

fn draw (cmd-encoder render-pass)
    let state = ('force-unwrap state)
    wgpu.render_pass_set_pipeline render-pass state.pipeline
    wgpu.render_pass_set_bind_group render-pass 0 state.texture-binding null 0
    wgpu.render_pass_draw render-pass 6 1 0 0
    ;

app.run init update draw
