using import radlib.core-extensions

using import Option
using import Array
using import glm
using import struct
using import enum

import .raydEngine.use
import app
let gfx = (import gfx.webgpu.backend)
let wgpu = (import gfx.webgpu.wrapper)
import HID
import PRNG
import timer

using import .utils
using import .materials
using import .hittables
using import .camera
using import .scene
import .threads

THREAD_COUNT := 4

RT_SAMPLE_COUNT := 500
MAX_BOUNCES     := 50

FB_WIDTH     := 1200:u32
FB_HEIGHT    := 675:u32
vFOV         := 20 * (pi / 180)
aspect-ratio := FB_WIDTH / FB_HEIGHT

lookfrom   := (vec3 13 2 3)
lookat     := (vec3 0 0 0)
vup        := (vec3 0 1 0)
focus-dist := 10.0
aperture   := 0.1
cam        := (Camera lookfrom lookat vup vFOV aspect-ratio aperture focus-dist)
run-stage;

# random scene
global rng : PRNG.random.Xoshiro256** 0
global world : Scene
global ground-material =
    'add-material world
        LambertianM (albedo = (vec3 0.5))
'emplace-append world.objects
    SphereH (vec3 0 -1000 0) 1000 ground-material

for a in (range -11 11)
    for b in (range -11 11)
        a as:= f32
        b as:= f32
        choose-mat := ('normalized rng)
        let center =
            vec3
                a + 0.9 * ('normalized rng)
                0.2
                b + 0.9 * ('normalized rng)

        fn... random-color (lb = 0.0, ub = 1.0)
            vec3
                ('normalized rng) * (ub - lb) + lb
                ('normalized rng) * (ub - lb) + lb
                ('normalized rng) * (ub - lb) + lb

        if ((length (center - (vec3 4 0.2 0))) > 0.9)
            let sphere-material =
                if (choose-mat < 0.8)
                    albedo := (random-color) * (random-color)
                    'add-material world (LambertianM albedo)
                elseif (choose-mat < 0.95)
                    albedo := (random-color 0.5 1.0)
                    roughness := (('normalized rng) * 0.5) as f32
                    'add-material world (MetallicM albedo roughness)
                else
                    'add-material world (DielectricM 1.5)

            'emplace-append world.objects
                SphereH center 0.2 sphere-material

global material1 = ('add-material world (DielectricM 1.5))
'emplace-append world.objects
    SphereH (vec3 0 1 0) 1.0 material1
global material2 = ('add-material world (LambertianM (vec3 0.4 0.2 0.1)))
'emplace-append world.objects
    SphereH (vec3 -4 1 0) 1.0  material2
global material3 = ('add-material world (MetallicM (vec3 0.7 0.6 0.5) 0.0))
'emplace-append world.objects
    SphereH (vec3 4 1 0) 1.0 material3

fn color (uv rng)
    vvv bind ray-color
    loop (r color bounces = ('ray cam uv rng) (vec3 1) 0)
        if (bounces >= MAX_BOUNCES)
            break (vec3)

        let hit? record = ('hit? world.objects r 0.001 Inf)
        if hit?
            mat := ('material world record.mat)
            let scattered? sray attenuation = ('scatter mat r record rng)
            if scattered?
                _ sray (attenuation * color) (bounces + 1)
            else
                # didn't arrive at where we're looking at;
                # remember we're tracing the path in reverse.
                break (vec3)
        else
            n := (normalize r.direction)
            t := 0.5 * (n.y + 1)
            break
                color * (mix (vec3 1) (vec3 0.5 0.7 1) t)
    vec4 ray-color 1

global profile-heatmap : (Array f32 (FB_WIDTH * FB_HEIGHT))
'resize profile-heatmap ('capacity profile-heatmap)
global color-buffer : (Array vec4 (FB_WIDTH * FB_HEIGHT))
'resize color-buffer ('capacity color-buffer)

fn render-row (row rng)
    scale := 1.0 / RT_SAMPLE_COUNT
    for x in (range FB_WIDTH)
        vvv bind color-result
        fold (color-result = (vec4)) for i in (range RT_SAMPLE_COUNT)
            let uv =
                /
                    (vec2 x row) + (vec2 ('normalized rng) ('normalized rng))
                    (vec2 (FB_WIDTH - 1) (FB_HEIGHT - 1))
            color-result + (color uv rng)

        color-result := color-result * scale
        idx := row * FB_WIDTH + x
        color-buffer @ idx = color-result

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

    let render-tex = (make-texture FB_WIDTH FB_HEIGHT wgpu.TextureFormat.Rgba8UnormSrgb)
    let sampler = (make-sampler wgpu.FilterMode.Linear)
    let bgroup-layout = (make-texture-binding-layout)
    state =
        RenderingState
            raytracing-target = render-tex
            sampler = sampler
            pipeline = (make-pipeline bgroup-layout)
            texture-binding = (make-texture-bindgroup
                               bgroup-layout render-tex.view sampler)

    renderbuf := ('force-unwrap state) . raytracing-buffer
    'resize renderbuf ('capacity renderbuf)

    clock = (timer.Timer) # start counting from now

    # dispatch work
    va-map
        inline (i)
            threads.spawn
                fn (arg)
                    # in lieu of a proper jump function
                    local rng : PRNG.random.Xoshiro256+ (i * 1000)
                    for y in (range FB_HEIGHT)
                        if ((y % THREAD_COUNT) == i)
                            render-row y rng
                    print "thread" i "done in" ('run-time-real clock) "seconds"
                    null as voidstar
                null
        va-range THREAD_COUNT
    ;

fn update (dt)
    # copy to texture
    local copy-color-buffer : (Array vec4 (FB_WIDTH * FB_HEIGHT))
    'reserve copy-color-buffer ('capacity color-buffer)
    for el in color-buffer
        'append copy-color-buffer el

    tex := ('force-unwrap state) . raytracing-target
    buf := ('force-unwrap state) . raytracing-buffer
    for i in (range (countof color-buffer))
        buf @ i =
            typeinit
                va-map
                    (x) -> ((clamp (x * 255) 0. 255.) as u8)
                    unpack (sqrt (copy-color-buffer @ i))

    'update tex buf

fn draw (cmd-encoder render-pass)
    let state = ('force-unwrap state)
    wgpu.render_pass_set_pipeline render-pass state.pipeline
    wgpu.render_pass_set_bind_group render-pass 0 state.texture-binding null 0
    wgpu.render_pass_draw render-pass 6 1 0 0
    ;

app.run init update draw
